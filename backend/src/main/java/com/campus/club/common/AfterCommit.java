package com.campus.club.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把「必须在事务提交之后才生效」的副作用推迟到提交之后。
 *
 * <h3>为什么不能直接在事务里删缓存</h3>
 * 假设 T1 报名成功、在事务里删掉了缓存，但事务还没提交；
 * 此时 T2 读详情未命中，回源查库 —— 它读不到 T1 未提交的 {@code joined_count}，
 * 于是把<b>旧数据</b>重新写回缓存。T1 提交后，缓存里躺着的是旧值，
 * 而且会一直新鲜到 TTL 到期。这就是「删缓存删早了」导致的脏数据。
 * <p>
 * 正确做法是：事务提交之后再删。删得晚一点最多多一次回源，不会写入脏数据。
 * <p>
 * 反过来，如果事务回滚，回调根本不会执行 —— 这正好，本来就没有数据变化需要失效。
 * <p>
 * 没有事务时（单元测试、非事务调用）直接同步执行，避免调用方为了用这个工具而必须包一层事务。
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    // 事务已经提交成功，缓存失效失败不能让调用方以为业务失败，
                    // 只能记录并依赖 TTL 兜底
                    log.warn("事务提交后的后续动作执行失败: {}", ex.toString());
                }
            }
        });
    }
}
