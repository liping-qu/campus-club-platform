package com.campus.club.club.controller;

import com.campus.club.auth.CurrentUserContext;
import com.campus.club.club.service.ClubService;
import com.campus.club.club.vo.ClubOptionVO;
import com.campus.club.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社团接口。
 * <p>
 * 现在只有一个只读接口：给「新建活动」的下拉框提供可管理的社团。
 * 社团的搜索/详情/加入申请仍在前端 mock，没有对应的后端接口 —— 这一点在 README 的完成度表里写明。
 */
@Tag(name = "社团", description = "社团管理相关")
@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @Operation(summary = "我能管理的社团", description = "平台管理员返回全部；社团负责人只返回自己负责的")
    @PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")
    @GetMapping("/mine")
    public ApiResponse<List<ClubOptionVO>> mine() {
        return ApiResponse.ok(clubService.listManageable(CurrentUserContext.requirePrincipal()));
    }
}
