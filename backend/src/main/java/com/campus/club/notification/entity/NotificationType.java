package com.campus.club.notification.entity;

/**
 * 通知类型。
 * <p>
 * 落库时存名字（{@code VARCHAR}），不存序号：
 * 序号一旦调整，历史数据的含义就悄悄变了；存名字即使枚举增删也不影响已写入的行。
 */
public enum NotificationType {

    /** 报名相关：报名成功、名额变化、活动取消 */
    REGISTRATION,

    /** 签到相关：签到成功、志愿时长入账 */
    ATTENDANCE,

    /** 社团相关：审核通过、被移出社团 */
    CLUB,

    /** 系统公告 */
    SYSTEM
}
