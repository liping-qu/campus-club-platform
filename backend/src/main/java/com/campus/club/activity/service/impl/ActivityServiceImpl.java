package com.campus.club.activity.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.ActivityService;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.cache.ActivityCache;
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
import java.util.Optional;

/**
 * 活动读模型服务。
 *
 * <h3>缓存只存「数据快照」，不存「结论」</h3>
 * {@code phase}、{@code phaseText}、{@code remaining} 都是拿当前时间算出来的推导值。
 * 如果把算好的结果一起缓存，一条 10 分钟 TTL 的活动详情会在报名截止之后
 * 仍然显示「报名中」—— 缓存把时间冻住了。
 * <p>
 * 所以这里的分工是：<b>缓存里放的是数据库原始快照（不含推导字段），每次读出来都按当前时间重算</b>。
 * 也就是说缓存的是一份「过时但无害」的数据，而不是一份「过时且错误」的结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    /** 抢不到回填锁时，自旋等别人回填的次数与间隔：最多 200ms，不能让用户为了一把锁干等 */
    private static final int WAIT_ROUNDS = 5;
    private static final long WAIT_INTERVAL_MILLIS = 40L;

    private final ActivityMapper activityMapper;
    private final ActivityCache activityCache;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ActivityListVO> pageActivities(ActivityQuery query) {
        // 归一化放在 Service 而不是 Controller：Controller 只做协议适配
        query.setKeyword(query.normalizedKeyword());
        query.setCategory(query.normalizedCategory());

        int pageNo = query.safePage();
        int pageSize = query.safeSize();
        ActivityPhase phase = query.getPhase() == null ? ActivityPhase.ALL : query.getPhase();

        // 指纹必须在归一化之后算：否则「摄影」和「 摄影 」会成为两个不同的缓存键
        String fingerprint = ActivityCache.fingerprint(query);

        LocalDateTime now = LocalDateTime.now();
        Optional<PageResult<ActivityListVO>> cached = activityCache.list(fingerprint);
        if (cached.isPresent()) {
            PageResult<ActivityListVO> hit = cached.get();
            // 命中缓存同样要重算推导字段，理由见类注释
            hit.getRecords().forEach(vo -> enrich(vo, now));
            return hit;
        }

        Page<ActivityListVO> pageParam = new Page<>(pageNo, pageSize);
        IPage<ActivityListVO> result = activityMapper.selectActivityPage(pageParam, query, phase.name());
        PageResult<ActivityListVO> page = PageResult.of(result.getRecords(), result.getTotal(), pageNo, pageSize);

        // 先落缓存（此时推导字段还是空的），再给返回对象填值。
        // 顺序不能反：反了就会把「此刻的报名中」一起写进缓存。
        activityCache.putList(fingerprint, page);

        // 展示阶段依赖「当前时间」，统一取一次 now，避免同一次响应里不同记录算出不同基准
        result.getRecords().forEach(vo -> enrich(vo, now));

        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailVO getActivityDetail(Long id) {
        ActivityDetailVO detail = loadDetail(id);
        // 无论命中缓存还是回源，推导字段一律按当前时间重算
        enrich(detail, LocalDateTime.now());
        return detail;
    }

    /**
     * 取详情：缓存 → 互斥回填 → 数据库。
     * <p>
     * 返回的对象一定是「数据库里的原始快照」，推导字段由调用方填。
     */
    private ActivityDetailVO loadDetail(Long id) {
        if (!activityCache.isEnabled()) {
            // 缓存被关掉或 Redis 处于故障静默期：不折腾锁，直接查库
            return loadFromDb(id);
        }

        ActivityCache.DetailLookup lookup = activityCache.detail(id);
        if (lookup.state() == ActivityCache.LookupState.HIT) {
            return lookup.value();
        }
        if (lookup.state() == ActivityCache.LookupState.NOT_FOUND) {
            // 空值标记命中：这个 id 确实不存在，不用再查库
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        boolean locked = activityCache.tryLock(id);
        try {
            if (locked) {
                // 双检：等锁的这一小段时间里，前一个持锁线程可能已经把数据填进去了
                ActivityCache.DetailLookup again = activityCache.detail(id);
                if (again.state() == ActivityCache.LookupState.HIT) {
                    return again.value();
                }
                if (again.state() == ActivityCache.LookupState.NOT_FOUND) {
                    throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
                }
                return loadFromDb(id);
            }

            // 没抢到锁：说明已经有线程在回源。先等它把结果写进缓存，等不到再自己查。
            ActivityDetailVO filled = waitForOtherLoader(id);
            if (filled != null) {
                return filled;
            }
            // 等不到也不能让用户继续等：自己查一次库（fail-open）
            log.debug("回填锁等待超时，改为直接查库 id={}", id);
            return loadFromDb(id);
        } finally {
            if (locked) {
                activityCache.unlock(id);
            }
        }
    }

    /** 自旋读缓存，最多等 {@code WAIT_ROUNDS * WAIT_INTERVAL_MILLIS} 毫秒 */
    private ActivityDetailVO waitForOtherLoader(Long id) {
        for (int i = 0; i < WAIT_ROUNDS; i++) {
            try {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                // 恢复中断标记后立刻放弃等待，交给调用方兜底
                Thread.currentThread().interrupt();
                return null;
            }
            ActivityCache.DetailLookup lookup = activityCache.detail(id);
            if (lookup.state() == ActivityCache.LookupState.HIT) {
                return lookup.value();
            }
            if (lookup.state() == ActivityCache.LookupState.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
            }
        }
        return null;
    }

    /**
     * 回源。查不到时写空值标记 —— 这是防穿透的关键一步：
     * 没有它，请求一个不存在的 id 每次都必然穿透到数据库。
     */
    private ActivityDetailVO loadFromDb(Long id) {
        ActivityDetailVO detail = activityMapper.selectActivityDetail(id);
        if (detail == null) {
            activityCache.putDetailNotFound(id);
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        detail.setRuleList(splitRules(detail.getRules()));
        detail.setRules(null);
        activityCache.putDetail(id, detail);
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
