package com.campus.club.cache;

import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.impl.ActivityServiceImpl;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存击穿 / 穿透在<b>并发下</b>的行为测试。
 *
 * <h3>为什么单线程测不出来</h3>
 * 「防击穿」的全部价值在于「很多线程同时发现 key 过期」这一瞬间。
 * 串行调用时，第一个请求已经回填好了，第二个必然命中 —— 无论有没有互斥锁都能通过，
 * 这种测试等于没测。
 * <p>
 * 这里让 30 个线程同时撞一个冷 key，然后<b>数数据库被真实访问了几次</b>。
 * 断言口径很硬：只能 1 次。2 次说明互斥失效，30 次说明锁根本没生效。
 * <p>
 * 回源用 {@code Thread.sleep(50)} 模拟一次真实查询的耗时 —— 这不是「偷懒的假数据」，
 * 而是必要的：如果回源瞬间完成，等锁的线程还没来得及自旋就已经有值了，
 * 竞争窗口消失，测不到锁的作用。
 */
class CacheStampedeTest {

    private static final int REDIS_PORT = 16380;
    private static final long HOT_ID = 1L;
    private static final long MISSING_ID = 404L;

    private static RedisServer server;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private ActivityMapper mapper;
    private ActivityCache cache;
    private ActivityServiceImpl service;

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
                // 进程退出时会回收，不影响测试结论
            }
        }
    }

    @BeforeEach
    void setUp() {
        flushAll();
        ActivityCacheProperties properties = new ActivityCacheProperties();
        cache = new ActivityCache(redis, redisObjectMapper(), properties, new SimpleMeterRegistry());

        mapper = mock(ActivityMapper.class);
        // 每次回源都「慢」50ms，制造出足够的竞争窗口
        when(mapper.selectActivityDetail(anyLong())).thenAnswer(invocation -> {
            Thread.sleep(50);
            long id = invocation.getArgument(0, Long.class);
            // 1 是热点 id，2 只用于预热，404 用于「不存在」场景
            return (id == HOT_ID || id == 2L) ? detail(id) : null;
        });
        // 这条用例只关心读链路，社团查询用不到，传 null 即可
        service = new ActivityServiceImpl(mapper, cache, null);

        // 预热：让 Lettuce 建连、Jackson 完成首次类加载。
        // 不预热的话，第一个线程光是建立连接就可能超过等待上限（200ms），
        // 导致等锁的线程全部超时回源 —— 那时测出来的不是「互斥失效」，而是「环境冷」。
        service.getActivityDetail(2L);
        flushAll();
    }

    @Test
    @DisplayName("冷 key 被 30 个线程同时打：数据库只被访问 1 次")
    void coldKeyIsLoadedFromDatabaseOnlyOnce() throws Exception {
        List<Object> outcomes = runConcurrently(HOT_ID, 30);

        verify(mapper, times(1)).selectActivityDetail(HOT_ID);

        long errors = outcomes.stream().filter(item -> item instanceof Throwable).count();
        assertEquals(0, errors, "并发请求不应有异常，实际：" + outcomes);
        assertEquals(30, outcomes.size());
        assertEquals(ActivityCache.LookupState.HIT, cache.detail(HOT_ID).state(),
                "回填后缓存里必须有值，后续请求才会命中");
    }

    @Test
    @DisplayName("不存在的 id 被并发请求：数据库同样只被访问 1 次（空值标记挡住穿透）")
    void missingIdIsAlsoLoadedOnlyOnce() throws Exception {
        List<Object> outcomes = runConcurrently(MISSING_ID, 30);

        verify(mapper, times(1)).selectActivityDetail(MISSING_ID);

        // 每个请求都应该得到「活动不存在」，而不是空指针或其它异常
        long notFound = outcomes.stream()
                .filter(item -> item instanceof BusinessException ex
                        && ex.getErrorCode() == ErrorCode.ACTIVITY_NOT_FOUND)
                .count();
        assertEquals(30, notFound, "所有请求都应返回 ACTIVITY_NOT_FOUND，实际：" + outcomes);
        assertEquals(ActivityCache.LookupState.NOT_FOUND, cache.detail(MISSING_ID).state());
    }

    // ------------------------------------------------------------------

    /** 所有线程在同一时刻放行，把竞争压进同一毫秒 */
    private List<Object> runConcurrently(long activityId, int threads) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Object> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    outcomes.add(service.getActivityDetail(activityId));
                } catch (Exception ex) {
                    // 业务异常也是结果之一（比如「活动不存在」），不能被吞掉
                    outcomes.add(ex);
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未能全部就绪");
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求未在 60 秒内完成");
        return outcomes;
    }

    private ActivityDetailVO detail(long id) {
        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(id);
        vo.setTitle("校园摄影展");
        vo.setClubId(1L);
        vo.setClubName("摄影协会");
        vo.setLocation("图书馆一层");
        vo.setCategory("文艺");
        vo.setCapacity(50);
        vo.setJoinedCount(10);
        vo.setStatus(1);
        vo.setStartTime(LocalDateTime.now().plusDays(3));
        vo.setEndTime(LocalDateTime.now().plusDays(4));
        vo.setSignupDeadline(LocalDateTime.now().plusDays(2));
        vo.setRules("请提前十分钟到场");
        return vo;
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
}
