package com.campus.club.notification.service;

import com.campus.club.common.PageResult;
import com.campus.club.notification.event.RegistrationSucceededEvent;
import com.campus.club.notification.vo.NotificationVO;

/**
 * 站内通知服务。
 *
 * <h3>为什么写库和读库放在同一个接口里</h3>
 * 它们共享同一张表和同一套越权判断（只允许操作自己的通知）。
 * 拆成两个接口只会让「谁负责 user_id 过滤」这件事变得模糊。
 */
public interface NotificationService {

    /**
     * 投递「报名成功」通知。
     * <p>
     * <b>这个方法在异步线程里执行</b>，因此：
     * 拿不到 {@code CurrentUserContext}（ThreadLocal 不跨线程），
     * 也拿不到发起请求的事务 —— 需要的字段全部由入参带进来。
     * <p>
     * 承诺是「尽力投递」：失败只记日志和指标，不抛回给调用方。
     * 通知失败不应该影响一次已经成功的报名。
     */
    void sendRegistrationNotice(RegistrationSucceededEvent event);

    PageResult<NotificationVO> listMy(int pageNo, int pageSize);

    long unreadCount();

    /** 标记单条已读。通知不存在或不属于当前用户都会抛 404，不区分 —— 避免探测他人数据 */
    void markRead(Long notificationId);

    /** 全部标记已读，返回本次实际更新的条数 */
    int markAllRead();
}
