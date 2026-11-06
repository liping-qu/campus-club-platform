package com.campus.club.attendance.service;

import com.campus.club.attendance.dto.AdminCheckInRequest;
import com.campus.club.attendance.dto.VolunteerHoursRequest;
import com.campus.club.attendance.vo.AttendanceRecordVO;
import com.campus.club.attendance.vo.MyAttendanceSummaryVO;
import com.campus.club.attendance.vo.MyCheckInVO;

import java.util.List;

/**
 * 签到服务。
 *
 * <h3>两类调用者，两套规则</h3>
 * <table>
 *   <tr><th></th><th>本人签到</th><th>负责人代签</th></tr>
 *   <tr><td>时间窗</td><td>活动开始前 30 分钟 ~ 活动结束</td><td><b>不限</b>（典型场景是结束后补录）</td></tr>
 *   <tr><td>报名要求</td><td>自己已报名</td><td>被代签人已报名</td></tr>
 *   <tr><td>记录方式</td><td>{@code MANUAL}</td><td>{@code ADMIN}（名单上能看出不是本人签的）</td></tr>
 * </table>
 * <p>
 * 时间窗只约束「本人签到」不是偷懒：自助签到的意义是证明「我此刻在场」，
 * 活动结束三天后还能自己点一下，这个证明就没有意义了。补录的合理性由负责人背书。
 */
public interface AttendanceService {

    /** 本人签到。未报名 40903、重复签到 40906、不在时间窗 40911/40912 */
    MyCheckInVO checkIn(Long activityId);

    /** 我在这个活动上的签到状态；未签到返回 checkedIn=false，不抛异常 */
    MyCheckInVO getMyCheckIn(Long activityId);

    /** 负责人代签（补录），返回被代签人的签到记录 */
    AttendanceRecordVO checkInForUser(Long activityId, AdminCheckInRequest request);

    /** 修正某人的志愿时长；没有签到记录返回 40405 */
    void updateVolunteerHours(Long activityId, Long userId, VolunteerHoursRequest request);

    /** 活动签到名单（管理端） */
    List<AttendanceRecordVO> listAttendance(Long activityId);

    /** 我的签到历史与累计志愿时长 */
    MyAttendanceSummaryVO listMyAttendances();
}
