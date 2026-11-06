package com.campus.club.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修正某人某次活动的志愿时长。
 * <p>
 * 之所以单独一个接口而不是塞进代签：时长经常是「活动结束后统一结算」时才确定的，
 * 那时签到早就完成了。允许 0 是合法的 —— 到场但不算志愿时长（比如培训、内部会议）。
 */
@Data
public class VolunteerHoursRequest {

    @NotNull(message = "志愿时长不能为空")
    @DecimalMin(value = "0.0", message = "志愿时长不能为负")
    @DecimalMax(value = "999.9", message = "志愿时长不能超过 999.9 小时")
    @Digits(integer = 3, fraction = 1, message = "志愿时长最多 1 位小数")
    private BigDecimal volunteerHours;
}
