package com.campus.club.activity.controller;

import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.service.ActivityService;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.ApiResponse;
import com.campus.club.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动查询接口。
 * <p>
 * Controller 只做三件事：接收参数、触发校验、调用 Service 并包装响应。
 * 不写业务判断，也不直接访问 Mapper。
 */
@Tag(name = "活动", description = "活动列表与详情查询")
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "分页查询活动", description = "支持关键词、分类、展示阶段筛选")
    @GetMapping
    public ApiResponse<PageResult<ActivityListVO>> list(@Valid ActivityQuery query) {
        return ApiResponse.ok(activityService.pageActivities(query));
    }

    @Operation(summary = "查询活动详情")
    @GetMapping("/{id}")
    public ApiResponse<ActivityDetailVO> detail(
            @Parameter(description = "活动 ID") @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id) {
        return ApiResponse.ok(activityService.getActivityDetail(id));
    }
}
