package com.campus.club.notification.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.common.PageResult;
import com.campus.club.config.AsyncConfig;
import com.campus.club.notification.NotificationProperties;
import com.campus.club.notification.entity.Notification;
import com.campus.club.notification.entity.NotificationType;
import com.campus.club.notification.event.RegistrationSucceededEvent;
import com.campus.club.notification.mapper.NotificationMapper;
import com.campus.club.notification.service.NotificationService;
import com.campus.club.notification.vo.NotificationVO;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 站内通知实现。
 *
 * <h3>异步的边界画在这里</h3>
 * 「写一条通知」这件事被放到 {@code notificationExecutor} 线程池里做，
 * 报名接口不用等它。选 {@code @Async} 而不是 MQ 的理由见
 * {@code docs/async-notification.md}：单实例、可丢失、无跨服务消费者。
 *
 * <h3>失败只降级，不外抛</h3>
 * 通知是「附赠品」。一次报名已经提交成功了，不能因为通知写不进去就报错给用户。
 * 所以这里 catch 住所有异常，打 WARN + 计数，让人能从指标上看到「通知在丢」，
 * 而不是让用户在报名成功后又收到一个 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    /** 单页上限：通知列表是「一眼扫完」的场景，没有理由一次拉 1000 条 */
    private static final int MAX_PAGE_SIZE = 50;

    private static final String CONTENT_TEMPLATE =
            "你已成功报名《%s》。时间：%s，地点：%s，当前剩余名额 %d 个。";

    private final NotificationMapper notificationMapper;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @Override
    public void sendRegistrationNotice(RegistrationSucceededEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(event.userId());
            notification.setTitle("报名成功");
            notification.setContent(String.format(CONTENT_TEMPLATE,
                    safe(event.activityTitle()),
                    event.startTime() == null ? "待定" : event.startTime().format(TIME_FORMAT),
                    safe(event.location()),
                    event.remaining()));
            notification.setType(NotificationType.REGISTRATION.name());
            notification.setIsRead(0);
            notification.setCreatedAt(LocalDateTime.now());

            notificationMapper.insert(notification);
            counter("success").increment();
            log.info("站内通知已投递 userId={} activityId={} notificationId={}",
                    event.userId(), event.activityId(), notification.getId());
        } catch (Exception ex) {
            // 用户可能刚好被删掉（外键冲突）、数据库可能短暂不可用 —— 都只影响这条通知
            counter("failed").increment();
            log.warn("站内通知投递失败，已丢弃 userId={} activityId={}",
                    event.userId(), event.activityId(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationVO> listMy(int pageNo, int pageSize) {
        Long userId = CurrentUserContext.require();
        int safePage = Math.max(1, pageNo);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        IPage<NotificationVO> result = notificationMapper.selectPageByUser(
                new Page<>(safePage, safeSize), userId);
        return PageResult.of(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationMapper.countUnread(CurrentUserContext.require());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId) {
        Long userId = CurrentUserContext.require();
        // user_id 在 WHERE 里：别人的通知影响 0 行，直接当不存在处理
        if (notificationMapper.markRead(notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead() {
        return notificationMapper.markAllRead(CurrentUserContext.require());
    }

    private io.micrometer.core.instrument.Counter counter(String result) {
        return meterRegistry.counter("app.notification.dispatch", "result", result);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未命名活动" : value;
    }
}
