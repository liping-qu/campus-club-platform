package com.campus.club.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 给每个请求打一个 traceId，写进日志上下文和响应头。
 *
 * <h3>为什么需要它</h3>
 * 一个请求会打出多条日志：进来、查到什么、写了什么、报了什么错。
 * 线上并发一高，这些日志交错在一起，没有 traceId 就拼不回一次请求的完整过程。
 * 有了它，`grep <traceId>` 就是这一次调用的全部证据，前端报障时把响应头里的
 * {@code X-Trace-Id} 贴过来就能直接定位。
 *
 * <h3>为什么放在最外层（HIGHEST_PRECEDENCE）</h3>
 * 它必须比 Spring Security 的过滤器链（order = -100）更早。
 * 否则 401 / 403 这些由 Security 直接返回的响应里不会有 traceId，
 * 而「权限问题查不到日志」恰恰是最需要日志的场景。
 *
 * <h3>为什么不能直接信任请求头</h3>
 * 允许调用方传 {@code X-Request-Id} 方便把链路串到网关或前端，但这个值会写进日志。
 * 如果原样接受，`X-Request-Id: xxx\n2026-01-01 INFO 伪造的日志行` 就能伪造日志（日志注入）。
 * 所以只接受 {@code [A-Za-z0-9_-]{8,64}}，不合规的一律重新生成。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 日志里引用的键名，logback 用 %X{traceId} 取 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    /** 只允许字母数字下划线中划线，且长度 8~64：防日志注入，也顺便限制了垃圾值 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(REQUEST_HEADER));
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用线程，不清理会把 traceId 带到下一个无关请求上
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    static String resolveTraceId(String fromHeader) {
        if (fromHeader != null && SAFE_TRACE_ID.matcher(fromHeader).matches()) {
            return fromHeader;
        }
        return generate();
    }

    static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
