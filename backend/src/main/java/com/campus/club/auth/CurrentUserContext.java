package com.campus.club.auth;

import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;

/**
 * 当前请求的登录主体上下文。
 * <p>
 * 身份来源被刻意收敛到这一个 ThreadLocal：第三阶段由请求头 {@code X-User-Id} 写入，
 * 第四阶段改由 {@link JwtAuthenticationFilter} 解析 token 后写入 —— Service 层代码一行都没改。
 * <p>
 * 因此 <b>业务代码只能通过 {@link #require()} / {@link #requirePrincipal()} 取用户</b>，
 * 不要自己去读 header 或 token。
 * <p>
 * 用 ThreadLocal 的前提是「一个请求在一个线程上跑完」，Spring MVC 满足这一点；
 * 一旦引入异步或响应式编程，这个前提就不再成立，那时要换成显式传参或 Reactor Context。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    /** 未登录返回 null，用于「登录可选」的场景（例如详情页顺带返回是否已报名） */
    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    /**
     * 取出当前用户 ID，未登录直接抛 401。
     * <p>
     * 需要登录的接口统一走这里，避免每个方法自己判空、自己决定返回什么错误。
     */
    public static Long require() {
        return requirePrincipal().userId();
    }

    /** 需要角色判断时用它 */
    public static AuthPrincipal requirePrincipal() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /**
     * 请求结束必须清理，否则线程复用会把上一个请求的用户带给下一个请求 ——
     * 这是一种比「忘写鉴权」更隐蔽的越权漏洞。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
