package com.campus.club.auth;

import com.campus.club.common.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 职责只有三件：从 {@code Authorization: Bearer xxx} 取令牌、验签解析、把身份写进
 * SecurityContext 与 {@link CurrentUserContext}。
 * <p>
 * <b>不在这里直接返回 401</b>：非法令牌只记录错误码，交给
 * {@code AuthenticationEntryPoint} 统一输出响应体。否则「过滤器写的 401」和
 * 「异常处理器写的 401」会是两套格式，前端得写两套判断。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因放在请求属性上，供入口点读取 */
    public static final String AUTH_ERROR_ATTRIBUTE = "campus.auth.error";

    /**
     * 认证成功的用户 ID 放在请求属性上。
     * <p>
     * 为什么不直接读 {@link CurrentUserContext}：它在 {@code finally} 里就被清掉了，
     * 而外层的访问日志过滤器是在整条链返回之后才写日志的，那时已经读不到。
     * 请求属性跟着这次请求的生命周期走，正好合适。
     */
    public static final String USER_ID_ATTRIBUTE = "campus.auth.userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                authenticate(request, header.substring(BEARER_PREFIX.length()).trim());
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用，必须清理。放在 finally 里保证异常路径也会执行。
            CurrentUserContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request, String token) {
        if (token.isEmpty()) {
            return;
        }
        try {
            AuthPrincipal principal = jwtService.parse(token);
            CurrentUserContext.set(principal);
            // 供外层访问日志读取（见 USER_ID_ATTRIBUTE 的注释）
            request.setAttribute(USER_ID_ATTRIBUTE, principal.userId());

            // 角色转成 Spring Security 的 ROLE_ 前缀权限，方法级 @PreAuthorize 才能用 hasRole
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BusinessException ex) {
            // 令牌有问题：保持匿名，把具体原因留给入口点
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex.getErrorCode());
        }
    }
}
