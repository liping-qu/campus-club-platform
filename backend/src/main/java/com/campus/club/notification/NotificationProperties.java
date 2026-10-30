package com.campus.club.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 异步通知配置。
 * <p>
 * 这里的参数都是「线程池出问题时先炸哪一头」的选择，逐个都有对应后果：
 * <ul>
 *   <li>{@code corePoolSize} / {@code maxPoolSize}：并发投递能力上限。
 *       通知是 IO 型（一次 INSERT），不需要开很大；开大了只是把压力挪给数据库连接池。</li>
 *   <li>{@code queueCapacity}：突发流量能缓冲多少。队列越长，
 *       请求被接受后真正投递的时间越晚（通知延迟从毫秒变秒级），所以不要贪大。</li>
 * </ul>
 * 队列满之后的策略不在配置里，因为它是代码决策，见 {@code AsyncConfig}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** 总开关。关掉后监听器照常收事件但不写库，便于压测时隔离通知的影响 */
    private boolean enabled = true;

    private int corePoolSize = 2;

    private int maxPoolSize = 8;

    private int queueCapacity = 2000;
}
