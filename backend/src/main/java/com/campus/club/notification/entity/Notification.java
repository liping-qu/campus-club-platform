package com.campus.club.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（表 {@code notification}）。
 * <p>
 * 这张表只负责「存消息」，不负责「产生消息」——
 * 谁在什么时机写入，由 {@code RegistrationNotificationListener} 决定。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** 取值见 {@link NotificationType}，存字符串 */
    private String type;

    /** 0 未读，1 已读。用数字而不是布尔，方便以后扩展「已忽略」等状态 */
    private Integer isRead;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
