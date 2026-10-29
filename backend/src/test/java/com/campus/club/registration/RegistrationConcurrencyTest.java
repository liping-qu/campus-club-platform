package com.campus.club.registration;

import com.campus.club.auth.CurrentUserContext;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.registration.service.RegistrationService;
import com.campus.club.registration.vo.RegistrationResultVO;
import com.campus.club.support.RegistrationTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报名的真实并发测试。
 *
 * <p><b>为什么必须有这一组测试</b>：
 * 「不会超卖」是设计意图，不是既成事实。串行地调两次报名接口，无论实现多糟糕都能通过；
 * 只有让几十个线程在同一毫秒抢占同一条记录，才能把「先查再改」的漏洞逼出来。
 * 这组用例连的是 docker 里的真实 MySQL，走的也是 Spring 事务代理，
 * 断言的是数据库的真实行为 —— 没有 Mock，没有伪造的并发数字。
 *
 * <p><b>断言口径</b>：
 * <ul>
 *   <li>成功人数 == 名额上限（不多不少，多了是超卖，少了是漏放）</li>
 *   <li>{@code activity.joined_count} == 有效报名行数（冗余计数不能漂移）</li>
 *   <li>失败原因必须是 {@code ACTIVITY_FULL}，出现其它异常说明有实现缺陷</li>
 * </ul>
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 并发抢锁时线程会占着连接等锁，池子太小会让测试退化成排队，掩盖真实的竞争
        "spring.datasource.hikari.maximum-pool-size=24"
})
class RegistrationConcurrencyTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long activityId;
    private List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        RegistrationTestData.cleanup(jdbcTemplate, activityId, userIds);
        activityId = null;
        userIds = new ArrayList<>();
    }

    @Test
    @DisplayName("40 人抢 5 个名额：恰好 5 人成功，计数与报名记录严格相等")
    void concurrentRegistrationNeverOversells() throws Exception {
        int capacity = 5;
        int threads = 40;
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, capacity);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, threads);

        List<String> results = runConcurrently(threads, index -> registerAs(userIds.get(index), activityId));

        long success = countOf(results, "OK");
        assertEquals(capacity, success,
                "成功报名人数必须恰好等于名额上限，实际 " + success + "，结果分布=" + distribution(results));
        assertEquals(0, countOf(results, ErrorCode.SYSTEM_ERROR.name()) + errorCount(results),
                "不应出现系统异常，结果分布=" + distribution(results));
        assertEquals(threads - capacity, countOf(results, ErrorCode.ACTIVITY_FULL.name()),
                "没抢到的请求都应该是「名额已满」，结果分布=" + distribution(results));
        assertEquals(capacity, RegistrationTestData.joinedCount(jdbcTemplate, activityId),
                "joined_count 必须等于名额上限");
        assertEquals(capacity, RegistrationTestData.activeRegistrations(jdbcTemplate, activityId),
                "有效报名行数必须等于名额上限");
    }

    @Test
    @DisplayName("同一用户 20 个并发请求：只生成 1 条报名记录，只占 1 个名额")
    void sameUserConcurrentRegistrationCreatesSingleRecord() throws Exception {
        int threads = 20;
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 10);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);
        long userId = userIds.get(0);

        List<String> results = runConcurrently(threads, index -> registerAs(userId, activityId));

        assertEquals(1, countOf(results, "OK"), "同一用户只能成功报名一次，结果分布=" + distribution(results));
        assertEquals(0, errorCount(results), "不应出现系统异常，结果分布=" + distribution(results));
        assertEquals(threads - 1, countOf(results, ErrorCode.ALREADY_REGISTERED.name()),
                "其余请求都应该是「已报名」，结果分布=" + distribution(results));
        assertEquals(1, RegistrationTestData.joinedCount(jdbcTemplate, activityId), "名额只能被占用 1 个");
        assertEquals(1, RegistrationTestData.activeRegistrations(jdbcTemplate, activityId), "只能有 1 条有效记录");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_registration WHERE activity_id = ? AND user_id = ?",
                Integer.class, activityId, userId);
        assertEquals(1, rows, "唯一约束保证同一用户在同一活动上只有一行，含已取消");
    }

    @Test
    @DisplayName("并发取消同一条报名：只释放 1 个名额，计数不会减成负数或重复释放")
    void concurrentCancelReleasesExactlyOnce() throws Exception {
        int threads = 20;
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 10);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);
        long userId = userIds.get(0);

        // 先正常报名一次，把计数打到 1
        assertTrue(registerAs(userId, activityId).equals("OK"));
        assertEquals(1, RegistrationTestData.joinedCount(jdbcTemplate, activityId));

        List<String> results = runConcurrently(threads, index -> cancelAs(userId, activityId));

        assertEquals(1, countOf(results, "OK"), "只有 1 个请求能取消成功，结果分布=" + distribution(results));
        assertEquals(0, errorCount(results), "不应出现系统异常，结果分布=" + distribution(results));
        assertEquals(threads - 1, countOf(results, ErrorCode.NOT_REGISTERED.name()),
                "其余请求都应该是「未报名」，结果分布=" + distribution(results));
        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId),
                "取消后计数必须回到 0，不能变成负数");
        assertEquals(0, RegistrationTestData.activeRegistrations(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("取消后重新报名：复用同一行记录，不新增行，计数正确回到 1")
    void reRegisterReusesCancelledRow() {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);
        long userId = userIds.get(0);

        RegistrationResultVO first = registerWithResult(userId, activityId);
        assertEquals("OK", cancelAs(userId, activityId));
        RegistrationResultVO second = registerWithResult(userId, activityId);

        assertEquals(first.getRegistrationId(), second.getRegistrationId(),
                "重新报名应复用原记录，而不是插入新行");
        assertEquals(1, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_registration WHERE activity_id = ?", Integer.class, activityId);
        assertEquals(1, totalRows, "唯一约束下始终只有 1 行");
    }

    @Test
    @DisplayName("名额已满的活动：报名返回 ACTIVITY_FULL 并保持计数不变")
    void fullActivityRejectsRegistration() {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 1);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 2);

        assertEquals("OK", registerAs(userIds.get(0), activityId));
        assertEquals(ErrorCode.ACTIVITY_FULL.name(), registerAs(userIds.get(1), activityId));
        assertEquals(1, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("报名截止后再报名：返回 SIGNUP_CLOSED，计数不变")
    void closedSignupRejectsRegistration() {
        activityId = RegistrationTestData.createActivity(jdbcTemplate, 10,
                LocalDateTime.now().minusHours(1), 1);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);

        assertEquals(ErrorCode.SIGNUP_CLOSED.name(), registerAs(userIds.get(0), activityId));
        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("未报名时取消：返回 NOT_REGISTERED，不触碰计数")
    void cancelWithoutRegistrationFails() {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 10);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);

        assertEquals(ErrorCode.NOT_REGISTERED.name(), cancelAs(userIds.get(0), activityId));
        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("活动不存在：返回 ACTIVITY_NOT_FOUND")
    void registerMissingActivityFails() {
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);
        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND.name(), registerAs(userIds.get(0), 999_999L));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /**
     * 所有线程在同一时刻放行，把竞争压缩到尽可能小的时间窗口里。
     * <p>
     * 没有 ready 闩，后面的线程可能在第一个线程已经提交后才开始跑，
     * 竞争就退化成串行，测试会「轻松通过」但什么都没验证到。
     */
    private List<String> runConcurrently(int threads, IntFunction<String> task) throws InterruptedException {
        // 线程数必须等于任务数：FixedThreadPool 的任务先入队再由线程取，
        // 池子比任务少时后面的任务永远不会启动，ready 闩就永远减不到 0。
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(task.apply(index));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    results.add("INTERRUPTED");
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未能全部就绪");
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "并发任务未在 120 秒内完成");
        return results;
    }

    private String registerAs(long userId, long activityId) {
        CurrentUserContext.set(userId);
        try {
            registrationService.register(activityId);
            return "OK";
        } catch (BusinessException ex) {
            return ex.getErrorCode().name();
        } catch (Exception ex) {
            return "ERROR:" + ex.getClass().getSimpleName() + ":" + ex.getMessage();
        } finally {
            CurrentUserContext.clear();
        }
    }

    private RegistrationResultVO registerWithResult(long userId, long activityId) {
        CurrentUserContext.set(userId);
        try {
            return registrationService.register(activityId);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private String cancelAs(long userId, long activityId) {
        CurrentUserContext.set(userId);
        try {
            registrationService.cancel(activityId);
            return "OK";
        } catch (BusinessException ex) {
            return ex.getErrorCode().name();
        } catch (Exception ex) {
            return "ERROR:" + ex.getClass().getSimpleName() + ":" + ex.getMessage();
        } finally {
            CurrentUserContext.clear();
        }
    }

    private long countOf(List<String> results, String value) {
        return results.stream().filter(value::equals).count();
    }

    private long errorCount(List<String> results) {
        return results.stream().filter(item -> item.startsWith("ERROR:") || "INTERRUPTED".equals(item)).count();
    }

    private String distribution(List<String> results) {
        return results.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.split(":")[0],
                        java.util.stream.Collectors.counting()))
                .toString();
    }
}
