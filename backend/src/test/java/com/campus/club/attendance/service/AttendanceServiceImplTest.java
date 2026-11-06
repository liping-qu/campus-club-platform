package com.campus.club.attendance.service;

import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.attendance.dto.AdminCheckInRequest;
import com.campus.club.attendance.dto.VolunteerHoursRequest;
import com.campus.club.attendance.entity.ActivityAttendance;
import com.campus.club.attendance.entity.AttendanceMethod;
import com.campus.club.attendance.mapper.ActivityAttendanceMapper;
import com.campus.club.attendance.service.impl.AttendanceServiceImpl;
import com.campus.club.attendance.vo.MyAttendanceSummaryVO;
import com.campus.club.attendance.vo.MyCheckInVO;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.club.support.ClubOwnershipGuard;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.registration.entity.ActivityRegistration;
import com.campus.club.registration.entity.RegistrationStatus;
import com.campus.club.registration.mapper.ActivityRegistrationMapper;
import com.campus.club.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到分支的单元测试。
 * <p>
 * 重点不是「能不能插一条记录」，而是**每种失败都能给出对的原因**：
 * 未报名、太早、太晚、已签、活动下架，五条路对用户来说是五件不同的事，
 * 一律报「签到失败」等于让用户自己猜。
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final long ACTIVITY_ID = 10L;
    private static final long USER_ID = 6L;
    private static final long OWNER_ID = 2L;

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivityAttendanceMapper attendanceMapper;

    @Mock
    private ActivityRegistrationMapper registrationMapper;

    /** 归属判断本身有单独的 ClubOwnershipGuardTest，这里只关心「被拦下之后不写库」 */
    @Mock
    private ClubOwnershipGuard ownershipGuard;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set(new AuthPrincipal(USER_ID, "student1", UserRole.STUDENT));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    // ------------------------------------------------------------------ 夹具

    /** 正在进行中的活动：开始于 10 分钟前，结束于 2 小时后 —— 一定落在签到窗口里 */
    private Activity ongoing() {
        Activity activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setTitle("测试活动");
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setStartTime(LocalDateTime.now().minusMinutes(10));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        return activity;
    }

    private ActivityRegistration registered() {
        ActivityRegistration record = new ActivityRegistration();
        record.setActivityId(ACTIVITY_ID);
        record.setUserId(USER_ID);
        record.setStatus(RegistrationStatus.REGISTERED);
        return record;
    }

    private void givenRegistered() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(registered());
    }

    private AdminCheckInRequest adminCheckIn(Long userId, String hours) {
        AdminCheckInRequest request = new AdminCheckInRequest();
        request.setUserId(userId);
        request.setVolunteerHours(hours == null ? null : new BigDecimal(hours));
        return request;
    }

    // ------------------------------------------------------------------ 本人签到

    @Test
    @DisplayName("报名了、在窗口内 → 签到成功，记 MANUAL、时长 0")
    void checkInSucceedsWithinWindow() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        givenRegistered();

        MyCheckInVO result = attendanceService.checkIn(ACTIVITY_ID);

        ArgumentCaptor<ActivityAttendance> captor = ArgumentCaptor.forClass(ActivityAttendance.class);
        verify(attendanceMapper).insert(captor.capture());
        ActivityAttendance saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(AttendanceMethod.MANUAL, saved.getMethod());
        assertEquals(BigDecimal.ZERO, saved.getVolunteerHours());
        assertNotNull(saved.getCheckedInAt());

        assertTrue(result.isCheckedIn());
        assertEquals(ACTIVITY_ID, result.getActivityId());
    }

    @Test
    @DisplayName("没报名就签到 → 40903，不写库")
    void checkInWithoutRegistrationIsRejected() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.NOT_REGISTERED, ex.getErrorCode());
        verify(attendanceMapper, never()).insert(any(ActivityAttendance.class));
    }

    @Test
    @DisplayName("报名后又取消了 → 仍算未报名（取消是 2，不是有效报名）")
    void cancelledRegistrationCannotCheckIn() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        ActivityRegistration cancelled = registered();
        cancelled.setStatus(RegistrationStatus.CANCELLED);
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(cancelled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.NOT_REGISTERED, ex.getErrorCode());
    }

    @Test
    @DisplayName("太早（开始前 30 分钟之外）→ 40911")
    void checkInTooEarlyIsRejected() {
        Activity future = ongoing();
        future.setStartTime(LocalDateTime.now().plusHours(3));
        future.setEndTime(LocalDateTime.now().plusHours(4));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(future);
        givenRegistered();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.CHECKIN_NOT_OPEN, ex.getErrorCode());
        verify(attendanceMapper, never()).insert(any(ActivityAttendance.class));
    }

    @Test
    @DisplayName("活动已结束 → 40912（结束后只能由负责人补录）")
    void checkInAfterEndIsRejected() {
        Activity past = ongoing();
        past.setStartTime(LocalDateTime.now().minusHours(4));
        past.setEndTime(LocalDateTime.now().minusHours(2));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(past);
        givenRegistered();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.CHECKIN_CLOSED, ex.getErrorCode());
    }

    @Test
    @DisplayName("开始前 30 分钟内 → 允许签到（边界包含）")
    void checkInIsOpenWithinThirtyMinutesBeforeStart() {
        Activity soon = ongoing();
        soon.setStartTime(LocalDateTime.now().plusMinutes(20));
        soon.setEndTime(LocalDateTime.now().plusHours(2));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(soon);
        givenRegistered();

        assertTrue(attendanceService.checkIn(ACTIVITY_ID).isCheckedIn());
    }

    @Test
    @DisplayName("已经签过 → 40906，不会插第二条")
    void duplicateCheckInIsRejected() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        givenRegistered();
        when(attendanceMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID))
                .thenReturn(new ActivityAttendance());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.ALREADY_CHECKED_IN, ex.getErrorCode());
        verify(attendanceMapper, never()).insert(any(ActivityAttendance.class));
    }

    @Test
    @DisplayName("并发下唯一键拦截重复签到 → 转成 40906 而不是 500")
    void duplicateKeyBecomesBusinessError() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        givenRegistered();
        // 预查询没查到（另一个请求刚插进去，还没提交完成后的可见性），insert 时被唯一键拦下
        when(attendanceMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);
        when(attendanceMapper.insert(any(ActivityAttendance.class)))
                .thenThrow(new DuplicateKeyException("uk_attendance_activity_user"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.ALREADY_CHECKED_IN, ex.getErrorCode());
    }

    @Test
    @DisplayName("活动已下架 → 40904，不写库")
    void checkInOnCancelledActivityIsRejected() {
        Activity cancelled = ongoing();
        cancelled.setStatus(ActivityStatus.CANCELLED);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(cancelled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_CLOSED, ex.getErrorCode());
        verify(attendanceMapper, never()).insert(any(ActivityAttendance.class));
    }

    @Test
    @DisplayName("活动不存在 → 404")
    void checkInOnMissingActivityReturnsNotFound() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
    }

    // ------------------------------------------------------------------ 我的签到状态

    @Test
    @DisplayName("没签到时返回 checkedIn=false，并把窗口是否开放告诉前端")
    void myCheckInWithoutRecord() {
        Activity future = ongoing();
        future.setStartTime(LocalDateTime.now().plusDays(1));
        future.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(future);
        when(attendanceMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);

        MyCheckInVO vo = attendanceService.getMyCheckIn(ACTIVITY_ID);

        assertFalse(vo.isCheckedIn());
        assertFalse(vo.isCheckInOpen());
    }

    @Test
    @DisplayName("签过之后返回签到时间、时长与方式")
    void myCheckInWithRecord() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(ongoing());
        ActivityAttendance record = new ActivityAttendance();
        record.setActivityId(ACTIVITY_ID);
        record.setUserId(USER_ID);
        record.setCheckedInAt(LocalDateTime.now().minusMinutes(5));
        record.setVolunteerHours(new BigDecimal("1.5"));
        record.setMethod(AttendanceMethod.ADMIN);
        when(attendanceMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(record);

        MyCheckInVO vo = attendanceService.getMyCheckIn(ACTIVITY_ID);

        assertTrue(vo.isCheckedIn());
        assertEquals(new BigDecimal("1.5"), vo.getVolunteerHours());
        assertEquals(AttendanceMethod.ADMIN, vo.getMethod());
    }

    // ------------------------------------------------------------------ 代签

    @Test
    @DisplayName("代签：归属校验不通过时直接抛出，不查报名也不写库")
    void adminCheckInRequiresOwnership() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(ownershipGuard).assertCanManage(ACTIVITY_ID, CurrentUserContext.requirePrincipal());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkInForUser(ACTIVITY_ID, adminCheckIn(USER_ID, null)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(registrationMapper, never()).selectByActivityAndUser(anyLong(), anyLong());
        verify(attendanceMapper, never()).insert(any(ActivityAttendance.class));
    }

    @Test
    @DisplayName("代签：目标用户没报名 → 40903，提示的是「该用户」而不是「你」")
    void adminCheckInTargetNotRegistered() {
        when(registrationMapper.selectByActivityAndUser(ACTIVITY_ID, USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkInForUser(ACTIVITY_ID, adminCheckIn(USER_ID, null)));

        assertEquals(ErrorCode.NOT_REGISTERED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("该用户"), "代签的提示主语应该错在代签对象上：" + ex.getMessage());
    }

    @Test
    @DisplayName("代签不受时间窗限制：整条路径不读活动时间，所以活动结束后也能补录")
    void adminCheckInWorksAfterActivityEnded() {
        givenRegistered();

        attendanceService.checkInForUser(ACTIVITY_ID, adminCheckIn(USER_ID, "2.5"));

        ArgumentCaptor<ActivityAttendance> captor = ArgumentCaptor.forClass(ActivityAttendance.class);
        verify(attendanceMapper).insert(captor.capture());
        assertEquals(AttendanceMethod.ADMIN, captor.getValue().getMethod());
        assertEquals(new BigDecimal("2.5"), captor.getValue().getVolunteerHours());
        // 「不受窗口限制」最强的证据不是「活动已结束也过了」，而是这段代码压根没看时间：
        // 少一次 selectById，就少一处能把时间窗悄悄漏进代签路径的地方
        verify(activityMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("代签：不填时长按 0 计，事后再补")
    void adminCheckInDefaultsHoursToZero() {
        givenRegistered();

        attendanceService.checkInForUser(ACTIVITY_ID, adminCheckIn(USER_ID, null));

        ArgumentCaptor<ActivityAttendance> captor = ArgumentCaptor.forClass(ActivityAttendance.class);
        verify(attendanceMapper).insert(captor.capture());
        assertEquals(BigDecimal.ZERO, captor.getValue().getVolunteerHours());
    }

    @Test
    @DisplayName("代签：该用户已签到 → 40906，提示「该用户已经签到过了」")
    void adminCheckInDuplicate() {
        givenRegistered();
        when(attendanceMapper.insert(any(ActivityAttendance.class)))
                .thenThrow(new DuplicateKeyException("uk_attendance_activity_user"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkInForUser(ACTIVITY_ID, adminCheckIn(USER_ID, null)));

        assertEquals(ErrorCode.ALREADY_CHECKED_IN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("该用户"));
    }

    // ------------------------------------------------------------------ 志愿时长

    @Test
    @DisplayName("设置志愿时长：没有签到记录 → 40405")
    void updateHoursWithoutAttendanceReturns404() {
        VolunteerHoursRequest request = new VolunteerHoursRequest();
        request.setVolunteerHours(new BigDecimal("1.0"));
        when(attendanceMapper.updateVolunteerHours(ACTIVITY_ID, USER_ID, request.getVolunteerHours()))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.updateVolunteerHours(ACTIVITY_ID, USER_ID, request));

        assertEquals(ErrorCode.ATTENDANCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("设置志愿时长：成功时按传入值更新")
    void updateHoursSucceeds() {
        VolunteerHoursRequest request = new VolunteerHoursRequest();
        request.setVolunteerHours(new BigDecimal("3.0"));
        when(attendanceMapper.updateVolunteerHours(ACTIVITY_ID, USER_ID, request.getVolunteerHours()))
                .thenReturn(1);

        attendanceService.updateVolunteerHours(ACTIVITY_ID, USER_ID, request);

        verify(attendanceMapper).updateVolunteerHours(ACTIVITY_ID, USER_ID, new BigDecimal("3.0"));
    }

    // ------------------------------------------------------------------ 我的签到汇总

    @Test
    @DisplayName("我的签到汇总：SUM 为 null 时返回 0 而不是 null")
    void myAttendancesDefaultsTotalToZero() {
        when(attendanceMapper.selectMyAttendances(USER_ID)).thenReturn(List.of());
        when(attendanceMapper.sumVolunteerHours(USER_ID)).thenReturn(null);

        MyAttendanceSummaryVO summary = attendanceService.listMyAttendances();

        assertEquals(BigDecimal.ZERO, summary.getTotalHours());
        assertEquals(0, summary.getCount());
    }
}
