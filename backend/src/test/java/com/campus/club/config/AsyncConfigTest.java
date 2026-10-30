package com.campus.club.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MDC 真的能跨线程传递。
 *
 * <p><b>为什么值得专门测一次</b>：这件事没有「看起来的那么自然」。
 * {@code MDC} 底层是 {@code ThreadLocal}，丢进线程池的任务跑在另一个线程上，
 * 拿不到提交线程的上下文 —— 结果是 P6 建立起来的链路在异步边界断掉，
 * 而且不会有任何报错，只是日志里悄悄变成 {@code traceId=no-trace}。
 * 这种「静默降级」比报错难发现得多，所以用测试把它钉住。
 */
class AsyncConfigTest {

    private final AsyncConfig.MdcPropagatingDecorator decorator =
            new AsyncConfig.MdcPropagatingDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("提交线程的 traceId 会带进执行线程")
    void propagatesTraceIdToWorkerThread() throws Exception {
        MDC.put("traceId", "abc123def456");
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Runnable decorated = decorator.decorate(() -> {
            seen.set(MDC.get("traceId"));
            done.countDown();
        });

        // 放到另一个线程跑：同线程跑的话 ThreadLocal 本来就在，测不出任何东西
        Thread worker = new Thread(decorated, "test-worker");
        worker.start();
        assertTrue(done.await(2, TimeUnit.SECONDS), "装饰后的任务未执行");

        assertEquals("abc123def456", seen.get(), "执行线程应拿到提交线程的 traceId");
    }

    @Test
    @DisplayName("任务结束后清空执行线程的 MDC，避免污染复用的线程")
    void clearsContextAfterRun() throws Exception {
        MDC.put("traceId", "will-be-cleared");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> afterRun = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            pool.submit(decorator.decorate(() -> { /* 什么都不用做 */ }));
            // 同一个线程接着跑一个未装饰的任务：如果上一个任务没清 MDC，这里会读到残留
            pool.submit(() -> {
                afterRun.set(MDC.get("traceId"));
                done.countDown();
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertNull(afterRun.get(), "上一个任务必须清掉 MDC，否则线程池会串味");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("提交时没有 MDC：执行线程也不会凭空多出内容")
    void handlesMissingContext() throws Exception {
        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("sentinel");
        CountDownLatch done = new CountDownLatch(1);

        Thread worker = new Thread(decorator.decorate(() -> {
            seen.set(MDC.get("traceId"));
            done.countDown();
        }));
        worker.start();
        assertTrue(done.await(2, TimeUnit.SECONDS));

        assertNull(seen.get());
    }
}
