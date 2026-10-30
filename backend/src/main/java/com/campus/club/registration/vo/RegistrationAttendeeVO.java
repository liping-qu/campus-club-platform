package com.campus.club.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名名单项，仅社团负责人 / 平台管理员可见。
 * <p>
 * 这里会带出手机号吗？不带。名单用途是签到核对，昵称 + 学号 + 学院已经够用，
 * 联系方式属于额外暴露面，需要时再单独授权查询。
 */
@Data
public class RegistrationAttendeeVO {

    private Long registrationId;

    private Long userId;

    private String nickname;

    private String studentNo;

    private String college;

    private LocalDateTime registeredAt;

    /** 是否已签到，名单页用来区分「报了但没来」 */
    private Boolean checkedIn;
}
