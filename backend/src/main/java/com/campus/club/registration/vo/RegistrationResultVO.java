package com.campus.club.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名成功的返回结果。
 * <p>
 * 带上报名后的最新计数，前端可以直接更新详情页的进度条，
 * 不必为了一个数字再发一次详情请求。
 */
@Data
public class RegistrationResultVO {

    private Long activityId;

    private Long registrationId;

    /** 报名后的已报名人数（事务内回读的真实值，不是「加一」推出来的） */
    private Integer joinedCount;

    /** 报名后的剩余名额 */
    private Integer remaining;

    private LocalDateTime registeredAt;
}
