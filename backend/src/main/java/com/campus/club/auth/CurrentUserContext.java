package com.campus.club.auth;

import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;

/**
 * 当前请求的登录用户上下文。
 * <p>
 * 身份来源被刻意收敛到这一个 ThreadLocal：第三阶段由请求头 {@code X-User-Id} 写入，
 * 第四阶段接入 JWT 后改由过滤器从 token 解析后写入，Service 层代码一行都不用改。
 * <p>
 * 因此 <b>业务代码只能通过 {@link #require()} 取用户</b>，不要自己去读 header 或 token。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    /**
     * 取出当前用户，未登录直接抛 401。
     * <p>
     * 需要登录的接口统一走这里，避免每个方法自己判空、自己决定返回什么错误。
     */
    public static Long require() {
        Long userId = HOLDER.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 请求结束必须清理，否则线程复用会把上一个请求的用户带给下一个请求。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
