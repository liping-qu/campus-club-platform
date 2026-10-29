package com.campus.club.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动实体，对应表 {@code activity}。
 * <p>
 * 注意：{@code joinedCount} 是冗余计数，只允许由报名/取消报名的 SQL 条件更新维护，
 * 任何接收前端参数的 DTO 都不得直接暴露该字段。
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;

    private String title;

    private String description;

    private String location;

    private String category;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime signupDeadline;

    private Integer capacity;

    /** 已报名人数（冗余计数，仅事务内维护） */
    private Integer joinedCount;

    /** 见 {@link com.campus.club.activity.entity.ActivityStatus} */
    private Integer status;

    private String coverColor;

    /** 活动规则，换行分隔的纯文本 */
    private String rules;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
