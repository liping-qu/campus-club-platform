package com.campus.club.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 活动缓存配置。
 * <p>
 * 这里的每个参数都对应一个具体的失败模式，不是随便填的默认值：
 * <ul>
 *   <li>{@code detailTtl} 长、{@code listTtl} 短 —— 详情几乎不变，列表每次报名都在变；</li>
 *   <li>{@code nullMarkerTtl} 更短 —— 它只用来挡住「查不存在的 id」的重复穿透，不需要长命；</li>
 *   <li>{@code jitterSeconds} —— 给过期时间加随机抖动，避免同一批 key 同时失效造成雪崩；</li>
 *   <li>{@code lockTtl} —— 回填互斥锁的兜底过期时间，防止持锁线程崩了把 key 永久锁死；</li>
 *   <li>{@code degradedCooldown} —— Redis 挂掉后的静默期，期间直接走数据库。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class ActivityCacheProperties {

    /** 总开关。关掉之后代码路径与「没有缓存」完全一致，便于对比压测 */
    private boolean enabled = true;

    private Duration detailTtl = Duration.ofMinutes(10);

    private Duration listTtl = Duration.ofSeconds(60);

    private Duration nullMarkerTtl = Duration.ofSeconds(60);

    /** 过期时间抖动幅度，单位秒，实际 TTL = 基础 TTL ± 该值 */
    private int jitterSeconds = 30;

    private Duration lockTtl = Duration.ofSeconds(3);

    /** Redis 故障后的静默期 */
    private Duration degradedCooldown = Duration.ofSeconds(10);
}
