package com.campus.club.activity.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.ActivityService;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ActivityListVO> pageActivities(ActivityQuery query) {
        // 归一化放在 Service 而不是 Controller：Controller 只做协议适配
        query.setKeyword(query.normalizedKeyword());
        query.setCategory(query.normalizedCategory());

        int pageNo = query.safePage();
        int pageSize = query.safeSize();
        ActivityPhase phase = query.getPhase() == null ? ActivityPhase.ALL : query.getPhase();

        Page<ActivityListVO> pageParam = new Page<>(pageNo, pageSize);
        IPage<ActivityListVO> result = activityMapper.selectActivityPage(pageParam, query, phase.name());

        // 展示阶段依赖「当前时间」，统一取一次 now，避免同一次响应里不同记录算出不同基准
        LocalDateTime now = LocalDateTime.now();
        List<ActivityListVO> records = result.getRecords();
        records.forEach(vo -> enrich(vo, now));

        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailVO getActivityDetail(Long id) {
        ActivityDetailVO detail = activityMapper.selectActivityDetail(id);
        if (detail == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        enrich(detail, LocalDateTime.now());
        detail.setRuleList(splitRules(detail.getRules()));
        detail.setRules(null);
        return detail;
    }

    /**
     * 填充派生字段。这些值全部由已有字段推算，不接受外部输入。
     */
    private void enrich(ActivityListVO vo, LocalDateTime now) {
        int capacity = vo.getCapacity() == null ? 0 : vo.getCapacity();
        int joined = vo.getJoinedCount() == null ? 0 : vo.getJoinedCount();
        vo.setRemaining(Math.max(0, capacity - joined));

        // 阶段判定统一收敛在 ActivityPhase 上，「我的报名」列表复用同一套规则
        ActivityPhase phase = ActivityPhase.resolve(vo.getStatus(), capacity, joined,
                vo.getStartTime(), vo.getEndTime(), vo.getSignupDeadline(), now);
        vo.setPhase(phase.name());
        vo.setPhaseText(phase.getText());
    }

    private List<String> splitRules(String rules) {
        if (rules == null || rules.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rules.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
