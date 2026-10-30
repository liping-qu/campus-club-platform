package com.campus.club.registration.service;

import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.registration.entity.ActivityRegistration;
import com.campus.club.registration.entity.RegistrationStatus;
import com.campus.club.registration.mapper.ActivityRegistrationMapper;
import com.campus.club.registration.service.impl.RegistrationServiceImpl;
import com.campus.club.registration.vo.RegistrationResultVO;
import com.campus.club.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报名分支的单元测试。
 * <p>
 * 这里只验证「判定与编排」：抢不到名额时到底抛哪个业务码、复活失败时会不会回滚、
 * 唯一键冲突会不会被吞掉。数据库的真实并发行为由集成测试覆盖，不在这一层假装能测出来。
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    private static final long ACTIVITY_ID = 1L;
    private static final long USER_ID = 6L;

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivityRegistrationMapper registrationMapper;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set(new AuthPrincipal(USER_ID, "student1", UserRole.STUDENT));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private Activity activity(int capacity, int joined, int status, LocalDateTime deadline) {
        Activity activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setCapacity(capacity);
        activity.setJoinedCount(joined);
        activity.setStatus(status);
        activity.setSignupDeadline(deadline);
        activity.setStartTime(deadline);
        return activity;
    }

    private ActivityRegistration registration(int status) {
        ActivityRegistration record = new ActivityRegistration();
        record.setId(100L);
        record.setActivityId(ACTIVITY_ID);
        record.setUserId(USER_ID);
        record.setStatus(status);
        record.setRegisteredAt(LocalDateTime.now().minusDays(1));
        return record;
    }

    @Test
    @DisplayName("未登录时直接 401，不触碰数据库")
    void registerWithoutLoginThrowsUnauthorized() {
        CurrentUserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        verify(activityMapper, never()).occupySlot(anyLong());
    }

    @Test
    @DisplayName("占位成功时写报名记录并回读最新计数")
    void registerSucceeds() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(1);
        when(activityMapper.selectForRegistrationLatest(ACTIVITY_ID)).thenReturn(activity(10, 4, ActivityStatus.PUBLISHED, future));

        RegistrationResultVO result = registrationService.register(ACTIVITY_ID);

        assertEquals(4, result.getJoinedCount());
        assertEquals(6, result.getRemaining());
        verify(registrationMapper).insert(any(ActivityRegistration.class));
        verify(registrationMapper, never()).reviveRegistration(anyLong(), anyLong());
    }

    @Test
    @DisplayName("占位失败且最新状态已满员 → ACTIVITY_FULL，且不写报名记录")
    void occupyFailureExplainsFull() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(2, 1, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(0);
        when(activityMapper.selectForRegistrationLatest(ACTIVITY_ID)).thenReturn(activity(2, 2, ActivityStatus.PUBLISHED, future));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_FULL, ex.getErrorCode());
        verify(registrationMapper, never()).insert(any(ActivityRegistration.class));
    }

    @Test
    @DisplayName("占位失败且已过截止时间 → SIGNUP_CLOSED（用最新快照解释，而不是进入事务时的旧值）")
    void occupyFailureExplainsClosedSignup() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(0);
        when(activityMapper.selectForRegistrationLatest(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, past));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.SIGNUP_CLOSED, ex.getErrorCode());
    }

    @Test
    @DisplayName("条件都满足却仍更新 0 行 → REGISTER_CONFLICT，不伪装成名额已满")
    void occupyFailureWithUnknownReasonIsRetryableConflict() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(0);
        when(activityMapper.selectForRegistrationLatest(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, future));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.REGISTER_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("活动已下架 → ACTIVITY_CLOSED，不进入占位")
    void registerCancelledActivityFails() {
        when(activityMapper.selectForRegistration(ACTIVITY_ID))
                .thenReturn(activity(10, 0, ActivityStatus.CANCELLED, LocalDateTime.now().plusDays(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_CLOSED, ex.getErrorCode());
        verify(activityMapper, never()).occupySlot(anyLong());
    }

    @Test
    @DisplayName("已是报名状态 → ALREADY_REGISTERED，不重复占用名额")
    void registerTwiceFails() {
        when(activityMapper.selectForRegistration(ACTIVITY_ID))
                .thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, LocalDateTime.now().plusDays(1)));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(registration(RegistrationStatus.REGISTERED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.ALREADY_REGISTERED, ex.getErrorCode());
        verify(activityMapper, never()).occupySlot(anyLong());
    }

    @Test
    @DisplayName("之前取消过 → 走复活路径，复用原记录而不是插入新行")
    void registerAfterCancelRevivesRow() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(10, 2, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(registration(RegistrationStatus.CANCELLED));
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(1);
        when(registrationMapper.reviveRegistration(ACTIVITY_ID, USER_ID)).thenReturn(1);
        when(activityMapper.selectForRegistrationLatest(ACTIVITY_ID)).thenReturn(activity(10, 3, ActivityStatus.PUBLISHED, future));

        RegistrationResultVO result = registrationService.register(ACTIVITY_ID);

        assertEquals(100L, result.getRegistrationId());
        verify(registrationMapper, never()).insert(any(ActivityRegistration.class));
    }

    @Test
    @DisplayName("复活被并发抢先 → ALREADY_REGISTERED，事务回滚占位")
    void reviveConflictThrows() {
        when(activityMapper.selectForRegistration(ACTIVITY_ID))
                .thenReturn(activity(10, 2, ActivityStatus.PUBLISHED, LocalDateTime.now().plusDays(1)));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(registration(RegistrationStatus.CANCELLED));
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(1);
        when(registrationMapper.reviveRegistration(ACTIVITY_ID, USER_ID)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.ALREADY_REGISTERED, ex.getErrorCode());
        // 不再回读计数：异常会让整个事务回滚，占位自然撤销
        verify(activityMapper, never()).selectForRegistrationLatest(anyLong());
    }

    @Test
    @DisplayName("并发插入撞唯一键 → 转成 ALREADY_REGISTERED，而不是 500")
    void duplicateKeyOnInsertBecomesBusinessError() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        when(activityMapper.selectForRegistration(ACTIVITY_ID)).thenReturn(activity(10, 2, ActivityStatus.PUBLISHED, future));
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(activityMapper.occupySlot(ACTIVITY_ID)).thenReturn(1);
        when(registrationMapper.insert(any(ActivityRegistration.class)))
                .thenThrow(new DuplicateKeyException("uk_registration_activity_user"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(ACTIVITY_ID));

        assertEquals(ErrorCode.ALREADY_REGISTERED, ex.getErrorCode());
    }

    @Test
    @DisplayName("取消报名：先释放名额再改状态")
    void cancelReleasesSlotThenUpdatesStatus() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(registration(RegistrationStatus.REGISTERED));
        when(activityMapper.releaseSlot(ACTIVITY_ID)).thenReturn(1);
        when(registrationMapper.cancelRegistration(ACTIVITY_ID, USER_ID)).thenReturn(1);

        registrationService.cancel(ACTIVITY_ID);

        verify(activityMapper).releaseSlot(ACTIVITY_ID);
        verify(registrationMapper).cancelRegistration(ACTIVITY_ID, USER_ID);
    }

    @Test
    @DisplayName("取消时发现已被并发取消 → NOT_REGISTERED，让事务回滚掉刚才的释放")
    void cancelRacingLosesAndRollsBack() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(registration(RegistrationStatus.REGISTERED));
        when(activityMapper.releaseSlot(ACTIVITY_ID)).thenReturn(1);
        when(registrationMapper.cancelRegistration(ACTIVITY_ID, USER_ID)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.cancel(ACTIVITY_ID));

        assertEquals(ErrorCode.NOT_REGISTERED, ex.getErrorCode());
        verify(activityMapper).releaseSlot(ACTIVITY_ID);
    }

    @Test
    @DisplayName("从未报名时取消 → NOT_REGISTERED，不触碰活动计数")
    void cancelWithoutRecordFails() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.cancel(ACTIVITY_ID));

        assertEquals(ErrorCode.NOT_REGISTERED, ex.getErrorCode());
        verify(activityMapper, never()).releaseSlot(anyLong());
    }

    @Test
    @DisplayName("查询报名状态：没有记录时返回 NONE 而不是抛异常")
    void myRegistrationReturnsNoneWhenAbsent() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);

        assertEquals("NONE", registrationService.getMyRegistration(ACTIVITY_ID).getStatus());
        verify(registrationMapper, never()).countActiveByActivity(eq(ACTIVITY_ID));
    }

    @Test
    @DisplayName("报名名单：平台管理员可以查看任意活动")
    void platformAdminCanListAnyAttendees() {
        CurrentUserContext.set(new AuthPrincipal(1L, "admin", UserRole.PLATFORM_ADMIN));
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(2L);
        when(registrationMapper.selectAttendees(ACTIVITY_ID)).thenReturn(List.of());

        assertEquals(0, registrationService.listAttendees(ACTIVITY_ID).size());
        verify(registrationMapper).selectAttendees(ACTIVITY_ID);
    }

    @Test
    @DisplayName("报名名单：社团负责人只能看自己社团的活动，别人的一律 403")
    void clubAdminCannotListOtherClubAttendees() {
        // 当前用户是社团负责人，但该活动归属的社团负责人是 2
        CurrentUserContext.set(new AuthPrincipal(23L, "other-admin", UserRole.CLUB_ADMIN));
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.listAttendees(ACTIVITY_ID));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        // 关键断言：越权时根本不去查名单，避免数据先被读出来再判断
        verify(registrationMapper, never()).selectAttendees(anyLong());
    }

    @Test
    @DisplayName("报名名单：活动不存在返回 ACTIVITY_NOT_FOUND")
    void attendeesOfMissingActivityFails() {
        CurrentUserContext.set(new AuthPrincipal(1L, "admin", UserRole.PLATFORM_ADMIN));
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.listAttendees(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
    }
}
