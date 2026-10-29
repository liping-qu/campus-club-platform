package com.campus.club.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名实体，对应表 {@code activity_registration}。
 * <p>
 * 表上有 {@code UNIQUE (activity_id, user_id)}，所以一个用户在一个活动
 * 永远只有一行：取消报名是把 {@code status} 改成 2，重新报名是复用该行改回 1。
 */
@Data
@TableName("activity_registration")
public class ActivityRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    /** 见 {@link RegistrationStatus} */
    private Integer status;

    private LocalDateTime registeredAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
