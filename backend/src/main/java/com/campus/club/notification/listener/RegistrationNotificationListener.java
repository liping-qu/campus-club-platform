package com.campus.club.notification.listener;

import com.campus.club.notification.event.RegistrationSucceededEvent;
import com.campus.club.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 报名成功后发通知。
 *
 * <h3>为什么是 {@code AFTER_COMMIT} 而不是普通的 {@code @EventListener}</h3>
 * 普通监听器在 {@code publishEvent} 那一刻就执行 —— 此时事务<b>还没提交</b>。
 * 如果后面任何一步失败回滚（唯一键冲突、约束不满足、数据库断连），
 * 用户会收到「报名成功」但数据库里根本没有这条报名 —— 这类问题排查起来极其痛苦，
 * 因为日志里明明有一条「已发送通知」。
 * <p>
 * {@code AFTER_COMMIT} 把执行时机挪到提交之后，事务回滚则监听器根本不触发。
 *
 * <h3>为什么 {@code fallbackExecution = true}</h3>
 * 它表示「没有事务时也执行」。没有事务就没有「回滚」这回事，
 * 提交即完成，此时立即执行是语义正确的；否则在事务外调用（比如某些管理脚本、
 * 单元测试）会静默不触发，行为变得依赖调用上下文。
 *
 * <h3>为什么这里不是 {@code @Async}</h3>
 * 异步边界放在 {@link NotificationService#sendRegistrationNotice} 上。
 * 如果给监听器本身加 {@code @Async}，{@code AFTER_COMMIT} 的语义会变模糊
 * —— 到底是「提交后异步执行」还是「异步地等提交」，读代码的人要靠猜。
 * 拆成「同步监听器 + 异步服务方法」，时序是一句话能说清的：
 * <b>事务提交 → 监听器被同步唤起 → 把任务丢进线程池 → 立刻返回</b>。
 * <p>
 * 顺带解决一个实际问题：{@code @TransactionalEventListener} 与 {@code @Async} 同时标注时，
 * 异常既不会回传给调用方，也可能因为代理顺序不同而落不到我们期望的线程池里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationNotificationListener {

    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRegistrationSucceeded(RegistrationSucceededEvent event) {
        try {
            notificationService.sendRegistrationNotice(event);
        } catch (TaskRejectedException ex) {
            // 队列满且线程池也满了。这是「主动丢车保帅」的选择，不是 bug，
            // 但必须留下可观测的痕迹：指标 + 一条 WARN，否则通知静默消失没人发现。
            meterRegistry.counter("app.notification.dispatch", "result", "rejected").increment();
            log.warn("通知队列已满，本次通知丢弃 userId={} activityId={}",
                    event.userId(), event.activityId());
        }
    }
}
