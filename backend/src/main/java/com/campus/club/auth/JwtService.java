package com.campus.club.auth;

import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.user.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <h3>为什么用 JWT 而不是服务端 Session</h3>
 * Session 要在服务端存状态，多实例部署时得做 Session 共享（Redis 或粘性会话）；
 * JWT 把状态放进令牌本身，服务端只验签不存状态，横向扩容时不需要任何共享存储。
 * 代价是<b>无法主动吊销</b>：令牌签发后到过期前一直有效，除非额外引入黑名单。
 * 当前 token 有效期 2 小时、没有「强制下线」需求，因此接受这个代价。
 *
 * <h3>令牌里放什么</h3>
 * 只放 {@code sub}（用户 ID）、{@code username}、{@code role} 和标准时间字段。
 * 不放昵称、手机号这类会变的信息 —— 令牌是快照，放进去了就会和数据库不一致；
 * 需要最新资料时用 {@code /api/auth/me} 查。
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 宁可启动失败，也不要静默使用一个不满足 HS256 要求的短密钥
            throw new IllegalStateException(
                    "app.jwt.secret 至少需要 32 个字节（HS256 要求 256 位），当前为 " + keyBytes.length + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        // 注意：jjwt 按密钥长度自动选算法 —— 32 字节 HS256、48 字节 HS384、64 字节 HS512。
        // 也就是说换个长度不同的 secret 会静默改变签名算法，老令牌可能突然验不过。
        // 生产环境固定用同一个 secret，并在轮换时接受一个过渡期（多密钥验签）才安全。
        log.info("JWT 签名算法 {}（由 secret 长度 {} 字节决定）", signingKey.getAlgorithm(), keyBytes.length);

        if (properties.usingDevSecret()) {
            log.warn("正在使用内置的开发用 JWT 密钥，请在生产环境通过环境变量 APP_JWT_SECRET 覆盖");
        }
        log.info("JWT 已启用，有效期 {}", properties.getTtl());
    }

    /**
     * 用配置的有效期签发令牌。
     */
    public String issue(SysUser user) {
        return issue(user, properties.getTtl());
    }

    /**
     * 指定有效期签发。
     * <p>
     * 单独开放这个重载有两个用途：测试里签发一个「已过期」的令牌验证 401 分支；
     * 将来做「记住我 / 短期令牌」时也不必改签名。
     * 传负数或零表示立即过期。
     */
    public String issue(SysUser user, Duration ttl) {
        Instant now = Instant.now();
        Instant expiration = now.plus(ttl);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 校验并解析令牌。
     * <p>
     * 验签、过期、签发者三项校验全部由 jjwt 完成；任何一项不通过都转成业务异常，
     * 由过滤器转成 401，绝不把底层异常信息抛给调用方。
     *
     * @throws BusinessException {@code TOKEN_EXPIRED} 已过期；{@code INVALID_TOKEN} 签名错误、
     *                           格式损坏、签发者不匹配
     */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (role == null || role.isBlank()) {
                // 正常签发的令牌不会缺角色；缺了说明是被改造过的令牌
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return new AuthPrincipal(userId, username, role);
        } catch (ExpiredJwtException ex) {
            // 过期是「可预期」的，单独的码让前端能区分「该重新登录」和「凭证有问题」
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("非法令牌: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** 剩余有效秒数，用于登录响应里的 expiresIn */
    public long ttlSeconds() {
        return properties.getTtl().toSeconds();
    }
}
