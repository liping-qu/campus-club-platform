package com.campus.club.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 慢 SQL 记录。
 *
 * <h3>为什么要在 MyBatis 层再插一道</h3>
 * 访问日志能告诉你「这个接口慢了」，但说不出慢在哪：
 * 是 SQL 慢、还是 Java 里的循环慢、还是缓存没命中。
 * 在 Mapper 执行这一层计时，才能把「接口慢」和「SQL 慢」区分开。
 * <p>
 * 用的是 MyBatis 插件机制（{@link Interceptor}），不需要改任何 Mapper。
 *
 * <h3>只记语句 ID，不记 SQL 和参数</h3>
 * 打印完整 SQL 看起来方便，但参数里可能有密码哈希、手机号。
 * 日志是长期留存、会被多人查看的，一旦写进去就很难收回。
 * 记 {@code MappedStatement} 的 ID（例如 {@code ...ActivityMapper.selectActivityPage}）
 * 已经足够定位是哪条语句，具体参数可以按同一个 traceId 去查业务日志。
 *
 * <h3>阈值怎么定</h3>
 * 本地开发 200ms 已经算慢（走索引的分页查询通常在几毫秒）；
 * 生产环境视数据量调整，原则是「只记异常值」，否则慢 SQL 日志同样会被淹没。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        // 带 CacheKey / BoundSql 的重载：延迟加载与嵌套查询会走这个口
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private final long slowSqlMillis;

    public SlowSqlInterceptor(@Value("${app.log.slow-sql-millis:200}") long slowSqlMillis) {
        this.slowSqlMillis = slowSqlMillis;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (costMillis >= slowSqlMillis) {
                // 放在 finally 里：即使执行抛异常，慢的现场也值得记下来
                log.warn("slow sql statement={} cost={}ms threshold={}ms", statementId(invocation), costMillis, slowSqlMillis);
            }
        }
    }

    private String statementId(Invocation invocation) {
        Object[] args = invocation.getArgs();
        if (args.length > 0 && args[0] instanceof MappedStatement statement) {
            return statement.getId();
        }
        return "unknown";
    }
}
