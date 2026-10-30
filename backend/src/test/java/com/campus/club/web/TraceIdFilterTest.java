package com.campus.club.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * traceId 生成与传递。
 * <p>
 * 这层看着只是「塞个随机串」，但两个细节错了会很难查：
 * 一是线程池复用导致 traceId 串到下个请求；二是直接信任请求头造成日志注入。
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("没带请求头时自动生成，并回写到响应头")
    void generatesTraceIdAndEchoesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        String generated = response.getHeader(TraceIdFilter.RESPONSE_HEADER);
        assertNotNull(generated);
        assertEquals(16, generated.length());
    }

    @Test
    @DisplayName("请求里带了合规的 traceId 就沿用，方便把链路串到网关或前端")
    void keepsClientProvidedTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        request.addHeader(TraceIdFilter.REQUEST_HEADER, "trace-abc-123456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals("trace-abc-123456", response.getHeader(TraceIdFilter.RESPONSE_HEADER));
    }

    @Test
    @DisplayName("带换行/超长/非法字符的 traceId 一律丢弃 —— 否则可以伪造日志行（日志注入）")
    void rejectsUnsafeTraceId() throws Exception {
        String injected = "abc12345\n2026-10-30 19:00:00 INFO 伪造的日志行";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        request.addHeader(TraceIdFilter.REQUEST_HEADER, injected);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        String actual = response.getHeader(TraceIdFilter.RESPONSE_HEADER);
        assertTrue(actual != null && !actual.contains("\n"), "换行必须被拒");
        assertEquals(16, actual.length());

        // 超长的也要拒：没有长度上限就能拿日志系统当存储用
        assertEquals(16, TraceIdFilter.resolveTraceId("a".repeat(200)).length());
    }

    @Test
    @DisplayName("请求处理期间日志上下文里能取到 traceId，结束后必须清掉")
    void exposesTraceIdInMdcOnlyDuringRequest() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> seen.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals(response.getHeader(TraceIdFilter.RESPONSE_HEADER), seen.get());
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), "线程池复用，不清理会把 traceId 带到下一个请求");
    }
}
