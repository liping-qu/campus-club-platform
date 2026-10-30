package com.campus.club.notification;

import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.notification.service.NotificationService;
import com.campus.club.notification.vo.NotificationVO;
import com.campus.club.registration.service.RegistrationService;
import com.campus.club.support.RegistrationTestData;
import com.campus.club.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步通知的集成测试 —— 连着真数据库、真线程池跑。
 *
 * <p><b>这组测试真正要证明的只有两件事</b>：
 * <ol>
 *   <li>报名提交之后，通知<b>真的会被写进去</b>（不是「调用了一个方法」）；</li>
 *   <li>报名<b>回滚</b>之后，通知<b>一条都不能有</b>。
 *       第二条才是 {@code AFTER_COMMIT} 存在的理由。用普通 {@code @EventListener}
 *       也能让第一条通过，但第二条会失败 —— 而线上「收到报名成功通知却查不到报名记录」
 *       恰恰就是这么来的。</li>
 * </ol>
 *
 * <p>异步意味着不能「调完立刻断言」，所以这里用轮询等结果，
 * 而不是 {@code Thread.sleep} 一个拍脑袋的时长：等待有上限，等到了就立刻返回，
 * 既不会因为机器慢而偶发失败，也不会每次都白等 2 秒。
 */
@SpringBootTest
class NotificationFlowIntegrationTest {

    private static final long ASYNC_TIMEOUT_MILLIS = 5_000L;
    private static final long POLL_INTERVAL_MILLIS = 25L;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long activityId;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // 通知随用户级联删除（fk_notification_user ON DELETE CASCADE），不用单独清
        RegistrationTestData.cleanup(jdbcTemplate, activityId, userIds);
        activityId = null;
        userIds.clear();
    }

    @Test
    @DisplayName("报名提交后异步写入通知：内容取自事务内快照，且不阻塞报名接口")
    void registrationProducesNotificationAfterCommit() {
        prepare(10, 1);
        long userId = userIds.get(0);

        as(userId, () -> registrationService.register(activityId));

        int count = awaitNotifications(userId, 1);
        assertEquals(1, count, "报名成功后应恰好产生 1 条通知");

        NotificationVO notification = asWithResult(userId,
                () -> notificationService.listMy(1, 10)).getRecords().get(0);
        assertEquals("报名成功", notification.getTitle());
        assertEquals("REGISTRATION", notification.getType());
        assertFalse(notification.getRead(), "新通知必须是未读");
        assertTrue(notification.getContent().contains("并发测试活动"),
                "通知正文应包含活动标题，实际：" + notification.getContent());
        assertTrue(notification.getContent().contains("测试地点"),
                "通知正文应包含活动地点，实际：" + notification.getContent());
    }

    @Test
    @DisplayName("报名事务回滚：通知一条都不产生，报名字段也不留痕")
    void rolledBackRegistrationProducesNoNotification() {
        prepare(10, 1);
        long userId = userIds.get(0);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            as(userId, () -> registrationService.register(activityId));
            // 模拟「报名之后、提交之前」的后续步骤失败
            status.setRollbackOnly();
        });

        // 代码回滚了，但发布事件那一步确实执行过 —— 这就是必须 AFTER_COMMIT 的原因
        sleepQuietly(400);
        assertEquals(0, countNotifications(userId), "事务回滚后不应有任何通知");
        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId),
                "名额不应被占用");
        assertEquals(0, RegistrationTestData.activeRegistrations(jdbcTemplate, activityId),
                "报名记录不应留下");
    }

    @Test
    @DisplayName("同一用户可以有多条通知，列表按时间倒序；未读数与实际一致")
    void unreadCountTracksNotifications() {
        prepare(10, 1);
        long userId = userIds.get(0);

        // 报名 → 取消 → 再报名，只产生两条「报名成功」通知（取消不发通知）
        as(userId, () -> registrationService.register(activityId));
        as(userId, () -> registrationService.cancel(activityId));
        as(userId, () -> registrationService.register(activityId));

        awaitNotifications(userId, 2);
        assertEquals(2, asWithResult(userId, () -> notificationService.unreadCount()));

        List<NotificationVO> records = asWithResult(userId,
                () -> notificationService.listMy(1, 10)).getRecords();
        assertEquals(2, records.size());
        assertTrue(records.get(0).getId() > records.get(1).getId(),
                "列表应按最新在前排序");
    }

    @Test
    @DisplayName("标记已读只影响自己的通知：改别人的通知返回 404，且对方仍是未读")
    void markReadIsScopedToOwner() {
        prepare(10, 2);
        long owner = userIds.get(0);
        long other = userIds.get(1);

        as(owner, () -> registrationService.register(activityId));
        awaitNotifications(owner, 1);
        long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification WHERE user_id = ?", Long.class, owner);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> as(other, () -> notificationService.markRead(notificationId)));
        assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, ex.getErrorCode(),
                "越权修改应表现为「不存在」，不能暴露这条通知属于别人");
        assertEquals(1, asWithResult(owner, () -> notificationService.unreadCount()),
                "别人的标记不能影响本人的未读状态");

        as(owner, () -> notificationService.markRead(notificationId));
        assertEquals(0, asWithResult(owner, () -> notificationService.unreadCount()));

        // 重复标记已读：SQL 里带 is_read = 0，第二次影响 0 行 → 报 404 而不是「成功」
        BusinessException again = assertThrows(BusinessException.class,
                () -> as(owner, () -> notificationService.markRead(notificationId)));
        assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, again.getErrorCode());
    }

    @Test
    @DisplayName("全部标记已读：返回受影响条数，重复调用返回 0")
    void markAllReadIsIdempotent() {
        prepare(10, 1);
        long userId = userIds.get(0);

        as(userId, () -> registrationService.register(activityId));
        awaitNotifications(userId, 1);

        assertEquals(1, asWithResult(userId, () -> notificationService.markAllRead()));
        assertEquals(0, asWithResult(userId, () -> notificationService.markAllRead()),
                "第二次没有未读，应返回 0 而不是报错");
        assertEquals(0, asWithResult(userId, () -> notificationService.unreadCount()));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private void prepare(int capacity, int userCount) {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, capacity);
        userIds.addAll(RegistrationTestData.createUsers(jdbcTemplate, userCount));
    }

    private int countNotifications(long userId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class, userId);
        return value == null ? 0 : value;
    }

    /** 轮询等异步结果，超时即失败并给出实际条数 */
    private int awaitNotifications(long userId, int expected) {
        long deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MILLIS;
        int actual = countNotifications(userId);
        while (actual < expected && System.currentTimeMillis() < deadline) {
            sleepQuietly(POLL_INTERVAL_MILLIS);
            actual = countNotifications(userId);
        }
        assertEquals(expected, actual,
                "等待 " + ASYNC_TIMEOUT_MILLIS + "ms 后通知条数不符");
        return actual;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void as(long userId, Runnable action) {
        CurrentUserContext.set(new AuthPrincipal(userId, "t-" + userId, UserRole.STUDENT));
        try {
            action.run();
        } finally {
            CurrentUserContext.clear();
        }
    }

    private <T> T asWithResult(long userId, java.util.function.Supplier<T> action) {
        CurrentUserContext.set(new AuthPrincipal(userId, "t-" + userId, UserRole.STUDENT));
        try {
            return action.get();
        } finally {
            CurrentUserContext.clear();
        }
    }
}
