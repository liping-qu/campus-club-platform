package com.campus.club.cache;

import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存组件的行为测试 —— 连的是<b>真 Redis</b>（内嵌启动，端口 16379）。
 *
 * <h3>为什么不用 mock</h3>
 * 缓存这一层最怕的 bug 恰好都藏在「真实协议行为」里：
 * 序列化能不能往返（LocalDateTime、继承字段）、TTL 到底有没有设上、
 * SETNX 是不是真的互斥。用 mock 测这些等于只测了自己写的 if。
 * <p>
 * 内嵌 Redis 自带 Windows 二进制，测试不依赖本机装 Redis 或起 docker，
 * 换台机器 clone 下来就能跑。
 */
class ActivityCacheTest {

    private static final int REDIS_PORT = 16379;

    private static RedisServer server;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private ActivityCacheProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ActivityCache cache;

    @BeforeAll
    static void startEmbeddedRedis() throws IOException {
        server = new RedisServer(REDIS_PORT);
        server.start();

        connectionFactory = new LettuceConnectionFactory("127.0.0.1", REDIS_PORT);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void stopEmbeddedRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (server != null) {
            try {
                server.stop();
            } catch (IOException ex) {
                // 停不掉也不影响测试结论，进程退出时会被回收
            }
        }
    }

