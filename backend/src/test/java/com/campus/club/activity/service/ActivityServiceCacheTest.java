package com.campus.club.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.impl.ActivityServiceImpl;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.cache.ActivityCache;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.common.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动服务里的缓存编排：什么时候查库、什么时候回填、什么情况下不能查库。
 * <p>
 * 缓存组件本身（序列化、TTL、版本号、互斥锁）用真实 Redis 测，见 {@code ActivityCacheTest}；
 * 这一层只关心「决策」。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceCacheTest {

    private static final long ACTIVITY_ID = 1L;

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ActivityCache activityCache;

    @InjectMocks
    private ActivityServiceImpl activityService;

    // ---------------------------------------------------------------- 测试数据

    private ActivityCache.DetailLookup hit(ActivityDetailVO detail) {
        return new ActivityCache.DetailLookup(ActivityCache.LookupState.HIT, detail);
    }

    private ActivityCache.DetailLookup miss() {
        return new ActivityCache.DetailLookup(ActivityCache.LookupState.MISS, null);
    }

    private ActivityCache.DetailLookup notFound() {
        return new ActivityCache.DetailLookup(ActivityCache.LookupState.NOT_FOUND, null);
    }

    /** 一条「报名已截止、活动还没开始」的活动 */
    private ActivityDetailVO detailSignedUpClosed() {
        LocalDateTime now = LocalDateTime.now();
        ActivityDetailVO detail = new ActivityDetailVO();
        detail.setId(ACTIVITY_ID);
        detail.setTitle("摄影展");
        detail.setStatus(ActivityStatus.PUBLISHED);
        detail.setCapacity(10);
        detail.setJoinedCount(3);
        detail.setSignupDeadline(now.minusHours(1));
        detail.setStartTime(now.plusDays(2));
        detail.setEndTime(now.plusDays(3));
        return detail;
    }

    // ---------------------------------------------------------------- 详情

    @Test
    @DisplayName("详情命中缓存：不查库，且阶段按当前时间重算而不是沿用缓存里的旧值")
    void detailCacheHitSkipsDatabaseAndRecomputesPhase() {
        ActivityDetailVO cached = detailSignedUpClosed();
        // 缓存里存的是「上次算出来的结论」，此时还是报名中
        cached.setPhase("OPEN");
        cached.setPhaseText("报名中");
        cached.setRemaining(7);

        when(activityCache.isEnabled()).thenReturn(true);
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(hit(cached));

        ActivityDetailVO result = activityService.getActivityDetail(ACTIVITY_ID);

        // 报名已截止 → 必须重算成「即将开始」，不能因为缓存还新鲜就一直显示报名中
        assertEquals("UPCOMING", result.getPhase());
        assertEquals("即将开始", result.getPhaseText());
        assertEquals(7, result.getRemaining());
        verify(activityMapper, never()).selectActivityDetail(anyLong());
    }

    @Test
    @DisplayName("详情未命中：查一次库并回填缓存")
    void detailCacheMissLoadsDatabaseAndFillsCache() {
        when(activityCache.isEnabled()).thenReturn(true);
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(miss());
        when(activityCache.tryLock(ACTIVITY_ID)).thenReturn(true);
        when(activityMapper.selectActivityDetail(ACTIVITY_ID)).thenReturn(detailSignedUpClosed());

        ActivityDetailVO result = activityService.getActivityDetail(ACTIVITY_ID);

        assertEquals("UPCOMING", result.getPhase());
        assertEquals("即将开始", result.getPhaseText());
        verify(activityMapper, times(1)).selectActivityDetail(ACTIVITY_ID);
        verify(activityCache).putDetail(eq(ACTIVITY_ID), any(ActivityDetailVO.class));
        verify(activityCache).unlock(ACTIVITY_ID);
    }

    @Test
    @DisplayName("空值标记命中：直接判定不存在，不再查库（防穿透）")
    void nullMarkerSkipsDatabase() {
        when(activityCache.isEnabled()).thenReturn(true);
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(notFound());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.getActivityDetail(ACTIVITY_ID));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
        verify(activityMapper, never()).selectActivityDetail(anyLong());
    }

    @Test
    @DisplayName("活动真的不存在：查库一次后写入空值标记，下次请求就打不到库")
    void missingActivityWritesNullMarker() {
        when(activityCache.isEnabled()).thenReturn(true);
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(miss());
        when(activityCache.tryLock(ACTIVITY_ID)).thenReturn(true);
        when(activityMapper.selectActivityDetail(ACTIVITY_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> activityService.getActivityDetail(ACTIVITY_ID));

        verify(activityCache).putDetailNotFound(ACTIVITY_ID);
        verify(activityCache, never()).putDetail(anyLong(), any());
    }

    @Test
    @DisplayName("抢不到回填锁：先等别人回填，等到就直接用，不重复查库")
    void waitsForOtherLoaderWhenLockNotAcquired() {
        ActivityDetailVO filled = detailSignedUpClosed();

        when(activityCache.isEnabled()).thenReturn(true);
        // 第一次 MISS，之后（等待期间）变成 HIT —— 模拟另一个线程刚把数据填好
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(miss(), hit(filled));
        when(activityCache.tryLock(ACTIVITY_ID)).thenReturn(false);

        ActivityDetailVO result = activityService.getActivityDetail(ACTIVITY_ID);

        assertEquals("摄影展", result.getTitle());
        verify(activityMapper, never()).selectActivityDetail(anyLong());
        // 没抢到锁就不该去释放别人的锁
        verify(activityCache, never()).unlock(anyLong());
    }

    @Test
    @DisplayName("抢不到锁且等不到回填：自己查库兜底，不让用户一直等")
    void fallsBackToDatabaseWhenWaitTimesOut() {
        when(activityCache.isEnabled()).thenReturn(true);
        when(activityCache.detail(ACTIVITY_ID)).thenReturn(miss());
        when(activityCache.tryLock(ACTIVITY_ID)).thenReturn(false);
        when(activityMapper.selectActivityDetail(ACTIVITY_ID)).thenReturn(detailSignedUpClosed());

        ActivityDetailVO result = activityService.getActivityDetail(ACTIVITY_ID);

        assertEquals("摄影展", result.getTitle());
        verify(activityMapper, times(1)).selectActivityDetail(ACTIVITY_ID);
    }

    @Test
    @DisplayName("缓存被关掉或已降级：跳过锁与缓存，直接查库")
    void disabledCacheGoesStraightToDatabase() {
        when(activityCache.isEnabled()).thenReturn(false);
        when(activityMapper.selectActivityDetail(ACTIVITY_ID)).thenReturn(detailSignedUpClosed());

        activityService.getActivityDetail(ACTIVITY_ID);

        verify(activityCache, never()).detail(anyLong());
        verify(activityCache, never()).tryLock(anyLong());
    }

    // ---------------------------------------------------------------- 列表

    @Test
    @DisplayName("列表命中缓存：不查库，且阶段同样按当前时间重算")
    void listCacheHitSkipsDatabase() {
        ActivityListVO stale = new ActivityListVO();
        stale.setId(2L);
        stale.setStatus(ActivityStatus.PUBLISHED);
        stale.setCapacity(10);
        stale.setJoinedCount(10);
        stale.setSignupDeadline(LocalDateTime.now().plusDays(1));
        stale.setStartTime(LocalDateTime.now().plusDays(2));
        stale.setEndTime(LocalDateTime.now().plusDays(3));
        stale.setPhase("OPEN");
        stale.setPhaseText("报名中");
        stale.setRemaining(0);

        when(activityCache.list(anyString())).thenReturn(Optional.of(
                PageResult.of(List.of(stale), 1, 1, 10)));

        PageResult<ActivityListVO> result = activityService.pageActivities(new ActivityQuery());

        assertEquals("FULL", result.getRecords().get(0).getPhase());
        assertEquals(0, result.getRecords().get(0).getRemaining());
        verify(activityMapper, never()).selectActivityPage(any(), any(), any());
    }

    @Test
    @DisplayName("列表未命中：查库一次并回填，回填的是不含推导字段的原始快照")
    void listCacheMissLoadsDatabaseAndFillsCache() {
        ActivityListVO vo = new ActivityListVO();
        vo.setId(3L);
        vo.setStatus(ActivityStatus.PUBLISHED);
        vo.setCapacity(20);
        vo.setJoinedCount(1);
        vo.setSignupDeadline(LocalDateTime.now().plusDays(1));
        vo.setStartTime(LocalDateTime.now().plusDays(2));
        vo.setEndTime(LocalDateTime.now().plusDays(3));

        Page<ActivityListVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(activityCache.list(anyString())).thenReturn(Optional.empty());
        when(activityMapper.selectActivityPage(any(), any(), any())).thenReturn(page);

        PageResult<ActivityListVO> result = activityService.pageActivities(new ActivityQuery());

        assertEquals("OPEN", result.getRecords().get(0).getPhase());
        assertEquals(19, result.getRecords().get(0).getRemaining());
        verify(activityCache).putList(anyString(), any(PageResult.class));
    }
}
