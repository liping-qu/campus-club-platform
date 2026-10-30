package com.campus.club.cache;

import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 活动读缓存。
 *
 * <h3>只负责「怎么存、怎么取」，不掺业务判断</h3>
 * 缓存键长什么样、存多久、失效怎么做，全部收敛在这里；
 * 回源查库、写不写缓存、什么时候回填，由 {@code ActivityServiceImpl} 决定。
 * 这样缓存层可以脱离数据库单独测试。
 *
 * <h3>三个经典问题各自对应的处理</h3>
 * <table>
 *   <tr><th>问题</th><th>现象</th><th>这里的做法</th></tr>
 *   <tr>
 *     <td>穿透</td>
 *     <td>一直查不存在的 id，缓存永远不命中，每次都打到库</td>
 *     <td>查不到时写一个「空值标记」并给短 TTL，
 *         第二次请求直接判定「不存在」，不再查库</td>
 *   </tr>
 *   <tr>
 *     <td>击穿</td>
 *     <td>热点 key 刚过期，成百上千个线程同时回源同一条数据</td>
 *     <td>{@code SETNX} 抢回填锁，只有抢到的线程查库，其余线程短暂等待后读缓存；
 *         等不到就自己回源 —— 宁可多查一次库，也不能让用户卡住</td>
 *   </tr>
 *   <tr>
 *     <td>雪崩</td>
 *     <td>大批 key 同一时刻失效，流量整批压到数据库</td>
 *     <td>TTL 加随机抖动（{@code jitterSeconds}），把失效时间摊开</td>
 *   </tr>
 * </table>
 *
 * <h3>缓存挂了不能把业务带崩</h3>
 * 所有 Redis 调用都包在容错里：失败就当作未命中，直接让调用方查库。
 * 失败之后进入 {@code degradedCooldown} 静默期，期间不再尝试连接，
 * 避免每个请求都去撞一个挂掉的 Redis（等待超时比查库还慢）。
 *
 * <h3>键的设计</h3>
 * <pre>
 *   club:activity:detail:{id}                    活动详情
 *   club:activity:list:ver                       列表版本号（一个计数器）
 *   club:activity:list:v{ver}:{指纹}              某个查询条件下的列表页
 *   club:activity:lock:{id}                      详情回填锁
 * </pre>
 * 列表用「版本号」而不是「删掉所有列表 key」来做失效：
 * 活动数一多，列表 key 就是几十上百个，{@code KEYS} / {@code SCAN} 删键在线上是禁止操作。
 * 版本号 +1 之后旧 key 自然读不到（等 TTL 到期自己消失），代价是一次 INCR。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityCache {

    private static final String KEY_PREFIX = "club:activity:";
    private static final String DETAIL_PREFIX = KEY_PREFIX + "detail:";
    private static final String LIST_VERSION_KEY = KEY_PREFIX + "list:ver";
    private static final String LIST_PREFIX = KEY_PREFIX + "list:v";
    private static final String LOCK_PREFIX = KEY_PREFIX + "lock:";

    /** 缓存「这个 id 不存在」。必须是任何正常 JSON 都不会等于的字符串 */
    private static final String NULL_MARKER = "__NULL__";

    private static final TypeReference<PageSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ActivityCacheProperties properties;

    /** 降级截止时间（epoch 毫秒）。0 表示未降级 */
    private final AtomicLong degradedUntil = new AtomicLong();

    /** 详情查缓存的结果：命中 / 未命中 / 明确不存在，三者语义不同，不能用 Optional 混淆 */
    public enum LookupState { HIT, MISS, NOT_FOUND }

    public record DetailLookup(LookupState state, ActivityDetailVO value) {

        static DetailLookup hit(ActivityDetailVO value) {
            return new DetailLookup(LookupState.HIT, value);
        }

        static DetailLookup miss() {
            return new DetailLookup(LookupState.MISS, null);
        }

        static DetailLookup notFound() {
            return new DetailLookup(LookupState.NOT_FOUND, null);
        }
    }

    /**
     * 列表缓存里存的不是 {@link PageResult}（它的字段 final 且没有无参构造，反序列化不了），
     * 而是一个朴素快照；读出来再还原成 PageResult。
     */
    public record PageSnapshot(List<ActivityListVO> records, long total, int page, int size) {
    }

    /** 缓存当前是否可用：被配置关掉、或处于故障静默期，都算不可用 */
    public boolean isEnabled() {
        return properties.isEnabled() && !degraded();
    }

    // ------------------------------------------------------------------ 详情

    public DetailLookup detail(long activityId) {
        if (!isEnabled()) {
            return DetailLookup.miss();
        }
        String json = safely("读活动详情", () -> redis.opsForValue().get(detailKey(activityId)), null);
        if (json == null) {
            return DetailLookup.miss();
        }
        if (NULL_MARKER.equals(json)) {
            return DetailLookup.notFound();
        }
        try {
            return DetailLookup.hit(objectMapper.readValue(json, ActivityDetailVO.class));
        } catch (Exception ex) {
            // 缓存里是脏数据（比如实体字段改过）：当作未命中，让调用方回源覆盖掉
            log.warn("活动详情缓存反序列化失败，按未命中处理 id={}: {}", activityId, ex.toString());
            return DetailLookup.miss();
        }
    }

    public void putDetail(long activityId, ActivityDetailVO detail) {
        if (!isEnabled()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            log.warn("活动详情序列化失败，跳过写缓存 id={}: {}", activityId, ex.toString());
            return;
        }
        Duration ttl = jittered(properties.getDetailTtl());
        safely("写活动详情", () -> {
            redis.opsForValue().set(detailKey(activityId), json, ttl);
            return null;
        }, null);
    }

    /** 写入「不存在」标记，挡住对同一个不存在 id 的反复穿透 */
    public void putDetailNotFound(long activityId) {
        if (!isEnabled()) {
            return;
        }
        Duration ttl = jittered(properties.getNullMarkerTtl());
        safely("写空值标记", () -> {
            redis.opsForValue().set(detailKey(activityId), NULL_MARKER, ttl);
            return null;
        }, null);
    }

    // ------------------------------------------------------------------ 列表

    public Optional<PageResult<ActivityListVO>> list(String fingerprint) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        long version = listVersion();
        String json = safely("读活动列表", () -> redis.opsForValue().get(listKey(version, fingerprint)), null);
        if (json == null) {
            return Optional.empty();
        }
        try {
            PageSnapshot snapshot = objectMapper.readValue(json, SNAPSHOT_TYPE);
            return Optional.of(PageResult.of(snapshot.records(), snapshot.total(), snapshot.page(), snapshot.size()));
        } catch (Exception ex) {
            log.warn("活动列表缓存反序列化失败，按未命中处理: {}", ex.toString());
            return Optional.empty();
        }
    }

    public void putList(String fingerprint, PageResult<ActivityListVO> page) {
        if (!isEnabled()) {
            return;
        }
        PageSnapshot snapshot = new PageSnapshot(page.getRecords(), page.getTotal(), page.getPage(), page.getSize());
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            log.warn("活动列表序列化失败，跳过写缓存: {}", ex.toString());
            return;
        }
        long version = listVersion();
        Duration ttl = jittered(properties.getListTtl());
        safely("写活动列表", () -> {
            redis.opsForValue().set(listKey(version, fingerprint), json, ttl);
            return null;
        }, null);
    }

    // ------------------------------------------------------------------ 失效

    /**
     * 活动数据变化后调用：删掉详情，并把列表版本号 +1。
     * <p>
     * 注意这里<b>不</b>判断降级状态：降级期间写入也可能成功（比如刚好是 Redis 刚恢复），
     * 一旦因为降级跳过失效，旧详情会在 Redis 里留到 TTL 到期，变成「明明刚报名成功，详情里还显示没报」。
     * 失效一定尽最大努力执行；真的删不掉，就只能靠 TTL 兜底，这是本方案已知的最长陈旧时间。
     */
    public void evict(long activityId) {
        if (!properties.isEnabled()) {
            return;
        }
        safely("失效活动缓存", () -> {
            redis.delete(detailKey(activityId));
            // 计数器不存在时，INCR 会得到 1 —— 那正好等于「默认版本号」，
            // 等于没失效。所以先把初始值补上，保证每次失效都真的推进一个版本。
            redis.opsForValue().setIfAbsent(LIST_VERSION_KEY, "1");
            redis.opsForValue().increment(LIST_VERSION_KEY);
            return null;
        }, null);
        log.debug("活动缓存已失效 activityId={}", activityId);
    }

    // ------------------------------------------------------------------ 回填互斥

    /** 抢回填锁。抢不到返回 false，调用方负责「等一会儿再读」或「自己回源」 */
    public boolean tryLock(long activityId) {
        if (!isEnabled()) {
            return false;
        }
        Boolean acquired = safely("抢回填锁", () -> redis.opsForValue()
                .setIfAbsent(lockKey(activityId), "1", properties.getLockTtl()), false);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(long activityId) {
        safely("释放回填锁", () -> redis.delete(lockKey(activityId)), null);
    }

    /**
     * 把查询条件压成一个短指纹。
     * <p>
     * 不用 {@code String.hashCode()}：它只有 32 位且碰撞概率在缓存键这个量级上不可忽略，
     * 一旦碰撞就会把 A 条件的列表返回给 B 条件。SHA-256 截断 20 位十六进制（80 位）足够。
     * <p>
     * 调用前必须已经完成 keyword / category 归一化，否则「摄影」和「 摄影 」会算成两个 key。
     */
    public static String fingerprint(ActivityQuery query) {
        String raw = query.safePage() + "|" + query.safeSize() + "|"
                + nullSafe(query.getKeyword()) + "|" + nullSafe(query.getCategory()) + "|"
                + (query.getPhase() == null ? ActivityPhase.ALL.name() : query.getPhase().name());
        return sha256(raw).substring(0, 20);
    }

    // ------------------------------------------------------------------ 内部

    private boolean degraded() {
        long until = degradedUntil.get();
        return until > 0 && System.currentTimeMillis() < until;
    }

    /**
     * 标记降级。只在「健康 → 降级」这一刻打日志，
     * 否则 Redis 挂掉期间每个请求都会刷一条 warn，把日志打爆。
     */
    private void markDegraded(String action, Exception ex) {
        long now = System.currentTimeMillis();
        long until = now + properties.getDegradedCooldown().toMillis();
        long previous = degradedUntil.getAndSet(until);
        if (previous <= now) {
            log.warn("缓存{}失败，进入 {} 秒降级期，期间直接查库: {}",
                    action, properties.getDegradedCooldown().toSeconds(), ex.toString());
        }
    }

    /** 所有 Redis 调用的统一容错壳：失败不抛，返回兜底值 */
    private <T> T safely(String action, Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            markDegraded(action, ex);
            return fallback;
        }
    }

    /** 读列表版本号。读不到（Redis 里还没这个键）就当作 1，不额外写入 */
    private long listVersion() {
        String value = safely("读列表版本号", () -> redis.opsForValue().get(LIST_VERSION_KEY), null);
        if (value == null) {
            return 1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            log.warn("列表版本号异常，按 1 处理: {}", value);
            return 1L;
        }
    }

    /** TTL 加抖动：base ± jitter 秒，保证结果至少 1 秒 */
    private Duration jittered(Duration base) {
        int jitter = Math.max(0, properties.getJitterSeconds());
        long delta = jitter == 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitter, jitter + 1L);
        long seconds = base.getSeconds() + delta;
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private String detailKey(long activityId) {
        return DETAIL_PREFIX + activityId;
    }

    private String listKey(long version, String fingerprint) {
        return LIST_PREFIX + version + ":" + fingerprint;
    }

    private String lockKey(long activityId) {
        return LOCK_PREFIX + activityId;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // JDK 必须提供 SHA-256，走到这里说明运行环境异常，不能再假装能出 key
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    /** 供测试与排障使用：剩余降级时间（毫秒），未降级返回 0 */
    long degradedRemainingMillis() {
        long until = degradedUntil.get();
        long remaining = until - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /** 供测试使用：清掉降级标记 */
    void resetDegraded() {
        degradedUntil.set(0);
    }
}
