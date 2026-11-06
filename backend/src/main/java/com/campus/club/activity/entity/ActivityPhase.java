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

    /** 草稿：还没发布，对外不可见 */
    DRAFT("草稿"),

    /** 已下架：曾经发布过，现在撤回了，报名记录保留 */
    CANCELLED("已下架"),

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
     * 顺序很重要：先判草稿与已下架（都不是「在报名」的状态），
     * 再判结束（终态会覆盖一切），再判满员，最后才轮到报名是否开放。
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
        // 草稿必须最先判：草稿的报名截止时间通常在未来，直接往下走会被算成「报名中」，
        // 于是管理端把自己还没发布的活动显示成「报名中」—— 一个纯粹由推导顺序造成的假象。
        if (status != null && status == ActivityStatus.DRAFT) {
            return DRAFT;
        }
        if (status != null && status == ActivityStatus.CANCELLED) {
            // 下架不是「结束」：活动可能还没开始就被撤回。
            // 早先这里返回 FINISHED，管理端于是把「已下架」显示成「已结束」，
            // 运营看到一条时间在未来的活动写着「已结束」，只会以为数据错了。
            return CANCELLED;
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
