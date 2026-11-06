package com.campus.club.activity.controller;

import com.campus.club.activity.dto.ActivityCreateRequest;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.dto.ActivityUpdateRequest;
import com.campus.club.activity.service.ActivityService;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.ApiResponse;
import com.campus.club.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动接口：两张读接口对外公开，其余要求社团负责人或平台管理员。
 * <p>
 * Controller 只做三件事：接收参数、触发校验、调用 Service 并包装响应。
 * 不写业务判断，也不直接访问 Mapper —— 所以这里看不到任何
 * 「是不是这个社团的」「名额够不够」之类的 if。
 */
@Tag(name = "活动", description = "活动查询与管理")
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "分页查询活动", description = "支持关键词、分类、展示阶段筛选；只返回已发布活动")
    @GetMapping
    public ApiResponse<PageResult<ActivityListVO>> list(@Valid ActivityQuery query) {
        return ApiResponse.ok(activityService.pageActivities(query));
    }

    /**
     * 管理端列表：含草稿与已下架。
     * <p>
     * 路径写在 {@code /{id}} 之前只是可读性；Spring 的路径匹配本身会让字面量
     * {@code manage} 优先于变量 {@code {id}}，不会把 manage 当成 id 去转数字。
     */
    @Operation(summary = "管理端活动列表", description = "社团负责人只看自己社团；平台管理员看全部；含草稿与已下架")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @GetMapping("/manage")
    public ApiResponse<PageResult<ActivityDetailVO>> manage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") int pageNo,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 50, message = "每页条数不能超过 50") int pageSize,
            @Parameter(description = "状态过滤：0 草稿 / 1 已发布 / 2 已下架 / 3 已结束，不传则不过滤")
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(activityService.pageManageable(pageNo, pageSize, status));
    }

    @Operation(summary = "查询活动详情", description = "只返回已发布活动；草稿对公众不可见")
    @GetMapping("/{id}")
    public ApiResponse<ActivityDetailVO> detail(
            @Parameter(description = "活动 ID") @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id) {
        return ApiResponse.ok(activityService.getActivityDetail(id));
    }

    @Operation(summary = "新建活动", description = "初始状态为草稿；只能给自己负责的社团建")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PostMapping
    public ApiResponse<ActivityDetailVO> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.ok(activityService.createActivity(request));
    }

    @Operation(summary = "修改活动", description = "整体覆盖可编辑字段；已下架/已结束不可改；名额不得小于已报名人数")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<ActivityDetailVO> update(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id,
            @Valid @RequestBody ActivityUpdateRequest request) {
        return ApiResponse.ok(activityService.updateActivity(id, request));
    }

    @Operation(summary = "发布活动", description = "草稿或已下架 → 已发布；报名截止时间已过则拒绝")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PostMapping("/{id}/publish")
    public ApiResponse<ActivityDetailVO> publish(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id) {
        return ApiResponse.ok(activityService.publishActivity(id));
    }

    @Operation(summary = "下架活动", description = "已发布 → 已下架；已报名的记录保留")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @PostMapping("/{id}/unpublish")
    public ApiResponse<ActivityDetailVO> unpublish(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id) {
        return ApiResponse.ok(activityService.unpublishActivity(id));
    }

    @Operation(summary = "删除活动", description = "只能删草稿；已发布的只能下架，避免级联删掉报名记录")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "活动 ID 必须为正整数") Long id) {
        activityService.deleteActivity(id);
        return ApiResponse.ok();
    }
}
