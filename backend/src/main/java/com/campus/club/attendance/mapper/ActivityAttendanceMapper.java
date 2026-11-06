package com.campus.club.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.club.attendance.entity.ActivityAttendance;
import com.campus.club.attendance.vo.AttendanceRecordVO;
import com.campus.club.attendance.vo.MyAttendanceItemVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 签到数据访问。
 * <p>
 * 没有「先查再插」的写法：重复签到由唯一键 {@code uk_attendance_activity_user} 兜底，
 * 调用方捕获 {@code DuplicateKeyException} 转成业务异常即可。
 * 这样即使两个请求同时通过前置判断，也只有一个能写进去。
 */
public interface ActivityAttendanceMapper extends BaseMapper<ActivityAttendance> {

    /**
     * 查某人某活动的签到记录，没签到返回 null。
     */
    ActivityAttendance selectByActivityAndUser(@Param("activityId") Long activityId,
                                               @Param("userId") Long userId);

    /**
     * 活动签到名单（管理端）：谁签了、什么时候签的、记了多少时长。
     * <p>
     * 与「报名名单」是两个视角：报名名单是「报了但可能没来」，签到名单是「确实到了」。
     * 按签到时间排序 —— 现场核对时看的就是到场顺序。
     */
    List<AttendanceRecordVO> selectAttendanceList(@Param("activityId") Long activityId);

    /**
     * 单条签到记录（带用户信息），代签成功后直接返回给调用方，省一次查询。
     */
    AttendanceRecordVO selectRecord(@Param("activityId") Long activityId,
                                    @Param("userId") Long userId);

    /**
     * 我的签到历史：一次 JOIN 取回活动与社团，避免逐条回查。
     */
    List<MyAttendanceItemVO> selectMyAttendances(@Param("userId") Long userId);

    /**
     * 累计志愿时长。用 SQL 求和而不是把记录捞回来在 Java 里加：
     * 历史记录会随年份增长，而页面只关心一个总数。
     */
    BigDecimal sumVolunteerHours(@Param("userId") Long userId);

    /**
     * 修正/补录某人的志愿时长。
     *
     * @return 1 成功；0 表示该用户在这个活动上没有签到记录
     */
    int updateVolunteerHours(@Param("activityId") Long activityId,
                            @Param("userId") Long userId,
                            @Param("volunteerHours") BigDecimal volunteerHours);
}
