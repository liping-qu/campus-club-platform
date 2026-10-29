package com.campus.club.common;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 只用于「可以预期、可以返回给用户」的业务失败，例如名额已满、重复报名。
 * 不要用它包裹 NPE、SQL 异常等系统故障，那些交给全局异常处理兜底。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
