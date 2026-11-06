package com.campus.club.attendance.controller;

import com.campus.club.attendance.dto.AdminCheckInRequest;
import com.campus.club.attendance.dto.VolunteerHoursRequest;
import com.campus.club.attendance.service.AttendanceService;
import com.campus.club.attendance.vo.AttendanceRecordVO;
import com.campus.club.attendance.vo.MyAttendanceSummaryVO;
import com.campus.club.attendance.vo.MyCheckInVO;
import com.campus.club.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 签到接口。
 * <p>
 * 路径同样做成「活动下的子资源」，并且本人相关的路径统一带 {@code me}：
 * 前端不需要（也没法）传 userId，签到对象只能由令牌解析出来。
 * <p>
 * 注意 {@code GET /activities/{id}/check-in/me} 与 {@code GET /activities/{id}/attendances}
 * 都写在 {@code GET /api/activities/**} 的公开通配符之前 —— SecurityConfig 里的顺序不能改，
 * 否则这两个接口会被当成公开接口放行，然后靠 @PreAuthorize 兜底，
 * 结果「未登录」被报成「无权限」（这个坑在 P4 已经踩过一次）。
 */
@Tag(name = "签到", description = "签到、代签、签到名单与志愿时长")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "本人签到", description = "需已报名；窗口为活动开始前 30 分钟至活动结束")
    @PostMapping("/activities/{activityId}/check-in")
    public ApiResponse<MyCheckInVO> checkIn(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(attendanceService.checkIn(activityId));
    }

    @Operation(summary = "我的签到状态", description = "未签到返回 checkedIn=false，不返回 404")
    @GetMapping("/activities/{activityId}/check-in/me")
    public ApiResponse<MyCheckInVO> myCheckIn(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(attendanceService.getMyCheckIn(activityId));
    }

    @Operation(summary = "负责人代签（补录）", description = "不受签到时间窗限制；记 ADMIN 方式")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PostMapping("/activities/{activityId}/attendances")
    public ApiResponse<AttendanceRecordVO> checkInForUser(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId,
            @Valid @RequestBody AdminCheckInRequest request) {
        return ApiResponse.ok(attendanceService.checkInForUser(activityId, request));
    }

    @Operation(summary = "活动签到名单", description = "仅本社团负责人或平台管理员")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @GetMapping("/activities/{activityId}/attendances")
    public ApiResponse<List<AttendanceRecordVO>> attendance(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(attendanceService.listAttendance(activityId));
    }

    @Operation(summary = "设置志愿时长", description = "修正某人某次活动计入的时长；没有签到记录返回 404")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PutMapping("/activities/{activityId}/attendances/{userId}/hours")
    public ApiResponse<Void> updateHours(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId,
            @Parameter(description = "被设置的用户 ID") @PathVariable @Positive(message = "用户 ID 必须为正整数") Long userId,
            @Valid @RequestBody VolunteerHoursRequest request) {
        attendanceService.updateVolunteerHours(activityId, userId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "我的签到记录", description = "含累计志愿时长，用于个人中心")
    @GetMapping("/attendances/me")
    public ApiResponse<MyAttendanceSummaryVO> myAttendances() {
        return ApiResponse.ok(attendanceService.listMyAttendances());
    }
}
