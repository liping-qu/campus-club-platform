package com.campus.club.auth;

/**
 * 当前请求的登录主体。
 * <p>
 * 由 JWT 过滤器解析 token 后写入 {@link CurrentUserContext}，
 * 是「谁在调用」的唯一来源 —— 任何接口都不允许从请求参数里取用户 ID。
 *
 * @param userId   用户主键
 * @param username 登录名（便于日志排查，不参与鉴权判断）
 * @param role     角色，取值见 {@link com.campus.club.user.entity.UserRole}
 */
public record AuthPrincipal(Long userId, String username, String role) {

    public boolean isPlatformAdmin() {
        return com.campus.club.user.entity.UserRole.PLATFORM_ADMIN.equals(role);
    }

    public boolean isClubAdmin() {
        return com.campus.club.user.entity.UserRole.CLUB_ADMIN.equals(role);
    }
}