    @BeforeEach
    void setUp() {
        flushAll();
        properties = new ActivityCacheProperties();
        meterRegistry = new SimpleMeterRegistry();
        cache = new ActivityCache(redis, redisObjectMapper(), properties, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        // 每个用例独立，避免降级标记串场
        cache.resetDegraded();
    }

    private static ObjectMapper redisObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static void flushAll() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    /** KEYS 在测试里可以用；生产代码里绝不能这么写 */
    private static java.util.Set<String> keys(String pattern) {
        java.util.Set<String> result = redis.keys(pattern);
        return result == null ? java.util.Set.of() : result;
    }

    private ActivityDetailVO detail() {
        ActivityDetailVO detail = new ActivityDetailVO();
        detail.setId(1L);
        detail.setTitle("摄影展");
        detail.setCapacity(10);
        detail.setJoinedCount(3);
        detail.setStartTime(LocalDateTime.of(2026, 11, 1, 19, 0));
        detail.setSignupDeadline(LocalDateTime.of(2026, 10, 30, 9, 12, 0));
        detail.setRuleList(List.of("规则一", "规则二"));
        return detail;
    }

    // ---------------------------------------------------------------- 序列化往返

    @Test
    @DisplayName("详情缓存往返：LocalDateTime 与继承字段都能原样读回")
    void detailRoundTripKeepsLocalDateTime() {
        cache.putDetail(1L, detail());

        ActivityCache.DetailLookup lookup = cache.detail(1L);

        assertEquals(ActivityCache.LookupState.HIT, lookup.state());
        ActivityDetailVO cached = lookup.value();
        assertEquals("摄影展", cached.getTitle());
        assertEquals(LocalDateTime.of(2026, 11, 1, 19, 0), cached.getStartTime());
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 12, 0), cached.getSignupDeadline());
        assertEquals(List.of("规则一", "规则二"), cached.getRuleList());
    }

    @Test
    @DisplayName("未写过的 id 是未命中，不是「不存在」——两者语义必须分开")
    void unknownKeyIsMissNotNotFound() {
        assertEquals(ActivityCache.LookupState.MISS, cache.detail(404L).state());
    }

    @Test
    @DisplayName("空值标记：读出来是 NOT_FOUND，能挡住对不存在 id 的重复穿透")
    void nullMarkerReadsAsNotFound() {
        cache.putDetailNotFound(404L);

        assertEquals(ActivityCache.LookupState.NOT_FOUND, cache.detail(404L).state());
        // 标记本身有 TTL，不能永久占着内存
        assertTrue(redis.getExpire("club:activity:detail:404", TimeUnit.SECONDS) > 0);
    }

    // ---------------------------------------------------------------- TTL 与抖动

    @Test
    @DisplayName("详情 TTL 落在「基础值 ± 抖动」区间内，且确实设置了过期时间")
    void detailTtlIsJitteredAroundBase() {
        cache.putDetail(1L, detail());

        Long ttl = redis.getExpire("club:activity:detail:1", TimeUnit.SECONDS);
        long base = properties.getDetailTtl().toSeconds();
        int jitter = properties.getJitterSeconds();

        assertNotNull(ttl);
        assertTrue(ttl > 0, "必须设置过期时间，否则缓存永不失效");
        assertTrue(ttl >= base - jitter && ttl <= base + jitter,
                "TTL 应落在 [" + (base - jitter) + ", " + (base + jitter) + "]，实际 " + ttl);
    }

    @Test
    @DisplayName("多条数据的 TTL 不完全相同 —— 抖动把失效时间摊开了，避免同时失效造成雪崩")
    void ttlsAreSpreadByJitter() {
        for (long id = 1; id <= 30; id++) {
            cache.putDetail(id, detail());
        }

        List<Long> ttls = keys("club:activity:detail:*").stream()
                .map(key -> redis.getExpire(key, TimeUnit.SECONDS))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        assertTrue(ttls.size() > 1, "30 个 key 的 TTL 全一样，说明抖动没生效");
    }

    // ---------------------------------------------------------------- 列表与版本号失效

    @Test
    @DisplayName("列表缓存往返")
    void listRoundTrip() {
        ActivityListVO vo = new ActivityListVO();
        vo.setId(1L);
        vo.setTitle("摄影展");
        cache.putList("fp-1", PageResult.of(List.of(vo), 1, 1, 10));

        Optional<PageResult<ActivityListVO>> cached = cache.list("fp-1");

        assertTrue(cached.isPresent());
        assertEquals(1, cached.get().getTotal());
        assertEquals(10, cached.get().getSize());
        assertEquals("摄影展", cached.get().getRecords().get(0).getTitle());
    }

    @Test
    @DisplayName("失效：详情被删掉，列表靠版本号整体失效，且旧 key 不会被立刻遍历删除")
    void evictRemovesDetailAndBumpsListVersion() {
        cache.putDetail(1L, detail());
        cache.putList("fp-1", PageResult.of(List.of(), 0, 1, 10));
        assertTrue(cache.list("fp-1").isPresent());

        cache.evict(1L);

        assertEquals(ActivityCache.LookupState.MISS, cache.detail(1L).state());
        // 版本号 +1 之后，同一个查询条件读的是新 key，旧 key 自然读不到
        assertFalse(cache.list("fp-1").isPresent());
        // 首次失效：计数器从「不存在=1」推进到 2
        assertEquals("2", redis.opsForValue().get("club:activity:list:ver"));
    }

    @Test
    @DisplayName("列表版本号单调递增，不会因为读不到而回退")
    void listVersionIncrements() {
        cache.evict(1L);
        cache.evict(1L);

        assertEquals("3", redis.opsForValue().get("club:activity:list:ver"));
    }

    // ---------------------------------------------------------------- 回填互斥

    @Test
    @DisplayName("回填锁互斥：同一活动只有第一个线程能拿到，释放后可以再拿")
    void lockIsExclusive() {
        assertTrue(cache.tryLock(1L));
        assertFalse(cache.tryLock(1L), "第二个线程不应该拿到同一把锁");

        cache.unlock(1L);

        assertTrue(cache.tryLock(1L), "释放之后应能重新获取");
    }

    @Test
    @DisplayName("锁带过期时间：持锁线程崩了不会把 key 永久锁死")
    void lockHasTtl() {
        cache.tryLock(1L);

        Long ttl = redis.getExpire("club:activity:lock:1", TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0);
    }

    // ---------------------------------------------------------------- 降级

    @Test
    @DisplayName("缓存总开关关闭时：所有操作都当未命中，且不去连 Redis")
    void disabledCacheBehavesAsMiss() {
        properties.setEnabled(false);

        assertEquals(ActivityCache.LookupState.MISS, cache.detail(1L).state());
        assertFalse(cache.list("fp-1").isPresent());
        assertFalse(cache.tryLock(1L));

        // 关闭时不该写入任何东西
        assertTrue(keys("club:activity:*").isEmpty());
    }

    @Test
    @DisplayName("Redis 连不上：不抛异常、当作未命中，并进入降级静默期")
    void unavailableRedisDegradesInsteadOfThrowing() {
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory("127.0.0.1", 16398);
        deadFactory.afterPropertiesSet();
        try {
            StringRedisTemplate deadTemplate = new StringRedisTemplate(deadFactory);
            deadTemplate.afterPropertiesSet();
            ActivityCache deadCache = new ActivityCache(deadTemplate, redisObjectMapper(), properties);

            assertTrue(deadCache.isEnabled(), "连不上之前应认为是可用的");

            // 不抛异常，直接当作未命中，让调用方走数据库
            assertEquals(ActivityCache.LookupState.MISS, deadCache.detail(1L).state());

            assertFalse(deadCache.isEnabled(), "失败后应进入降级期，后续请求不再尝试");
            assertTrue(deadCache.degradedRemainingMillis() > 0);
        } finally {
            deadFactory.destroy();
        }
    }

    // ---------------------------------------------------------------- 指标

    private double counter(String cacheName, String result) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find("app.cache.requests")
                .tag("cache", cacheName)
                .tag("result", result)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("命中率指标：hit / miss / null-marker / bypass 分开计数，否则算不出真实命中率")
    void cacheAccessIsMeasured() {
        cache.putDetail(1L, detail());
        cache.detail(1L);          // hit
        cache.detail(404L);        // miss
        cache.putDetailNotFound(9L);
        cache.detail(9L);          // null-marker
        properties.setEnabled(false);
        cache.detail(1L);          // bypass（缓存被关掉）

        assertEquals(1.0, counter("activity-detail", "hit"));
        assertEquals(1.0, counter("activity-detail", "miss"));
        assertEquals(1.0, counter("activity-detail", "null-marker"));
        assertEquals(1.0, counter("activity-detail", "bypass"));
    }

    @Test
    @DisplayName("Redis 故障会计一次 redis-errors，不会悄悄失败")
    void redisErrorIsCounted() {
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory("127.0.0.1", 16397);
        deadFactory.afterPropertiesSet();
        try {
            StringRedisTemplate deadTemplate = new StringRedisTemplate(deadFactory);
            deadTemplate.afterPropertiesSet();
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ActivityCache deadCache = new ActivityCache(deadTemplate, redisObjectMapper(), properties, registry);

            deadCache.detail(1L);

            io.micrometer.core.instrument.Counter errors = registry.find("app.cache.redis-errors").counter();
            assertNotNull(errors);
            assertEquals(1.0, errors.count());
        } finally {
            deadFactory.destroy();
        }
    }

    // ---------------------------------------------------------------- 键指纹

    @Test
    @DisplayName("指纹稳定：同样的查询条件得到同一个 key，不同条件不会碰撞")
    void fingerprintIsStableAndDistinct() {
        ActivityQuery a = new ActivityQuery();
        a.setKeyword("摄影");

        ActivityQuery b = new ActivityQuery();
        b.setKeyword("摄影");

        ActivityQuery c = new ActivityQuery();
        c.setKeyword("摄影");
        c.setPage(2);

        ActivityQuery d = new ActivityQuery();
        d.setPhase(ActivityPhase.OPEN);

        assertEquals(ActivityCache.fingerprint(a), ActivityCache.fingerprint(b));
        assertFalse(ActivityCache.fingerprint(a).equals(ActivityCache.fingerprint(c)));
        assertFalse(ActivityCache.fingerprint(a).equals(ActivityCache.fingerprint(d)));
        assertEquals(20, ActivityCache.fingerprint(a).length());
    }
}
