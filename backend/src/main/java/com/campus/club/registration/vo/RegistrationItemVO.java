package com.campus.club.registration.vo;

import com.campus.club.activity.vo.ActivityListVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 「我的报名」列表项。
 * <p>
 * 直接复用活动列表的字段（{@code id} 即活动 ID），再补上报名自身的属性：
 * 两个列表展示的是同一份活动信息，字段复制一份只会让两处慢慢长歪。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationItemVO extends ActivityListVO {

    /** 报名记录 ID */
    private Long registrationId;

    /** 1 已报名，2 已取消 */
    private Integer registrationStatus;

    /** 最近一次报名成功时间 */
    private LocalDateTime registeredAt;
}
