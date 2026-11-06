package com.campus.club.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我在某个活动上的签到状态。
 * <p>
 * 参不参加是常态问题，没签到也是合法状态，所以未签到返回 {@code checkedIn = false}
 * 而不是 404 —— 前端拿它决定按钮是「签到」还是「已签到」，不该靠异常分支来驱动渲染。
 */
@Data
public class MyCheckInVO {

    private Long activityId;

    private boolean checkedIn;

    private LocalDateTime checkedInAt;

    private BigDecimal volunteerHours;

    /** 活动还没开始签到 / 已过签到时间时为 false，用于前端置灰按钮并给出原因 */
    private boolean checkInOpen;

    private String method;

    public static MyCheckInVO none(Long activityId, boolean checkInOpen) {
        MyCheckInVO vo = new MyCheckInVO();
        vo.setActivityId(activityId);
        vo.setCheckedIn(false);
        vo.setCheckInOpen(checkInOpen);
        return vo;
    }

    public static MyCheckInVO of(Long activityId, LocalDateTime checkedInAt,
                                 BigDecimal volunteerHours, String method) {
        MyCheckInVO vo = new MyCheckInVO();
        vo.setActivityId(activityId);
        vo.setCheckedIn(true);
        vo.setCheckedInAt(checkedInAt);
        vo.setVolunteerHours(volunteerHours);
        vo.setCheckInOpen(true);
        vo.setMethod(method);
        return vo;
    }
}
