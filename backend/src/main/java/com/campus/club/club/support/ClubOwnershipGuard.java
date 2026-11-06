package com.campus.club.club.support;

import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 「这个活动归不归我管」的唯一判断入口。
 *
 * <h3>为什么单独抽出来</h3>
 * 角色注解（{@code @PreAuthorize}）只能回答「是不是社团负责人」，
 * 回答不了「是不是<b>这个</b>社团的负责人」。后者要在 Service 层拿 {@code club.owner_user_id}
 * 和当前登录人比对 —— 报名名单、签到名单、代签、改活动、发布、删除，六个入口都要做这件事。
 * <p>
 * 抄六遍的后果不是「难看」，而是<b>漏一遍就是一个横向越权漏洞</b>，
 * 而且漏的那个接口平时没人点，测试也未必覆盖。所以这里只留一份实现。
 *
 * <h3>为什么先判存在再判归属</h3>
 * 活动不存在返回 404、存在但不归你管返回 403。顺序反了（先判权限）会让
 * 「有没有这个活动」这件事变得不可判断 —— 拿一个 id 挨个试，403 说明存在、404 说明不存在，
 * 等于白送一个探测接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubOwnershipGuard {

    private final ActivityMapper activityMapper;

    /**
     * 断言当前用户能管理这个活动所属的社团。
     *
     * @throws BusinessException 活动不存在 404；不属于当前用户 403
     */
    public void assertCanManage(Long activityId, AuthPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long ownerId = activityMapper.selectClubOwnerId(activityId);
        if (ownerId == null) {
            // selectClubOwnerId 查不到有两种可能：活动不存在，或社团没有负责人（账号被删、外键置空）。
            // 只有后者才是 403，前者必须如实报 404 —— 否则「有没有这个活动」就成了可探测的信息。
            if (activityMapper.selectById(activityId) == null) {
                throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
            }
            if (!principal.isPlatformAdmin()) {
                log.warn("社团暂无负责人，拒绝非平台管理员操作 activityId={} userId={}",
                        activityId, principal.userId());
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        if (principal.isPlatformAdmin()) {
            return;
        }
        if (!ownerId.equals(principal.userId())) {
            log.warn("越权操作他人社团数据 userId={} role={} activityId={} 社团负责人={}",
                    principal.userId(), principal.role(), activityId, ownerId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
