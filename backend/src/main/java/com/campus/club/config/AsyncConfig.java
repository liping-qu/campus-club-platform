package com.campus.club.config;

import com.campus.club.notification.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置。
 *
 * <h3>为什么必须自己定义线程池，不能用默认的</h3>
 * {@code @Async} 找不到 executor 时，Spring 退回到 {@code SimpleAsyncTaskExecutor}：
 * <b>每个任务新建一个线程，没有上限、没有队列</b>。通知高峰时它会一直开线程直到
 * 系统 OOM —— 一个「优化响应时间」的功能，最后把整个服务拖垮。
 * 所以这里显式给一个有界队列 + 固定线程数的池子，把「过载」从「崩溃」降级成「拒绝」。
 *
 * <h3>为什么拒绝策略是 Abort，而不是 CallerRuns</h3>
 * {@code CallerRunsPolicy} 在队列满时让<b>提交任务的线程</b>自己跑。
 * 在我们的场景里，提交者就是处理 HTTP 请求的 Tomcat 线程 ——
 * 等于队列一满，异步立刻退化成同步，用户在报名接口上多等一次 INSERT。
 * 那正是引入异步要避免的事。这里选择快速失败：
 * 由调用方捕获 {@code TaskRejectedException}、计数、打一条 WARN，
 * <b>丢掉一条通知，好过拖慢一次报名</b>。
 *
 * <h3>为什么需要 TaskDecorator</h3>
 * {@code MDC} 是 ThreadLocal 绑在线程上的，跨线程不会自动传递。
 * 不复制的话，异步线程里打出来的日志全是 {@code traceId=no-trace}，
 * P6 好不容易串起来的链路在这里断掉。装饰器在提交时抓一份 MDC 快照，
 * 执行时换上、结束后清掉（不清会污染线程池里的下一个任务）。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor(NotificationProperties properties,
                                                       MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(60);
        // 线程名带上业务前缀：jstack 里一眼能看出是谁在占线程
        executor.setThreadNamePrefix("notify-");
        executor.setTaskDecorator(new MdcPropagatingDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅停机：已接单的通知跑完再退，别让用户收到「报名成功」却查不到记录
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();

        // 队列积压和活跃线程数是「异步是不是快跟不上了」最直接的信号
        Gauge.builder("app.notification.queue.size", executor,
                        e -> e.getThreadPoolExecutor().getQueue().size())
                .description("通知线程池当前排队任务数")
                .register(meterRegistry);
        Gauge.builder("app.notification.pool.active", executor,
                        e -> e.getThreadPoolExecutor().getActiveCount())
                .description("通知线程池当前活跃线程数")
                .register(meterRegistry);
        return executor;
    }

    /**
     * 让「没写 executor 名字的 {@code @Async}」也落到同一个池子上。
     * <p>
     * 不注册这个 Bean 的话，Spring 会悄悄退回 {@code SimpleAsyncTaskExecutor}（无界创建线程），
     * 而且不会有任何提示 —— 等到线上线程数爆炸时才发现。
     * 顺带把未捕获异常处理器挂上：返回 void 的异步方法抛异常不会回传给调用方，
     * 没有处理器就是彻底静默丢任务。
     */
    @Bean
    public AsyncConfigurer asyncConfigurer(
            @Qualifier(NOTIFICATION_EXECUTOR) ThreadPoolTaskExecutor notificationExecutor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return notificationExecutor;
            }

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (ex, method, params) ->
                        log.error("异步任务未捕获异常 method={} 参数个数={}", method.getName(),
                                params.length, ex);
            }
        };
    }

    /**
     * 把提交线程的 MDC 上下文带到执行线程。
     * <p>
     * 用副本而不是引用：提交之后主线程还会继续改自己的 MDC（比如 finally 里 clear），
     * 直接共享同一个 Map 会让异步线程读到已经被清空的内容。
     */
    static class MdcPropagatingDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> snapshot = MDC.getCopyOfContextMap();
            return () -> {
                if (snapshot != null) {
                    MDC.setContextMap(snapshot);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    // 线程是复用的：不清掉，下一个任务的日志会带上别人的 traceId
                    MDC.clear();
                }
            };
        }
    }
}
