package com.campus.club.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 配置。
 * <p>
 * 密钥不写进代码仓库的最终形态应该是环境变量 {@code JWT_SECRET}（Spring 的松散绑定会把
 * {@code app.jwt.secret} 映射到 {@code APP_JWT_SECRET}）。这里给了一个开发默认值，
 * 只为了让 clone 下来能直接跑起来 —— 启动时会打印警告，生产环境必须覆盖。
 * <p>
 * HS256 要求密钥至少 256 位（32 字节），太短会在启动时直接失败，而不是悄悄退化成弱签名。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 开发默认值，长度 48 字节，满足 HS256 最低要求 */
    public static final String DEV_SECRET = "campus-club-dev-only-secret-please-override-in-production";

    private String secret = DEV_SECRET;

    /** 令牌有效期。校园场景两小时足够，过期后重新登录成本很低 */
    private Duration ttl = Duration.ofHours(2);

    private String issuer = "campus-club";

    public boolean usingDevSecret() {
        return DEV_SECRET.equals(secret);
    }
}
