package com.campus.club.user.controller;

import com.campus.club.common.ApiResponse;
import com.campus.club.user.dto.LoginRequest;
import com.campus.club.user.service.AuthService;
import com.campus.club.user.vo.LoginResultVO;
import com.campus.club.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 * <p>
 * 刻意不做 {@code /api/auth/logout}：JWT 是无状态的，服务端没有可销毁的会话，
 * 写一个只返回 200 的「退出登录」会让人误以为令牌已经失效 —— 真正的退出是前端丢弃令牌。
 * 要支持「强制下线」，需要引入令牌黑名单，那是另一个量级的改造。
 */
@Tag(name = "认证", description = "登录与当前用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "成功返回 Bearer 令牌；密码错误 40101，账号禁用 40301")
    @PostMapping("/login")
    public ApiResponse<LoginResultVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "当前登录用户", description = "返回最新资料，令牌里的信息只用于鉴权")
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(authService.currentUser());
    }
}
