package com.campus.club.attendance.service.impl;

import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.attendance.dto.AdminCheckInRequest;
import com.campus.club.attendance.dto.VolunteerHoursRequest;
import com.campus.club.attendance.entity.ActivityAttendance;
import com.campus.club.attendance.entity.AttendanceMethod;
import com.campus.club.attendance.mapper.ActivityAttendanceMapper;
import com.campus.club.attendance.service.AttendanceService;
import com.campus.club.attendance.vo.AttendanceRecordVO;
import com.campus.club.attendance.vo.MyAttendanceItemVO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到服务实现。
 *
 * <h3>为什么签到要读报名表</h3>
 * 「到场的人一定是报名的人」是签到表与报名表之间唯一的不变量。
 * 如果只写签到表不校验报名，任何人都能凭一个 activityId 给自己签到刷志愿时长 ——
 * 而志愿时长是要写进评奖材料的。所以签到路径的第一件事是查报名。
 *
 * <h3>为什么签到不失效活动缓存</h3>
 * 活动详情/列表里没有任何签到信息，{@code joined_count} 也不因签到变化。
 * 每次写都顺手 evict 一次缓存看着「保险」，实际只是白白让详情缓存穿透一次。
 * 唯一会读签到数据的是报名名单接口，它直接查库、不走缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    /**
     * 签到提前量：活动开始前 30 分钟开放。
     * <p>
     * 太早（比如提前一天）等于允许「人没到先把卡打了」，这个窗口就失去意义；
     * 太晚（开始那一刻才开）会让提前到场的人排队等。30 分钟是现场组织常用的集合提前量。
     */
    private static final Duration CHECKIN_OPEN_BEFORE_START = Duration.ofMinutes(30);

    private final ActivityMapper activityMapper;
    private final ActivityAttendanceMapper attendanceMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final ClubOwnershipGuard ownershipGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MyCheckInVO checkIn(Long activityId) {
        Long userId = CurrentUserContext.require();
        Activity activity = requirePublishedActivity(activityId);

        // 先判报名，再判时间窗：没报名的人看到「签到尚未开始」只会更困惑
        requireRegistered(activityId, userId, ErrorCode.NOT_REGISTERED.getMessage());
        if (attendanceMapper.selectByActivityAndUser(activityId, userId) != null) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }

        LocalDateTime now = LocalDateTime.now();
        assertInCheckInWindow(activity, now);

        ActivityAttendance record = new ActivityAttendance();
        record.setActivityId(activityId);
        record.setUserId(userId);
        record.setCheckedInAt(now);
        record.setVolunteerHours(BigDecimal.ZERO);
        record.setMethod(AttendanceMethod.MANUAL);
        try {
            attendanceMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 上面那次「有没有签过」的查询只是快速失败，真正的裁决是唯一键。
            // 两个并发请求都可能通过查询，这里必须把第二个转成业务异常而不是 500。
            log.debug("唯一键拦截了重复签到 activityId={} userId={}", activityId, userId);
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }

        log.info("签到成功 activityId={} userId={} method={}", activityId, userId, AttendanceMethod.MANUAL);
        return MyCheckInVO.of(activityId, now, BigDecimal.ZERO, AttendanceMethod.MANUAL);
    }

    @Override
    @Transactional(readOnly = true)
    public MyCheckInVO getMyCheckIn(Long activityId) {
        Long userId = CurrentUserContext.require();
        Activity activity = requirePublishedActivity(activityId);
        boolean open = isInCheckInWindow(activity, LocalDateTime.now());

        ActivityAttendance record = attendanceMapper.selectByActivityAndUser(activityId, userId);
        if (record == null) {
            return MyCheckInVO.none(activityId, open);
        }
        return MyCheckInVO.of(activityId, record.getCheckedInAt(),
                record.getVolunteerHours(), record.getMethod());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceRecordVO checkInForUser(Long activityId, AdminCheckInRequest request) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        // 活动是否存在由 guard 判定（查不到 404），归属判断也都在这一处
        ownershipGuard.assertCanManage(activityId, principal);

        requireRegistered(activityId, request.getUserId(), "该用户没有报名这个活动");

        BigDecimal hours = request.getVolunteerHours() == null
                ? BigDecimal.ZERO : request.getVolunteerHours();
        ActivityAttendance record = new ActivityAttendance();
        record.setActivityId(activityId);
        record.setUserId(request.getUserId());
        record.setCheckedInAt(LocalDateTime.now());
        record.setVolunteerHours(hours);
        record.setMethod(AttendanceMethod.ADMIN);
        try {
            attendanceMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN, "该用户已经签到过了");
        }

        log.info("代签成功 activityId={} targetUserId={} operator={} hours={}",
                activityId, request.getUserId(), principal.userId(), hours);
        return attendanceMapper.selectRecord(activityId, request.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVolunteerHours(Long activityId, Long userId, VolunteerHoursRequest request) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        ownershipGuard.assertCanManage(activityId, principal);

        if (attendanceMapper.updateVolunteerHours(activityId, userId, request.getVolunteerHours()) == 0) {
            // 时长依附于签到记录：人没签到就没有「这次活动的时长」可言
            throw new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        log.info("志愿时长已更新 activityId={} targetUserId={} hours={} operator={}",
                activityId, userId, request.getVolunteerHours(), principal.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceRecordVO> listAttendance(Long activityId) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        ownershipGuard.assertCanManage(activityId, principal);
        return attendanceMapper.selectAttendanceList(activityId);
    }

    @Override
    @Transactional(readOnly = true)
    public MyAttendanceSummaryVO listMyAttendances() {
        Long userId = CurrentUserContext.require();
        List<MyAttendanceItemVO> records = attendanceMapper.selectMyAttendances(userId);
        BigDecimal total = attendanceMapper.sumVolunteerHours(userId);

        MyAttendanceSummaryVO summary = new MyAttendanceSummaryVO();
        // SUM 在没有记录时返回 0（SQL 里已 COALESCE），这里再兜一次 null：
        // 前端拿到 null 会渲染成「null 小时」，比 0 难解释得多
        summary.setTotalHours(total == null ? BigDecimal.ZERO : total);
        summary.setCount(records.size());
        summary.setRecords(records);
        return summary;
    }

    // ------------------------------------------------------------------ 内部

    private Activity requirePublishedActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() == null || activity.getStatus() != ActivityStatus.PUBLISHED) {
            // 草稿/已下架的活动对参与者来说等于不存在，但状态是可解释的，用 40904 而不是 404
            throw new BusinessException(ErrorCode.ACTIVITY_CLOSED);
        }
        return activity;
    }

    /**
     * 报名是签到的前置条件。查的是「有效报名」（status=1）：
     * 取消过报名的人来签到，等于给一个已经退出的人记志愿时长。
     */
    private void requireRegistered(Long activityId, Long userId, String message) {
        ActivityRegistration registration = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (registration == null || !RegistrationStatus.isRegistered(registration.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_REGISTERED, message);
        }
    }

    /**
     * 签到窗口的唯一定义在 {@link #isInCheckInWindow}，这里只负责把「为什么不在窗口里」说清楚。
     * <p>
     * 两处各写一遍边界判断是这类代码最常见的漂移来源：改了常量忘了改另一处，
     * 于是「能不能签」和「为什么不能签」互相矛盾。
     */
    private void assertInCheckInWindow(Activity activity, LocalDateTime now) {
        if (isInCheckInWindow(activity, now)) {
            return;
        }
        LocalDateTime start = activity.getStartTime();
        LocalDateTime end = activity.getEndTime();
        if (start != null && now.isBefore(start.minus(CHECKIN_OPEN_BEFORE_START))) {
            throw new BusinessException(ErrorCode.CHECKIN_NOT_OPEN);
        }
        if (end != null && now.isAfter(end)) {
            throw new BusinessException(ErrorCode.CHECKIN_CLOSED);
        }
        // 走到这里说明活动的起止时间根本没填全，属于数据问题，不是用户的问题
        throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT, "活动时间不完整，无法签到");
    }

    /** 窗口：开始前 30 分钟 ~ 结束时间（两端都含） */
    private boolean isInCheckInWindow(Activity activity, LocalDateTime now) {
        LocalDateTime start = activity.getStartTime();
        LocalDateTime end = activity.getEndTime();
        return start != null && end != null
                && !now.isBefore(start.minus(CHECKIN_OPEN_BEFORE_START))
                && !now.isAfter(end);
    }
}
