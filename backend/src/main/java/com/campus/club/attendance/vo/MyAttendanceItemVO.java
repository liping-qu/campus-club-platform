package com.campus.club.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的签到记录项（含活动信息），用于「志愿时长」页。
 */
@Data
public class MyAttendanceItemVO {

    private Long activityId;

    private String title;

    private String clubName;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime checkedInAt;

    private BigDecimal volunteerHours;

    private String method;
}
