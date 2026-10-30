package com.campus.club.notification.controller;

import com.campus.club.common.ApiResponse;
import com.campus.club.common.PageResult;
import com.campus.club.notification.service.NotificationService;
import com.campus.club.notification.vo.NotificationVO;
import com.campus.club.notification.vo.UnreadCountVO;
import com.campus.club.notification.vo.UpdatedCountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口。
 * <p>
 * 路径里没有 userId 参数 —— 一律从令牌解析。
 * 一旦允许传 userId，就得在每个方法里记得校验「传的是不是自己」，
 * 漏一次就是越权读别人的通知。设计上不给这个参数，就没有漏的机会。
 */
@Tag(name = "通知", description = "站内通知列表、未读数与已读标记")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表", description = "按时间倒序；pageSize 上限 50")
    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(notificationService.listMy(pageNo, pageSize));
    }

    @Operation(summary = "未读数", description = "红点用，走覆盖索引，代价可以忽略")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount() {
        return ApiResponse.ok(new UnreadCountVO(notificationService.unreadCount()));
    }

    @Operation(summary = "标记单条已读", description = "通知不存在或不属于当前用户返回 404")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable @Positive(message = "通知 ID 必须为正整数") Long id) {
        notificationService.markRead(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "全部标记已读", description = "返回本次实际更新条数")
    @PostMapping("/read-all")
    public ApiResponse<UpdatedCountVO> markAllRead() {
        return ApiResponse.ok(new UpdatedCountVO(notificationService.markAllRead()));
    }
}
