package com.campus.club.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到名单项，仅社团负责人 / 平台管理员可见。
 * <p>
 * 字段取舍与报名名单一致：昵称 + 学号 + 学院够核对用了，
 * 不带手机号/邮箱 —— 签到核对不需要联系方式，多带一个字段就多一个泄露面。
 */
@Data
public class AttendanceRecordVO {

    private Long userId;

    private String nickname;

    private String studentNo;

    private String college;

    private LocalDateTime checkedInAt;

    /** 本次计入的志愿时长（小时） */
    private BigDecimal volunteerHours;

    /** MANUAL 自签 / ADMIN 代签 / QR_CODE 扫码，见 AttendanceMethod */
    private String method;
}
