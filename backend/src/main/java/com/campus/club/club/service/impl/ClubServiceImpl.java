package com.campus.club.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.club.entity.Club;
import com.campus.club.club.mapper.ClubMapper;
import com.campus.club.club.service.ClubService;
import com.campus.club.club.vo.ClubOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 社团读服务。
 * <p>
 * 权限判断用的是「角色 + owner_id」这一组事实，不查任何中间表：
 * 社团负责人是 {@code club.owner_user_id} 直接指过去的，没有第二处定义。
 */
@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    /** 1 正常，0 已解散：已解散的社团不该出现在「新建活动」的下拉框里 */
    private static final int STATUS_ACTIVE = 1;

    private final ClubMapper clubMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClubOptionVO> listManageable(AuthPrincipal principal) {
        LambdaQueryWrapper<Club> query = new LambdaQueryWrapper<Club>()
                .eq(Club::getStatus, STATUS_ACTIVE)
                .orderByAsc(Club::getId);

        // 平台管理员看全部；社团负责人只看自己的。
        // 注意这里不拦「普通学生」—— 权限在 @PreAuthorize 上，能走到 service 的都已经有角色，
        // 多写一个分支只会让两个地方都要维护同一条规则。
        if (!principal.isPlatformAdmin()) {
            query.eq(Club::getOwnerUserId, principal.userId());
        }

        return clubMapper.selectList(query).stream().map(ClubServiceImpl::toOption).toList();
    }

    private static ClubOptionVO toOption(Club club) {
        ClubOptionVO option = new ClubOptionVO();
        option.setId(club.getId());
        option.setName(club.getName());
        return option;
    }
}
