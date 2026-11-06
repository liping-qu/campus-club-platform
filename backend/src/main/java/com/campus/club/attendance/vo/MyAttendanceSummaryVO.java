package com.campus.club.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 我的签到汇总：累计时长 + 记录列表。
 * <p>
 * 为什么把总数放在接口里而不是让前端把列表加一遍：
 * 总数将来要支持「按学年统计」「只算审核通过的」，规则一旦落到前端就会和别处的统计口径分叉。
 * 现在列表不分页（一个人的签到记录一年最多几十条），总数与列表来自同一份数据，不会对不上。
 */
@Data
public class MyAttendanceSummaryVO {

    /** 累计志愿时长（小时），没有记录时为 0.0 而不是 null */
    private BigDecimal totalHours;

    private int count;

    private List<MyAttendanceItemVO> records;
}
