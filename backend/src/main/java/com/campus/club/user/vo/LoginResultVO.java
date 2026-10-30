package com.campus.club.user.vo;

import lombok.Data;

/**
 * 登录结果。
 * <p>
 * {@code expiresIn} 给的是秒数而不是过期时间戳：前端按「收到响应时的时间 + 秒数」计算，
 * 不受客户端时钟与服务器时钟偏差影响。
 */
@Data
public class LoginResultVO {

    private String token;

    private String tokenType;

    /** 有效期，单位秒 */
    private long expiresIn;

    private UserVO user;

    public static LoginResultVO of(String token, long expiresIn, UserVO user) {
        LoginResultVO vo = new LoginResultVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(expiresIn);
        vo.setUser(user);
        return vo;
    }
}
