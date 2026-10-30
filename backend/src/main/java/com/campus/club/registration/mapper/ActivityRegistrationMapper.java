package com.campus.club.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.club.registration.entity.ActivityRegistration;
import com.campus.club.registration.vo.RegistrationAttendeeVO;
import com.campus.club.registration.vo.RegistrationItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报名记录数据访问。
 * <p>
 * 三个写操作都用「带状态条件的 UPDATE + 判断受影响行数」，
 * 而不是「先查后改」，这样重复取消、重复报名不会产生额外副作用。
 */
public interface ActivityRegistrationMapper extends BaseMapper<ActivityRegistration> {

    /**
     * 把已取消的记录恢复为已报名（重新报名时复用原行）。
     *
     * @return 1 表示复活成功；0 表示当前不是「已取消」状态
     */
    int reviveRegistration(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * 取消报名：只有「已报名」才能改成「已取消」。
     *
     * @return 1 表示取消成功；0 表示本来就没报名或已取消
     */
    int cancelRegistration(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * 查询某用户在某活动上的报名记录（不论状态）。
     */
    ActivityRegistration selectByActivityAndUser(@Param("activityId") Long activityId,
                                                 @Param("userId") Long userId);

    /**
     * 统计某活动的有效报名数，用于一致性校验与测试断言。
     */
    int countActiveByActivity(@Param("activityId") Long activityId);

    /**
     * 我的报名列表（只含未取消的）。
     * <p>
     * 一次 JOIN 取回活动信息，避免「先查报名再逐条查活动」的 N+1。
     */
    List<RegistrationItemVO> selectMyRegistrations(@Param("userId") Long userId);

    /**
     * 活动报名名单（管理端）。含签到状态，供社团负责人现场核对。
     * <p>
     * 这里<b>不做权限过滤</b>：能查哪个活动由 Service 层判断，
     * 把权限混进 SQL 会让「为什么这个人看不到数据」变得很难排查。
     */
    List<RegistrationAttendeeVO> selectAttendees(@Param("activityId") Long activityId);
}
