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
