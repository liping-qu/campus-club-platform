package com.campus.club.club.service;

import com.campus.club.auth.AuthPrincipal;
import com.campus.club.club.vo.ClubOptionVO;

import java.util.List;

/**
 * 社团读能力。
 * <p>
 * 目前只回答一个问题：<b>当前这个人能管哪些社团</b>。
 * 新建活动必须带 clubId，而「选哪个社团」的唯一合法来源就是这里 ——
 * 前端不能自己拼一个 clubId，后端也不会信任它（{@code requireManageableClub} 会再查一次归属）。
 */
public interface ClubService {

    /**
     * 平台管理员返回全部正常社团；社团负责人只返回自己负责的。
     *
     * @return 按 id 升序，保证前端下拉框的顺序稳定
     */
    List<ClubOptionVO> listManageable(AuthPrincipal principal);
}
