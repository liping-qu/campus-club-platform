package com.campus.club.registration.vo;

import com.campus.club.registration.entity.RegistrationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我在这个活动上的报名状态」。
 * <p>
 * 详情页需要它来决定按钮是「立即报名」还是「取消报名」，
 * 而不是像原型阶段那样把报名状态存在前端本地缓存里 —— 那种做法换设备就丢。
 */
@Data
public class MyRegistrationVO {

    /** NONE / REGISTERED / CANCELLED */
    private String status;

    private String statusText;

    private LocalDateTime registeredAt;

    public static MyRegistrationVO none() {
        MyRegistrationVO vo = new MyRegistrationVO();
        vo.setStatus("NONE");
        vo.setStatusText("未报名");
        return vo;
    }

    public static MyRegistrationVO of(Integer status, LocalDateTime registeredAt) {
        if (RegistrationStatus.isRegistered(status)) {
            MyRegistrationVO vo = new MyRegistrationVO();
            vo.setStatus("REGISTERED");
            vo.setStatusText("已报名");
            vo.setRegisteredAt(registeredAt);
            return vo;
        }
        if (RegistrationStatus.isCancelled(status)) {
            MyRegistrationVO vo = new MyRegistrationVO();
            vo.setStatus("CANCELLED");
            vo.setStatusText("已取消");
            vo.setRegisteredAt(registeredAt);
            return vo;
        }
        return none();
    }
}
