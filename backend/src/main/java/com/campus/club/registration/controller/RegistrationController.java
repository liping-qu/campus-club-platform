package com.campus.club.registration.controller;

import com.campus.club.common.ApiResponse;
import com.campus.club.registration.service.RegistrationService;
import com.campus.club.registration.vo.MyRegistrationVO;
import com.campus.club.registration.vo.RegistrationAttendeeVO;
import com.campus.club.registration.vo.RegistrationItemVO;
import com.campus.club.registration.vo.RegistrationResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报名接口。
 * <p>
 * 路径刻意做成「活动下的子资源」（{@code /activities/{id}/registrations}），
 * 而不是 {@code /registrations?activityId=}：
 * 取消报名用 {@code DELETE .../registrations/me}，{@code me} 由服务端从身份解析，
 * 前端无法通过改参数去取消别人的报名。
 */
@Tag(name = "报名", description = "活动报名、取消与查询")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "报名活动", description = "名额已满/报名截止/重复报名返回 409")
    @PostMapping("/activities/{activityId}/registrations")
    public ApiResponse<RegistrationResultVO> register(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(registrationService.register(activityId));
    }

    @Operation(summary = "取消报名", description = "未报名或已取消返回 409")
    @DeleteMapping("/activities/{activityId}/registrations/me")
    public ApiResponse<Void> cancel(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        registrationService.cancel(activityId);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询我在该活动的报名状态", description = "未报名返回 status=NONE，不返回 404")
    @GetMapping("/activities/{activityId}/registrations/me")
    public ApiResponse<MyRegistrationVO> myRegistration(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(registrationService.getMyRegistration(activityId));
    }

    @Operation(summary = "我的报名列表")
    @GetMapping("/registrations/me")
    public ApiResponse<List<RegistrationItemVO>> myRegistrations() {
        return ApiResponse.ok(registrationService.listMyRegistrations());
    }

    /**
     * 报名名单。
     * <p>
     * {@code @PreAuthorize} 只回答「这个角色能不能调这个接口」；
     * 「能看哪个活动的名单」在 Service 里按社团归属判断 —— 两层都过才算有权限。
     */
    @Operation(summary = "活动报名名单", description = "仅本社团负责人或平台管理员；其他角色返回 403")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @GetMapping("/activities/{activityId}/registrations")
    public ApiResponse<List<RegistrationAttendeeVO>> attendees(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long activityId) {
        return ApiResponse.ok(registrationService.listAttendees(activityId));
    }
}
