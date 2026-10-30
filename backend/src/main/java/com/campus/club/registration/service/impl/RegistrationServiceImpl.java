package com.campus.club.registration.service.impl;

import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityPhase;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.cache.ActivityCache;
import com.campus.club.common.AfterCommit;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.registration.entity.ActivityRegistration;
import com.campus.club.registration.entity.RegistrationStatus;
import com.campus.club.registration.mapper.ActivityRegistrationMapper;
import com.campus.club.registration.service.RegistrationService;
import com.campus.club.registration.vo.MyRegistrationVO;
import com.campus.club.registration.vo.RegistrationAttendeeVO;
import com.campus.club.registration.vo.RegistrationItemVO;
import com.campus.club.registration.vo.RegistrationResultVO;
import com.campus.club.notification.event.RegistrationSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名服务实现 —— 并发安全的核心。
 *
 * <h3>为什么不用「先查再改」</h3>
 * 直觉写法是：
 * <pre>
 *   Activity a = mapper.selectById(id);
 *   if (a.getJoinedCount() &lt; a.getCapacity()) { mapper.updateJoinedCount(id, a.getJoinedCount() + 1); }
 * </pre>
 * 两个线程同时读到 {@code joined_count = capacity - 1}，都会判断「还有名额」，
 * 于是各自把计数写成 {@code capacity}，最终两人都报名成功但只占到一个名额 —— 超卖。
 * {@code synchronized} 只能锁住单机单 JVM，多实例部署立刻失效；
 * 加分布式锁能解决，但为了一个自增引入 Redis/ZooKeeper 依赖，成本和故障面都不划算。
 *
 * <h3>这里的三层防线</h3>
 * <ol>
 *   <li><b>条件更新 + 判断受影响行数</b>（{@code UPDATE ... WHERE joined_count &lt; capacity}）：
 *       「判断」和「修改」压进同一条 SQL，由 InnoDB 行锁保证同一条记录上的并发更新串行执行。
 *       返回 0 就代表没抢到，调用方必须据此失败，不能默认成功。</li>
 *   <li><b>唯一约束</b> {@code uk_registration_activity_user}：即使业务代码判断出错，
 *       数据库也不允许同一用户在同一活动上出现第二条记录。</li>
 *   <li><b>CHECK 约束</b> {@code joined_count BETWEEN 0 AND capacity}：兜底，
 *       任何绕过前两层把计数改坏的路径都会直接被数据库拒绝。</li>
 * </ol>
 *
 * <h3>加锁顺序</h3>
 * 报名和取消都必须<b>先锁 activity 行，再操作 activity_registration 行</b>。
 * 如果取消反过来先锁报名行，两个方向的并发请求就会互相等待对方持有的锁，形成死锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final ActivityCache activityCache;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationResultVO register(Long activityId) {
        Long userId = CurrentUserContext.require();

        // ---- 第 0 步：廉价前置判断 ----
        // 这一步不承担并发安全职责，只是把「必然失败」的请求挡在抢行锁之前，
        // 减少无谓的锁竞争。真正的裁决在第 1 步的条件更新上。
        Activity snapshot = activityMapper.selectForRegistration(activityId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!isPublished(snapshot)) {
            throw new BusinessException(ErrorCode.ACTIVITY_CLOSED);
        }
        ActivityRegistration existing = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (existing != null && RegistrationStatus.isRegistered(existing.getStatus())) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        // ---- 第 1 步：原子占位（并发安全的唯一依据）----
        int occupied = activityMapper.occupySlot(activityId);
        if (occupied == 0) {
            // 抢不到必须失败。看到 0 还继续往下写，就是超卖的起点。
            throw explainOccupyFailure(activityId);
        }

        // ---- 第 2 步：落报名记录 ----
        Long registrationId;
        if (existing != null) {
            // 之前取消过，复用同一行：把 status 从 2 翻回 1。
            // WHERE 里带 status = 2，并发下只有一个线程能翻转成功。
            if (registrationMapper.reviveRegistration(activityId, userId) == 0) {
                // 同一用户的另一个请求刚刚先一步把它改回已报名。
                // 抛异常让事务回滚，第 1 步加的 1 会一起撤销。
                throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
            }
            registrationId = existing.getId();
        } else {
            ActivityRegistration record = new ActivityRegistration();
            record.setActivityId(activityId);
            record.setUserId(userId);
            record.setStatus(RegistrationStatus.REGISTERED);
            record.setRegisteredAt(LocalDateTime.now());
            try {
                registrationMapper.insert(record);
            } catch (DuplicateKeyException ex) {
                // 两个并发请求都判断「没有记录」，都会去 insert。
                // 唯一键让第二个必然失败，这里转成业务异常回滚整个事务：
                // 占位加的那 1 一并撤销，不会出现「计数涨了但人没报名」。
                log.debug("唯一键拦截了重复报名 activityId={} userId={}", activityId, userId);
                throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
            }
            registrationId = record.getId();
        }

        // ---- 第 3 步：回读真实计数 ----
        // 事务内自增过的值用普通 SELECT 读不到（快照读），必须用 FOR UPDATE 回读。
        // 此时行锁已在本事务手上，这次回读不会产生额外等待。
        Activity latest = activityMapper.selectForRegistrationLatest(activityId);
        int joined = latest == null ? 0 : latest.getJoinedCount();
        int capacity = latest == null || latest.getCapacity() == null ? 0 : latest.getCapacity();

        log.info("报名成功 activityId={} userId={} joined={}/{}", activityId, userId, joined, capacity);

        // 报名改变了 joined_count，缓存里的详情和列表都过期了。
        // 必须等事务提交后再失效：提前删会让并发读把未提交前的旧值重新写回缓存。
        AfterCommit.run(() -> activityCache.evict(activityId));

        // 通知「登记」在事务内，真正投递在提交之后（见 RegistrationNotificationListener）。
        // 事件里带的是事务内读到的快照，异步线程不再回查数据库 —— 否则用户收到的
        // 可能是活动被改标题之后的内容，和他当初报的那个活动对不上。
        if (latest != null) {
            eventPublisher.publishEvent(new RegistrationSucceededEvent(
                    activityId, userId, latest.getTitle(), latest.getLocation(), latest.getStartTime(),
                    Math.max(0, capacity - joined)));
        }

        RegistrationResultVO result = new RegistrationResultVO();
        result.setActivityId(activityId);
        result.setRegistrationId(registrationId);
        result.setJoinedCount(joined);
        result.setRemaining(Math.max(0, capacity - joined));
        result.setRegisteredAt(LocalDateTime.now());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long activityId) {
        Long userId = CurrentUserContext.require();

        // 前置判断：没报名就别去动 activity 行，避免无意义的写与锁。
        ActivityRegistration existing = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (existing == null || !RegistrationStatus.isRegistered(existing.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_REGISTERED);
        }

        // 加锁顺序：先 activity 再 registration，与报名保持一致。
        int released = activityMapper.releaseSlot(activityId);
        int cancelled = registrationMapper.cancelRegistration(activityId, userId);
        if (cancelled == 0) {
            // 并发取消：另一个请求已经把它改成已取消。
            // 抛异常回滚，把上面刚减掉的计数还原，避免「取消了两次、名额涨了两次」。
            throw new BusinessException(ErrorCode.NOT_REGISTERED);
        }
        if (released == 0) {
            // 活动记录已不存在，或计数已经是 0：属于数据异常，需要人工关注但不阻断用户。
            log.warn("取消报名时未能释放名额 activityId={} userId={}", activityId, userId);
        }
        log.info("取消报名 activityId={} userId={}", activityId, userId);

        // 与报名同理：名额释放后要让缓存失效，且必须推迟到提交之后
        AfterCommit.run(() -> activityCache.evict(activityId));

        // 取消报名刻意不发通知：这是用户自己点的操作，页面当场就有反馈，
        // 再推一条「你已取消」只是噪音。值得通知的是「用户没预期到的变化」——
        // 被移出活动、名额变动、活动取消，这些才应该走通知。
    }

    @Override
    @Transactional(readOnly = true)
    public MyRegistrationVO getMyRegistration(Long activityId) {
        Long userId = CurrentUserContext.require();
        ActivityRegistration record = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (record == null) {
            return MyRegistrationVO.none();
        }
        return MyRegistrationVO.of(record.getStatus(), record.getRegisteredAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationItemVO> listMyRegistrations() {
        Long userId = CurrentUserContext.require();
        List<RegistrationItemVO> items = registrationMapper.selectMyRegistrations(userId);

        // 阶段与剩余名额是推导值，规则与活动列表完全一致（同一个 ActivityPhase.resolve）
        LocalDateTime now = LocalDateTime.now();
        for (RegistrationItemVO item : items) {
            int capacity = item.getCapacity() == null ? 0 : item.getCapacity();
            int joined = item.getJoinedCount() == null ? 0 : item.getJoinedCount();
            item.setRemaining(Math.max(0, capacity - joined));
            ActivityPhase phase = ActivityPhase.resolve(item.getStatus(), capacity, joined,
                    item.getStartTime(), item.getEndTime(), item.getSignupDeadline(), now);
            item.setPhase(phase.name());
            item.setPhaseText(phase.getText());
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationAttendeeVO> listAttendees(Long activityId) {
        AuthPrincipal principal = CurrentUserContext.requirePrincipal();

        // 角色层已由 @PreAuthorize 卡住，这里只做数据层：是不是「这个」社团的负责人
        Long ownerId = activityMapper.selectClubOwnerId(activityId);
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!principal.isPlatformAdmin() && !ownerId.equals(principal.userId())) {
            log.warn("越权访问报名名单 userId={} role={} activityId={} 所属社团负责人={}",
                    principal.userId(), principal.role(), activityId, ownerId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return registrationMapper.selectAttendees(activityId);
    }

    /**
     * 条件更新返回 0 之后，重新读一次最新状态来给出准确原因。
     * <p>
     * 必须用 {@code FOR UPDATE} 版本：在 REPEATABLE READ 下普通 SELECT 读的是事务
     * 开始时的快照，拿旧数据解释失败原因，很容易把「名额已满」和「报名已截止」说反。
     * 只有在失败路径上才付这一次加锁读的代价。
     */
    private BusinessException explainOccupyFailure(Long activityId) {
        Activity latest = activityMapper.selectForRegistrationLatest(activityId);
        if (latest == null) {
            return new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!isPublished(latest)) {
            return new BusinessException(ErrorCode.ACTIVITY_CLOSED);
        }
        LocalDateTime deadline = latest.getSignupDeadline();
        if (deadline != null && !deadline.isAfter(LocalDateTime.now())) {
            return new BusinessException(ErrorCode.SIGNUP_CLOSED);
        }
        int capacity = latest.getCapacity() == null ? 0 : latest.getCapacity();
        int joined = latest.getJoinedCount() == null ? 0 : latest.getJoinedCount();
        if (joined >= capacity) {
            return new BusinessException(ErrorCode.ACTIVITY_FULL);
        }
        // 三个条件现在都满足却仍然更新失败：说明「判断」和「更新」之间状态被改了。
        // 这是可重试的竞态，不能伪装成「名额已满」误导用户。
        return new BusinessException(ErrorCode.REGISTER_CONFLICT);
    }

    private boolean isPublished(Activity activity) {
        return activity.getStatus() != null && activity.getStatus() == ActivityStatus.PUBLISHED;
    }
}
