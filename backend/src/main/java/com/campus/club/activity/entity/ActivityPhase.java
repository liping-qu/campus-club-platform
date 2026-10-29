package com.campus.club.activity.entity;

import lombok.Getter;

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
}
