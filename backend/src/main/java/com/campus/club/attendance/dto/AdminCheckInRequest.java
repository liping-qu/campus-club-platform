package com.campus.club.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 社团负责人代签（补录）。
 * <p>
 * 现场没网、手机没电、老人机扫不了码 —— 代签不是「后门」，是必需的能力。
 * 但代签必须记 {@code ADMIN} 方式，让名单上能看出「这个人不是自己签的」。
 * <p>
 * 代签<b>不受签到时间窗限制</b>：代签的典型场景就是活动结束后补录。
 * 时间窗只约束本人签到，见 {@code AttendanceServiceImpl#assertInCheckInWindow}。
 */
@Data
public class AdminCheckInRequest {

    @NotNull(message = "被代签的用户不能为空")
    @Positive(message = "用户 ID 必须为正整数")
    private Long userId;

    /**
     * 可选。不填按 0 计入，事后再用「设置志愿时长」接口补。
     * 上限 999.9 对应表上的 DECIMAL(4,1)：超过就存不下，应该在这里拦而不是让 MySQL 抛错。
     */
    @DecimalMin(value = "0.0", message = "志愿时长不能为负")
    @DecimalMax(value = "999.9", message = "志愿时长不能超过 999.9 小时")
    @Digits(integer = 3, fraction = 1, message = "志愿时长最多 1 位小数")
    private BigDecimal volunteerHours;
}
