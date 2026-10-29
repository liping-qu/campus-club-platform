package com.campus.club.activity.entity;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 活动的对外展示阶段。
 * <p>
 * 由 {@code status} + 当前时间 + 报名人数实时推导，不落库。
 * 同时用于列表筛选参数 {@code phase}。
 */
@Getter
public enum ActivityPhase {

    /** 不筛选 */
    ALL("全部"),

    /** 报名开放中，且未满员 */
    OPEN("报名中"),

    /** 报名已截止，活动尚未开始 */
    UPCOMING("即将开始"),

    /** 名额已满 */
    FULL("已满员"),

    /** 活动已结束 */
    FINISHED("已结束");

    private final String text;

    ActivityPhase(String text) {
        this.text = text;
    }

    /**
     * 阶段推导。
     * <p>
     * 顺序很重要：先判结束（终态会覆盖一切），再判满员，最后才轮到报名是否开放。
     * 否则「已满员且已结束」的活动会被展示成「已满员」。
     * <p>
     * 放在枚举上而不是某个 Service 的私有方法里，是因为活动列表和「我的报名」
     * 两条链路都要用同一套判定，复制两份必然会漂移。
     *
     * @param status         持久化状态，见 {@link ActivityStatus}
     * @param capacity       人数上限
     * @param joined         已报名人数
     * @param startTime      活动开始时间
     * @param endTime        活动结束时间
     * @param signupDeadline 报名截止时间
     * @param now            统一的时间基准，同一次响应内只取一次
     */
    public static ActivityPhase resolve(Integer status, int capacity, int joined,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        LocalDateTime signupDeadline, LocalDateTime now) {
        if (status != null && status == ActivityStatus.CANCELLED) {
            return FINISHED;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return FINISHED;
        }
        if (capacity > 0 && joined >= capacity) {
            return FULL;
        }
        if (signupDeadline != null && now.isBefore(signupDeadline)) {
            return OPEN;
        }
        return UPCOMING;
    }
}
