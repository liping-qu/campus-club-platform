package com.campus.club.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表项。
 * <p>
 * 字段名用 {@code read} 而不是 {@code isRead}：
 * 前者序列化成 {@code "read": false}，前端直接当布尔用；
 * 后者会变成 {@code "isRead"}，前端还得记住这个前缀。
 */
@Data
public class NotificationVO {

    private Long id;

    private String title;

    private String content;

    private String type;

    private Boolean read;

    private LocalDateTime createdAt;
}
