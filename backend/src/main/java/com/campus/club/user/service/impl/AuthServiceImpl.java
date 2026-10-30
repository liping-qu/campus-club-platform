package com.campus.club.user.service.impl;

import com.campus.club.auth.CurrentUserContext;
import com.campus.club.auth.JwtService;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.user.dto.LoginRequest;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import com.campus.club.user.service.AuthService;
import com.campus.club.user.vo.LoginResultVO;
import com.campus.club.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * 用户不存在时也要跑一次 BCrypt 校验，让两条失败分支的耗时接近。
     * <p>
     * 否则「用户不存在」立刻返回、「密码错误」要等 BCrypt 算 100ms 左右，
     * 攻击者用响应时间就能批量判断哪些用户名真实存在（账号枚举）。
     * 这个值本身无意义，只要是一段合法的 BCrypt 哈希即可。
     */
    private static final String DUMMY_HASH = "$2a$10$Ef85syL4SInZAlefEG3p7.E52EQNC1wSzD/k6MkNIaKTi9lbdaEmK";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional(readOnly = true)
    public LoginResultVO login(LoginRequest request) {
        String username = request.getUsername().trim();
        SysUser user = userMapper.selectByUsername(username);

        boolean matched = passwordEncoder.matches(
                request.getPassword(),
                user != null ? user.getPasswordHash() : DUMMY_HASH);

        // 用户不存在与密码错误返回同一个码：分开提示等于给攻击者一个「用户名是否存在」的探测器
        if (user == null || !matched) {
            log.info("登录失败 username={} 原因={}", username, user == null ? "用户不存在" : "密码错误");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        // 状态校验放在密码校验之后：先告诉对方「账号存在但被禁用」同样会泄漏账号存在性
        if (!user.isEnabled()) {
            log.info("登录失败 username={} 原因=账号已禁用", username);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtService.issue(user);
        log.info("登录成功 userId={} username={} role={}", user.getId(), user.getUsername(), user.getRole());
        return LoginResultVO.of(token, jwtService.ttlSeconds(), UserVO.from(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO currentUser() {
        Long userId = CurrentUserContext.require();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // 令牌还在有效期内，但账号已经没了：让前端重新登录
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return UserVO.from(user);
    }
}
