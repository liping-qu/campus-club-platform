package com.campus.club.user.service;

import com.campus.club.user.dto.LoginRequest;
import com.campus.club.user.vo.LoginResultVO;
import com.campus.club.user.vo.UserVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /**
     * 校验用户名密码，通过后签发令牌。
     *
     * @throws com.campus.club.common.BusinessException 凭证错误（40101）或账号被禁用（40301）
     */
    LoginResultVO login(LoginRequest request);

    /**
     * 查询当前登录用户的最新资料。
     * <p>
     * 令牌里只带 ID/用户名/角色，昵称学院这些会变的信息每次从库里取，
     * 避免用户改了资料却要重新登录才生效。
     */
    UserVO currentUser();
}
