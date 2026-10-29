package com.campus.club.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动列表项。
 * <p>
 * {@code joinedCount} 来自数据库冗余计数，由事务维护；
 * {@code remaining} 与 {@code phase} 是查询时推导的只读值，不落库。
 */
@Data
public class ActivityListVO {

    private Long id;
    private String title;
    private Long clubId;
    private String clubName;
    private String location;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime signupDeadline;
    private Integer capacity;
    private Integer joinedCount;
    private Integer remaining;
    private Integer status;
    /** ActivityPhase 名称，例如 OPEN */
    private String phase;
    /** 面向用户的中文阶段，例如「报名中」 */
    private String phaseText;
    private String coverColor;
}
