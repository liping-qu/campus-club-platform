package com.campus.club.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.club.notification.entity.Notification;
import com.campus.club.notification.vo.NotificationVO;
import org.apache.ibatis.annotations.Param;

/**
 * 通知数据访问。
 * <p>
 * 所有「读」与「改」的 SQL 都把 {@code user_id} 写进 WHERE 里，
 * 而不是「先查出来再在 Java 里比较」。理由：一旦漏掉那行 if，
 * 就是任何人都能改任何人的通知已读状态；写进 SQL 则漏掉的概率低得多，
 * 而且越权请求在数据库层就影响 0 行。
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 我的通知列表，走 idx_notification_user_read(user_id, is_read, created_at) */
    IPage<NotificationVO> selectPageByUser(IPage<NotificationVO> page, @Param("userId") Long userId);

    long countUnread(@Param("userId") Long userId);

    /** 标记单条已读。返回 0 说明这条通知不存在，或不属于当前用户 */
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /** 全部标记已读，返回受影响行数（可能为 0，这不是错误） */
    int markAllRead(@Param("userId") Long userId);
}
