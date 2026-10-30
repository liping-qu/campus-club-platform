package com.campus.club.notification.event;

import java.time.LocalDateTime;

/**
 * 报名成功事件。
 *
 * <h3>为什么把字段都塞进事件里，而不是只传一个 activityId</h3>
 * 监听方是在<b>事务提交之后、另一个线程</b>里执行的。那时既没有事务上下文，
 * 也不应该再查一次库：
 * <ul>
 *   <li>再查一次 = 一次额外的数据库往返，异步省下来的延迟又还回去了；</li>
 *   <li>更糟的是<b>读到的是新数据</b>：订单提交后活动可能被改标题、被下架，
 *       用户收到的通知就会和「他当初报的那个活动」对不上。</li>
 * </ul>
 * 所以事件里带的是「事务内看到的快照」，异步方只做渲染，不再回查。
 *
 * @param activityId    活动 ID
 * @param userId        报名人
 * @param activityTitle 活动标题（快照）
 * @param location      活动地点（快照）
 * @param startTime     活动开始时间（快照）
 * @param remaining     报名后剩余名额（快照，由事务内回读的真实计数算出）
 */
public record RegistrationSucceededEvent(
        Long activityId,
        Long userId,
        String activityTitle,
        String location,
        LocalDateTime startTime,
        int remaining) {
}
