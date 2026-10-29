package com.campus.club.activity.entity;

/**
 * 活动在数据库中的持久化状态。
 * <p>
 * 「报名中 / 已满员 / 已结束」这类展示状态不落库，
 * 因为它们随时间变化，落库必然产生不一致，改为查询时按时间与人数推导。
 */
public final class ActivityStatus {

    private ActivityStatus() {
    }

    /** 草稿，仅创建者可见 */
    public static final int DRAFT = 0;

    /** 已发布，可被检索和报名 */
    public static final int PUBLISHED = 1;

    /** 已下架/已取消，保留报名历史但不再接受新报名 */
    public static final int CANCELLED = 2;

    /** 已结束 */
    public static final int FINISHED = 3;
}
