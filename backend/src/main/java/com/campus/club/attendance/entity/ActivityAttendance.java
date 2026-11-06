package com.campus.club.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动签到记录，对应表 {@code activity_attendance}。
 * <p>
 * 表上有 {@code UNIQUE (activity_id, user_id)}：同一个人在同一活动最多一条签到记录。
 * 所以「重复签到」不是靠 Service 里的 if 判断挡住的，而是结构上不可能出现两条。
 * <p>
 * {@code volunteerHours} 用 {@link BigDecimal} 而不是 double：
 * 时长要参与累加（「我的志愿时长」是求和结果），double 的 0.1 + 0.2 会给出 0.30000000000000004，
 * 展示时再四舍五入等于把误差藏起来。DECIMAL(4,1) 在库里本来就是精确值。
 */
@Data
@TableName("activity_attendance")
public class ActivityAttendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    private LocalDateTime checkedInAt;

    /** 本次计入的志愿时长（小时），DECIMAL(4,1) */
    private BigDecimal volunteerHours;

    /** 见 {@link AttendanceMethod} */
    private String method;

    private LocalDateTime createdAt;
}
