package com.campus.club.registration.service;

import com.campus.club.registration.vo.MyRegistrationVO;
import com.campus.club.registration.vo.RegistrationAttendeeVO;
import com.campus.club.registration.vo.RegistrationItemVO;
import com.campus.club.registration.vo.RegistrationResultVO;

import java.util.List;

/**
 * 活动报名服务。
 * <p>
 * 本接口的所有写操作都以「当前登录用户」为主体，用户身份从
 * {@link com.campus.club.auth.CurrentUserContext} 取，<b>不接受前端传入 userId</b>。
 * 这是一条硬规则：一旦允许前端指定 userId，越权报名/取消就只是改个数字的事。
 */
public interface RegistrationService {

    /**
     * 报名活动。
     *
     * @throws com.campus.club.common.BusinessException 活动不存在、已下架、报名截止、
     *                                                  名额已满、重复报名
     */
    RegistrationResultVO register(Long activityId);

    /**
     * 取消报名。未报名或已取消时抛出 {@code NOT_REGISTERED}。
     */
    void cancel(Long activityId);

    /**
     * 查询我在指定活动上的报名状态，未报名返回 {@code NONE} 而不是 404。
     */
    MyRegistrationVO getMyRegistration(Long activityId);

    /**
     * 我的报名列表（仅未取消的）。
     */
    List<RegistrationItemVO> listMyRegistrations();

    /**
     * 活动报名名单，供社团负责人签到核对、平台管理员查看全局。
     * <p>
     * 权限分两层，缺一不可：
     * <ol>
     *   <li>角色层：必须是 CLUB_ADMIN 或 PLATFORM_ADMIN（控制器上的 {@code @PreAuthorize}）；</li>
     *   <li>数据层：社团负责人只能看<b>自己社团</b>的活动（本方法内的归属校验）。</li>
     * </ol>
     * 只做角色校验的话，A 社团的负责人能看 B 社团的报名名单，就是典型的横向越权。
     *
     * @throws com.campus.club.common.BusinessException 活动不存在（40401）或无权查看（40300）
     */
    List<RegistrationAttendeeVO> listAttendees(Long activityId);
}
