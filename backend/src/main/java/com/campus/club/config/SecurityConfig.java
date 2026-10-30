package com.campus.club.config;

import com.campus.club.auth.JwtAuthenticationFilter;
import com.campus.club.common.ApiResponse;
import com.campus.club.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * 安全配置。
 *
 * <h3>三条决定</h3>
 * <ol>
 *   <li><b>关掉 CSRF</b>：CSRF 攻击依赖浏览器自动携带 Cookie。本项目用
 *       {@code Authorization} 头传令牌，不存在「凭据被自动携带」这个前提，因此不需要 CSRF token。
 *       反过来说，如果哪天改成 Cookie 存 token，这里必须重新打开。</li>
 *   <li><b>不建 Session</b>：{@code STATELESS} 让每次请求都靠令牌自证身份，
 *       多实例部署时不需要 Session 共享。</li>
 *   <li><b>401 与 403 分开</b>：没登录是 401（该去登录），登录了但没权限是 403（登录也没用）。
 *       混成一个码会让前端只能弹「操作失败」。</li>
 * </ol>
 *
 * <h3>授权分两层</h3>
 * URL 级规则处理「登录即可访问」的粗粒度划分；
 * 需要按角色、按数据归属判断的，交给 {@code @PreAuthorize} 和 Service 里的归属校验。
 * 只靠 URL 规则表达不了「社团负责人只能看自己社团的名单」这类规则。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录接口必须匿名可访问，否则谁都登不进来
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // 报名名单是社团内部数据，不属于活动公开信息。
                        // 必须写在下面 /api/activities/** 之前：匹配是按顺序取第一条，
                        // 放到后面就会被那条通配符吞掉，匿名请求一路穿到 @PreAuthorize 才被拒，
                        // 结果「未登录」被报成「无权限」。
                        .requestMatchers(HttpMethod.GET, "/api/activities/*/registrations").authenticated()
                        // 活动列表与详情是公开数据，不登录也能浏览
                        .requestMatchers(HttpMethod.GET, "/api/activities/**").permitAll()
                        // 健康检查与接口文档
                        .requestMatchers("/actuator/health", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 其余 actuator 端点（目前是 /actuator/metrics）只给平台管理员：
                        // 缓存命中率、JVM 指标这些信息对外公开等于给攻击者做侦察
                        .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                        // 其余业务接口一律要求登录；具体角色由 @PreAuthorize 决定
                        .requestMatchers("/api/**").authenticated()
                        // 静态资源、错误页等
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码哈希。BCrypt 自带随机盐，同一个密码每次哈希结果都不同，
     * 因此不需要（也无法）在数据库里单独存 salt 字段。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 未认证：401。
     * <p>
     * 如果过滤器已经把「令牌过期 / 令牌非法」记在请求属性上，就返回更精确的码，
     * 让前端能区分「该静默刷新或重新登录」和「凭证被篡改」。
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
            ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;
            writeJson(request, response, errorCode);
        };
    }

    /** 已认证但无权限：403 */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJson(request, response, ErrorCode.FORBIDDEN);
    }

    /**
     * 统一输出 {@link ApiResponse} 格式：
     * Security 的过滤器在 DispatcherServlet 之前，拿不到 @RestControllerAdvice，
     * 只能自己写响应体，格式必须和全局异常处理保持一致。
     */
    private void writeJson(HttpServletRequest request, HttpServletResponse response,
                           ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }
}
