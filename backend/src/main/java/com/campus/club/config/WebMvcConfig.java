package com.campus.club.config;

import com.campus.club.auth.CurrentUserInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置。
 * <p>
 * 只注册身份解析拦截器，作用范围限定在 {@code /api/**}，
 * 避免影响 actuator、swagger 等非业务端点。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserInterceptor currentUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentUserInterceptor).addPathPatterns("/api/**");
    }
}
