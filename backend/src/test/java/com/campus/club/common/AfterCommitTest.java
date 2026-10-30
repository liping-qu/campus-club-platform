package com.campus.club.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「提交后才执行」的工具测试。
 * <p>
 * 这里不启真实事务：{@link TransactionSynchronizationManager} 的同步机制本身就能手动驱动，
 * 用 {@code initSynchronization()} + 手动调用 {@code afterCommit()} 就能精确验证时机，
 * 比开一个真事务再等它提交要确定得多。
 */
class AfterCommitTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("没有事务时立即执行，不强迫调用方包一层事务")
    void runsImmediatelyWithoutTransaction() {
        AtomicInteger counter = new AtomicInteger();

        AfterCommit.run(counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("有事务时推迟到提交之后 —— 提交前执行会让并发读把旧值写回缓存")
    void defersUntilCommit() {
        AtomicInteger counter = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(counter::incrementAndGet);

        assertEquals(0, counter.get(), "注册同步之后、提交之前不应执行");

        // 模拟事务提交
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("事务回滚时不执行：本来就没有数据变化需要失效")
    void doesNotRunOnRollback() {
        AtomicInteger counter = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(counter::incrementAndGet);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertEquals(0, counter.get());
    }

    @Test
    @DisplayName("后续动作抛异常不影响已经提交的事务")
    void swallowsExceptionAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> {
            throw new IllegalStateException("缓存删不掉");
        });

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        assertDoesNotThrow(synchronization::afterCommit);
    }
}
