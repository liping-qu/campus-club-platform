package com.campus.club.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 * <p>
 * 编码规则：5 位数字，前 3 位对应 HTTP 状态码，后 2 位为同类业务细分。
 * 这样既能让前端按 HTTP 状态码做统一处理，也能按 code 精确提示。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    // ---- 400 参数类 ----
    INVALID_PARAM(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    MALFORMED_BODY(40001, "请求体格式错误", HttpStatus.BAD_REQUEST),
    INVALID_TIME_RANGE(40002, "结束时间必须晚于开始时间", HttpStatus.BAD_REQUEST),
    SIGNUP_DEADLINE_AFTER_START(40003, "报名截止时间不能晚于活动开始时间", HttpStatus.BAD_REQUEST),

    // ---- 401 认证类 ----
    UNAUTHORIZED(40100, "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(40103, "登录凭证无效", HttpStatus.UNAUTHORIZED),

    // ---- 403 权限类 ----
    FORBIDDEN(40300, "无权执行该操作", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(40301, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN),

    // ---- 404 资源类 ----
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    ACTIVITY_NOT_FOUND(40401, "活动不存在", HttpStatus.NOT_FOUND),
    CLUB_NOT_FOUND(40402, "社团不存在", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(40403, "用户不存在", HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND(40404, "通知不存在", HttpStatus.NOT_FOUND),
    ATTENDANCE_NOT_FOUND(40405, "签到记录不存在", HttpStatus.NOT_FOUND),

    // ---- 409 业务冲突类 ----
    ACTIVITY_FULL(40901, "活动名额已满", HttpStatus.CONFLICT),
    ALREADY_REGISTERED(40902, "你已报名该活动", HttpStatus.CONFLICT),
    NOT_REGISTERED(40903, "你尚未报名该活动", HttpStatus.CONFLICT),
    ACTIVITY_CLOSED(40904, "活动已结束或已下架", HttpStatus.CONFLICT),
    SIGNUP_CLOSED(40905, "报名已截止", HttpStatus.CONFLICT),
    ALREADY_CHECKED_IN(40906, "你已签到，请勿重复签到", HttpStatus.CONFLICT),
    DUPLICATE_USERNAME(40907, "用户名已被占用", HttpStatus.CONFLICT),
    /**
     * 条件更新失败，但重新读取快照后发现条件其实都满足。
     * <p>
     * 这只可能出现在「判断」与「更新」之间有并发修改的瞬间，
     * 属于可重试的竞态，不应该伪装成「名额已满」误导用户。
     */
    REGISTER_CONFLICT(40908, "报名状态刚刚发生变化，请刷新后重试", HttpStatus.CONFLICT),
    /**
     * 名额要改到已报名人数以下。
     * <p>
     * 直接改会违反表上的 CHECK（joined_count <= capacity），
     * 但数据库报的是「约束冲突」，用户看不懂；必须由业务层拦下并说清原因。
     */
    CAPACITY_BELOW_JOINED(40909, "名额不能小于已报名人数", HttpStatus.CONFLICT),
    /** 活动的当前状态不允许这个操作（例如下架一个草稿、删除一个已发布的活动） */
    ACTIVITY_STATUS_CONFLICT(40910, "当前状态不允许该操作", HttpStatus.CONFLICT),
    /** 签到还没开始：太早到场。窗口见 AttendanceServiceImpl 里的常量 */
    CHECKIN_NOT_OPEN(40911, "签到尚未开始", HttpStatus.CONFLICT),
    /** 签到已经结束：活动结束后只能由负责人补录 */
    CHECKIN_CLOSED(40912, "签到已结束，请联系活动负责人补录", HttpStatus.CONFLICT),
    /**
     * 已签到的人不能取消报名。
     * <p>
     * 否则会出现「报名记录没了、志愿时长还在」的孤儿数据：
     * 时长是对「实际参加」的认定，而取消报名等于声明「没参加」。
     */
    CHECKED_IN_CANNOT_CANCEL(40913, "已签到的报名不能取消，请联系活动负责人", HttpStatus.CONFLICT),

    // ---- 500 系统类 ----
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
