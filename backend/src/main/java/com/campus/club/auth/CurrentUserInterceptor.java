package com.campus.club.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 过渡期的身份解析器：从 {@code X-User-Id} 请求头取用户 ID。
 * <p>
 * 第三阶段还没有登录接口，先用请求头模拟「已登录」，
 * 让报名链路可以在真实用户维度上被测试（唯一约束、并发都依赖 user_id）。
 * <p>
 * <b>第四阶段会用 JWT 过滤器替换本类</b>：token 校验、过期判断、签名验证都在过滤器里完成，
 * 校验通过后同样调用 {@link CurrentUserContext#set(Long)}，下游无感知。
 */
@Slf4j
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String raw = request.getHeader(USER_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            // 不在这里拒绝：公开接口（活动列表、详情）不需要登录。
            // 需要登录的接口会在 Service 里通过 CurrentUserContext.require() 抛 401。
            return true;
        }
        try {
            long userId = Long.parseLong(raw.trim());
            if (userId > 0) {
                CurrentUserContext.set(userId);
            }
        } catch (NumberFormatException ex) {
            // 非法请求头按「未登录」处理，不抛 500
            log.debug("非法的 {} 请求头: {}", USER_ID_HEADER, raw);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Tomcat 线程是复用的，必须清理，否则会串用户
        CurrentUserContext.clear();
    }
}
