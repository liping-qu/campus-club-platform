package com.campus.club.web;

import com.campus.club.auth.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 访问日志：每个请求一行，记录方法、路径、状态码、耗时、用户。
 *
 * <h3>记什么、不记什么</h3>
 * 只记「请求行级」的信息：方法、URI、状态码、耗时、用户 ID。
 * <b>不记请求体和响应体</b>：登录接口的请求体里有密码，报名接口的响应里可能带手机号，
 * 全量打印日志等于把敏感数据复制一份到日志系统里，风险远大于收益。
 * 需要看细节时按 traceId 去查业务日志。
 *
 * <h3>为什么慢请求要单独用 WARN</h3>
 * INFO 日志会被淹没，而「突然变慢」通常是最早的故障信号。
 * 超过阈值的请求单独打一条 WARN，日志平台按级别一筛就能看到，不用做复杂的耗时聚合。
 *
 * <h3>用户 ID 从哪里来</h3>
 * 认证发生在内层的 Security 过滤器链里，等这里拿到控制权时
 * {@code CurrentUserContext} 已经被清理了。所以认证过滤器把 userId 写在请求属性上
 * （{@link JwtAuthenticationFilter#USER_ID_ATTRIBUTE}），这里只读属性。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private final long slowRequestMillis;

    public AccessLogFilter(@Value("${app.log.slow-request-millis:500}") long slowRequestMillis) {
        this.slowRequestMillis = slowRequestMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            logRequest(request, response.getStatus(), costMillis);
        }
    }

    private void logRequest(HttpServletRequest request, int status, long costMillis) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        String user = userId == null ? "-" : userId.toString();

        if (costMillis >= slowRequestMillis) {
            // 慢请求带上查询串：排查「为什么慢」经常需要看到具体参数
            log.warn("slow request method={} uri={} query={} status={} cost={}ms user={}",
                    method, uri, request.getQueryString(), status, costMillis, user);
        } else {
            log.info("request method={} uri={} status={} cost={}ms user={}",
                    method, uri, status, costMillis, user);
        }
    }

    /**
     * 健康检查、接口文档、静态资源每几秒就来一次，记进来只会把有用的日志挤掉。
     * 要看这些请求可以临时调日志级别，不需要写进访问日志。
     */
    private boolean shouldLog(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return !uri.startsWith("/actuator")
                && !uri.startsWith("/v3/api-docs")
                && !uri.startsWith("/swagger-ui")
                && !uri.equals("/favicon.ico")
                && !"OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
