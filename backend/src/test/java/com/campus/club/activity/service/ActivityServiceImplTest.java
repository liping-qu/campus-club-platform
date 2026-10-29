package com.campus.club.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.impl.ActivityServiceImpl;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动读模型的单元测试。
 * <p>
 * 只测业务推导逻辑，不连数据库 —— 数据库行为由集成测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock
    private ActivityMapper activityMapper;

    @InjectMocks
    private ActivityServiceImpl activityService;

    private ActivityListVO activity(int capacity, int joined,
                                    LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        ActivityListVO vo = new ActivityListVO();
        vo.setId(1L);
        vo.setTitle("测试活动");
        vo.setStatus(ActivityStatus.PUBLISHED);
        vo.setCapacity(capacity);
        vo.setJoinedCount(joined);
        vo.setStartTime(start);
        vo.setEndTime(end);
        vo.setSignupDeadline(deadline);
        return vo;
    }

    private ActivityListVO queryOne(ActivityListVO vo) {
        Page<ActivityListVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);
        when(activityMapper.selectActivityPage(any(), any(), any())).thenReturn(page);

        PageResult<ActivityListVO> result = activityService.pageActivities(new ActivityQuery());
        return result.getRecords().get(0);
    }

    @Test
    @DisplayName("结束时间已过 → 已结束")
    void phaseIsFinishedWhenEndTimePassed() {
        LocalDateTime now = LocalDateTime.now();
        ActivityListVO vo = activity(60, 3, now.minusDays(3), now.minusDays(2), now.minusDays(4));

        ActivityListVO result = queryOne(vo);

        assertEquals("FINISHED", result.getPhase());
        assertEquals("已结束", result.getPhaseText());
    }

    @Test
    @DisplayName("报名数达到容量 → 已满员，剩余名额不为负")
    void phaseIsFullWhenJoinedReachesCapacity() {
        LocalDateTime now = LocalDateTime.now();
        ActivityListVO vo = activity(2, 2, now.plusDays(2), now.plusDays(3), now.plusDays(1));

        ActivityListVO result = queryOne(vo);

        assertEquals("FULL", result.getPhase());
        assertEquals(0, result.getRemaining());
    }

    @Test
    @DisplayName("未到报名截止且有余量 → 报名中")
    void phaseIsOpenBeforeDeadline() {
        LocalDateTime now = LocalDateTime.now();
        ActivityListVO vo = activity(30, 2, now.plusDays(5), now.plusDays(6), now.plusDays(4));

        ActivityListVO result = queryOne(vo);

        assertEquals("OPEN", result.getPhase());
        assertEquals(28, result.getRemaining());
    }

    @Test
    @DisplayName("报名已截止但活动未开始 → 即将开始")
    void phaseIsUpcomingBetweenDeadlineAndStart() {
        LocalDateTime now = LocalDateTime.now();
        ActivityListVO vo = activity(50, 1, now.plusDays(2), now.plusDays(3), now.minusHours(6));

        ActivityListVO result = queryOne(vo);

        assertEquals("UPCOMING", result.getPhase());
    }

    @Test
    @DisplayName("活动不存在 → 抛出 ACTIVITY_NOT_FOUND 业务异常")
    void detailNotFoundThrowsBusinessException() {
        when(activityMapper.selectActivityDetail(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.getActivityDetail(999L));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("规则文本按行拆分，忽略空行，且不再对外返回原始 rules")
    void rulesAreSplitByLine() {
        ActivityDetailVO detail = new ActivityDetailVO();
        detail.setId(1L);
        detail.setStatus(ActivityStatus.PUBLISHED);
        detail.setCapacity(10);
        detail.setJoinedCount(0);
        detail.setStartTime(LocalDateTime.now().plusDays(2));
        detail.setEndTime(LocalDateTime.now().plusDays(3));
        detail.setSignupDeadline(LocalDateTime.now().plusDays(1));
        detail.setRules("规则一\n\n规则二 \n 规则三 ");
        when(activityMapper.selectActivityDetail(1L)).thenReturn(detail);

        ActivityDetailVO result = activityService.getActivityDetail(1L);

        assertEquals(List.of("规则一", "规则二", "规则三"), result.getRuleList());
        assertNull(result.getRules());
    }

    @Test
    @DisplayName("page/size 非法时收敛到安全值，并把 keyword 去空格后传下去")
    void pageParamsAreNormalized() {
        ActivityQuery query = new ActivityQuery();
        query.setPage(0);
        query.setSize(9999);
        query.setKeyword("  摄影  ");

        Page<ActivityListVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(activityMapper.selectActivityPage(any(), any(), any())).thenReturn(page);

        PageResult<ActivityListVO> result = activityService.pageActivities(query);

        // 归一化作用在传给 Mapper 的分页参数上，因此断言返回结果里的 page/size
        assertEquals(1, result.getPage());
        assertEquals(100, result.getSize());
        // keyword 在 Service 内被去空格后回写，供 SQL 直接使用
        assertEquals("摄影", query.getKeyword());
    }

    @Test
    @DisplayName("空关键词归一化为 null，避免生成 LIKE '%%' 拖慢查询")
    void blankKeywordBecomesNull() {
        ActivityQuery query = new ActivityQuery();
        query.setKeyword("   ");

        Page<ActivityListVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(activityMapper.selectActivityPage(any(), any(), any())).thenReturn(page);

        activityService.pageActivities(query);

        assertNull(query.getKeyword());
    }

    @Test
    @DisplayName("查询详情只访问一次 Mapper，不做额外全表扫描")
    void detailQueriesMapperOnce() {
        when(activityMapper.selectActivityDetail(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> activityService.getActivityDetail(999L));

        verify(activityMapper).selectActivityDetail(999L);
        verify(activityMapper, never()).selectList(any());
    }
}
