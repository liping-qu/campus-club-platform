package com.campus.club.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 访问日志的内容与级别。
 * <p>
 * 这里不去断言「日志能被打印出来」这种没法验证的事，而是把 Logback 的 appender 挂到
 * 过滤器自己的 logger 上，直接看它到底写了什么、用什么级别写的。
 * 慢请求的用例不需要真的 sleep：把阈值设成 0，任何请求都算慢。
 */
class AccessLogFilterTest {

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private void run(AccessLogFilter filter, MockHttpServletRequest request, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
        filter.doFilter(request, response, chain);
    }

    @Test
    @DisplayName("普通请求打一条 INFO，包含方法、路径、状态码、耗时")
    void logsNormalRequestAsInfo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");

        run(new AccessLogFilter(500), request, 200);

        assertEquals(1, events().size());
        ILoggingEvent event = events().get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("method=GET"), message);
        assertTrue(message.contains("uri=/api/activities"), message);
        assertTrue(message.contains("status=200"), message);
        assertTrue(message.contains("cost="), message);
    }

    @Test
    @DisplayName("超过阈值降级为 WARN，并带上查询串方便排查")
    void logsSlowRequestAsWarn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        request.setQueryString("page=1&size=50");

        // 阈值 0：任何请求都算慢，不用真的 sleep 让测试变慢
        run(new AccessLogFilter(0), request, 200);

        assertEquals(1, events().size());
        assertEquals(Level.WARN, events().get(0).getLevel());
        assertTrue(events().get(0).getFormattedMessage().contains("query=page=1&size=50"));
    }

    @Test
    @DisplayName("请求里有用户时记录 userId，没有时记 -")
    void logsUserIdWhenAuthenticated() throws Exception {
        MockHttpServletRequest withUser = new MockHttpServletRequest("POST", "/api/activities/1/registrations");
        withUser.setAttribute("campus.auth.userId", 6L);
        run(new AccessLogFilter(500), withUser, 200);
        assertTrue(events().get(0).getFormattedMessage().contains("user=6"));

        appender.list.clear();

        MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/api/activities");
        run(new AccessLogFilter(500), anonymous, 200);
        assertTrue(events().get(0).getFormattedMessage().contains("user=-"));
    }

    @Test
    @DisplayName("健康检查与文档请求不记访问日志，避免把有用的日志挤掉")
    void skipsActuatorAndDocs() throws Exception {
        run(new AccessLogFilter(500), new MockHttpServletRequest("GET", "/actuator/health"), 200);
        run(new AccessLogFilter(500), new MockHttpServletRequest("GET", "/v3/api-docs"), 200);
        run(new AccessLogFilter(500), new MockHttpServletRequest("GET", "/swagger-ui/index.html"), 200);
        run(new AccessLogFilter(500), new MockHttpServletRequest("OPTIONS", "/api/activities"), 200);

        assertTrue(events().isEmpty(), "这些请求不该产生访问日志");
    }

    @Test
    @DisplayName("日志里不出现请求体和响应体 —— 登录密码不能进日志")
    void neverLogsBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContent("{\"username\":\"admin\",\"password\":\"123456\"}".getBytes());

        run(new AccessLogFilter(500), request, 200);

        assertFalse(events().get(0).getFormattedMessage().contains("123456"));
    }
}
