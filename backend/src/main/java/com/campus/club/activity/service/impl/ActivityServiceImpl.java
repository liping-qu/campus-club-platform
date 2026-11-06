package com.campus.club.activity.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityCreateRequest;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.dto.ActivitySaveRequest;
import com.campus.club.activity.dto.ActivityUpdateRequest;
import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.ActivityService;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.cache.ActivityCache;
import com.campus.club.club.entity.Club;
import com.campus.club.club.mapper.ClubMapper;
import com.campus.club.common.AfterCommit;
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

    /** 管理端列表单页上限：运营扫列表的场景，不需要一次拉几百条 */
    private static final int MAX_MANAGE_PAGE_SIZE = 50;

    private final ActivityMapper activityMapper;
    private final ActivityCache activityCache;
    private final ClubMapper clubMapper;

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

    // ==================================================================
    // 写模型
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResult<ActivityDetailVO> pageManageable(int pageNo, int pageSize, Integer status) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_MANAGE_PAGE_SIZE);

        // 平台管理员看全部（ownerId = null），社团负责人只看自己社团的。
        // 这个 ownerId 直接进 SQL 的 WHERE，而不是查出来再在 Java 里过滤：
        // 后者一旦漏写那一行 if，就是把别人社团的草稿列表整个吐出去。
        Long ownerId = principal.isPlatformAdmin() ? null : principal.userId();
        IPage<ActivityDetailVO> result = activityMapper.selectManagePage(
                new Page<>(safePage, safeSize), ownerId, status);

        LocalDateTime now = LocalDateTime.now();
        for (ActivityDetailVO vo : result.getRecords()) {
            vo.setRuleList(splitRules(vo.getRules()));
            vo.setRules(null);
            enrich(vo, now);
        }
        return PageResult.of(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailVO getManageableDetail(Long id) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();

        // 不用 activityCache：缓存里那份是对外的详情快照（只含已发布）。
        // 管理端刚改完活动就要立刻看到新值，而「缓存里没有草稿」本身也不是 bug 而是设计。
        ActivityDetailVO detail = activityMapper.selectManageDetail(id);
        if (detail == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        // 先存在再归属：顺序反了，拿 id 挨个试就能靠 403/404 反推出哪些活动存在
        requireManageableClub(detail.getClubId(), principal);

        detail.setRuleList(splitRules(detail.getRules()));
        detail.setRules(null);
        enrich(detail, LocalDateTime.now());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityDetailVO createActivity(ActivityCreateRequest request) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        Club club = requireManageableClub(request.getClubId(), principal);
        validateTimeline(request, true);

        Activity entity = new Activity();
        entity.setClubId(club.getId());
        applyEditable(entity, request);
        // 这两个字段不接受外部输入：计数只能由报名事务维护，状态只能由状态机改
        entity.setJoinedCount(0);
        entity.setStatus(ActivityStatus.DRAFT);
        entity.setCreatedBy(principal.userId());
        // 显式给创建时间：否则 insert 后实体里仍是 null（默认值只在库里生效），
        // 返回给前端的「创建时间」会是个空值，列表里立刻刷新就能看出对不上。
        entity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(entity);

        // 新建的活动本身还没有详情缓存，但对外列表的缓存快照里少了它。
        // 不推进列表版本的话，运营会看到「创建成功但列表里没有」，直到列表 TTL 到期。
        AfterCommit.run(activityCache::evictList);

        log.info("活动已创建 activityId={} clubId={} operator={}",
                entity.getId(), club.getId(), principal.userId());
        return toDetail(entity, club.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityDetailVO updateActivity(Long id, ActivityUpdateRequest request) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        Activity existing = requireActivity(id);
        Club club = requireManageableClub(existing.getClubId(), principal);
        requireEditable(existing, "修改");
        validateTimeline(request, false);

        int joined = existing.getJoinedCount() == null ? 0 : existing.getJoinedCount();
        if (request.getCapacity() < joined) {
            throw new BusinessException(ErrorCode.CAPACITY_BELOW_JOINED,
                    "名额不能小于已报名人数（当前已报名 " + joined + " 人）");
        }

        Activity patch = new Activity();
        patch.setId(id);
        applyEditable(patch, request);
        if (activityMapper.updateEditable(patch) == 0) {
            // 条件更新没命中：要么状态在这几毫秒里变了，要么刚刚有人报名把名额顶满了。
            // 重新读一次把真实原因说清楚，而不是笼统报「修改失败」。
            throw explainUpdateFailure(id, request.getCapacity());
        }

        // 详情与列表内容都变了，必须失效；且要等提交之后（提前删会让并发读把旧值写回缓存）
        AfterCommit.run(() -> activityCache.evict(id));

        // 同步内存对象，供返回最新值（不回查数据库，省一次往返）
        applyEditable(existing, request);
        log.info("活动已修改 activityId={} operator={}", id, principal.userId());
        return toDetail(existing, club.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityDetailVO publishActivity(Long id) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        Activity existing = requireActivity(id);
        Club club = requireManageableClub(existing.getClubId(), principal);

        int status = existing.getStatus() == null ? -1 : existing.getStatus();
        if (status != ActivityStatus.DRAFT && status != ActivityStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT,
                    "只有草稿或已下架的活动可以发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing.getSignupDeadline() == null || !existing.getSignupDeadline().isAfter(now)) {
            throw new BusinessException(ErrorCode.SIGNUP_CLOSED,
                    "报名截止时间已过，请先调整报名时间再发布");
        }
        if (existing.getEndTime() != null && !existing.getEndTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE,
                    "活动结束时间已过，请先调整活动时间再发布");
        }
        changeStatus(id, status, ActivityStatus.PUBLISHED);
        existing.setStatus(ActivityStatus.PUBLISHED);

        // 发布前这个 id 大概率被缓存成了「不存在」（详情查询只认已发布），
        // 不失效的话发布后最长 60 秒仍然 404。
        AfterCommit.run(() -> activityCache.evict(id));
        log.info("活动已发布 activityId={} operator={}", id, principal.userId());
        return toDetail(existing, club.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityDetailVO unpublishActivity(Long id) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        Activity existing = requireActivity(id);
        Club club = requireManageableClub(existing.getClubId(), principal);

        int status = existing.getStatus() == null ? -1 : existing.getStatus();
        if (status != ActivityStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT, "只有已发布的活动可以下架");
        }
        changeStatus(id, status, ActivityStatus.CANCELLED);
        existing.setStatus(ActivityStatus.CANCELLED);

        AfterCommit.run(() -> activityCache.evict(id));
        log.info("活动已下架 activityId={} operator={}", id, principal.userId());
        return toDetail(existing, club.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long id) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();
        Activity existing = requireActivity(id);
        requireManageableClub(existing.getClubId(), principal);

        int status = existing.getStatus() == null ? -1 : existing.getStatus();
        if (status != ActivityStatus.DRAFT) {
            // 已发布的活动上有报名记录，而 activity_registration 是 ON DELETE CASCADE：
            // 允许物理删除就等于允许一次性抹掉用户的历史报名。已发布的只能下架。
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT,
                    "只能删除草稿；已发布的活动请先下架");
        }
        if (activityMapper.deleteDraft(id) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT,
                    "活动状态刚刚变化，请刷新后重试");
        }
        AfterCommit.run(() -> activityCache.evict(id));
        log.info("活动已删除 activityId={} operator={}", id, principal.userId());
    }

    // ------------------------------------------------------------------
    // 写模型内部工具
    // ------------------------------------------------------------------

    /** 读活动；不存在直接 404（权限检查在外层，避免「有权限但查不到」被误报成 403） */
    private Activity requireActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    /**
     * 这个社团归不归当前用户管。
     * <p>
     * 角色是 CLUB_ADMIN 只说明「是某个社团的负责人」，还要确认正好是这个社团 ——
     * 否则就是横向越权：能改别人社团的活动、能看别人社团的草稿。
     */
    private Club requireManageableClub(Long clubId, AuthPrincipal principal) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BusinessException(ErrorCode.CLUB_NOT_FOUND);
        }
        if (principal.isPlatformAdmin()) {
            return club;
        }
        if (club.getOwnerUserId() == null || !club.getOwnerUserId().equals(principal.userId())) {
            log.warn("越权操作社团活动 userId={} role={} clubId={} 社团负责人={}",
                    principal.userId(), principal.role(), clubId, club.getOwnerUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return club;
    }

    /** 只有草稿和已发布可以改：已下架/已结束的活动改了对谁都没有意义，还会让报名人困惑 */
    private void requireEditable(Activity activity, String action) {
        Integer status = activity.getStatus();
        if (status == null || (status != ActivityStatus.DRAFT && status != ActivityStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT,
                    "已下架或已结束的活动不能" + action);
        }
    }

    /**
     * 时间窗口校验。
     * <p>
     * 三条规则与表上的 CHECK 约束一一对应，但在这里先拦一次：
     * 让数据库抛约束异常的话，用户看到的只会是「系统繁忙」，
     * 而他其实只是把结束时间填早了。
     *
     * @param requireFutureStart 新建时必须为 true；修改已有的已发布活动时允许开始时间在过去
     */
    private void validateTimeline(ActivitySaveRequest request, boolean requireFutureStart) {
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        LocalDateTime deadline = request.getSignupDeadline();

        if (!end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (deadline.isAfter(start)) {
            throw new BusinessException(ErrorCode.SIGNUP_DEADLINE_AFTER_START);
        }
        if (requireFutureStart && !start.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "开始时间必须晚于当前时间");
        }
    }

    private void applyEditable(Activity target, ActivitySaveRequest request) {
        target.setTitle(trim(request.getTitle()));
        target.setDescription(request.getDescription());
        target.setLocation(trim(request.getLocation()));
        target.setCategory(trim(request.getCategory()));
        target.setStartTime(request.getStartTime());
        target.setEndTime(request.getEndTime());
        target.setSignupDeadline(request.getSignupDeadline());
        target.setCapacity(request.getCapacity());
        target.setCoverColor(request.getCoverColor());
        target.setRules(request.getRules());
    }

    private void changeStatus(Long id, int expectedStatus, int newStatus) {
        if (activityMapper.changeStatus(id, expectedStatus, newStatus) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT,
                    "活动状态刚刚变化，请刷新后重试");
        }
    }

    /** 条件更新返回 0 之后重新读一次，给出准确原因 */
    private BusinessException explainUpdateFailure(Long id, Integer requestedCapacity) {
        Activity latest = activityMapper.selectById(id);
        if (latest == null) {
            return new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        Integer status = latest.getStatus();
        if (status == null || (status != ActivityStatus.DRAFT && status != ActivityStatus.PUBLISHED)) {
            return new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT, "活动已被下架或结束，无法修改");
        }
        int joined = latest.getJoinedCount() == null ? 0 : latest.getJoinedCount();
        if (requestedCapacity != null && requestedCapacity < joined) {
            return new BusinessException(ErrorCode.CAPACITY_BELOW_JOINED,
                    "名额不能小于已报名人数（当前已报名 " + joined + " 人）");
        }
        return new BusinessException(ErrorCode.ACTIVITY_STATUS_CONFLICT, "活动状态刚刚变化，请刷新后重试");
    }

    /** 把实体转成返回给调用方的详情（不回查数据库，用事务内已有的数据拼） */
    private ActivityDetailVO toDetail(Activity activity, String clubName) {
        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setClubId(activity.getClubId());
        vo.setClubName(clubName);
        vo.setLocation(activity.getLocation());
        vo.setCategory(activity.getCategory());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setSignupDeadline(activity.getSignupDeadline());
        vo.setCapacity(activity.getCapacity());
        vo.setJoinedCount(activity.getJoinedCount());
        vo.setStatus(activity.getStatus());
        vo.setCoverColor(activity.getCoverColor());
        vo.setDescription(activity.getDescription());
        vo.setCreatedBy(activity.getCreatedBy());
        vo.setCreatedAt(activity.getCreatedAt());
        vo.setRuleList(splitRules(activity.getRules()));
        vo.setRules(null);
        enrich(vo, LocalDateTime.now());
        return vo;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
