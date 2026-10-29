package com.campus.club.common;

import lombok.Getter;

/**
 * 统一响应体。
 * <p>
 * 约定：HTTP 状态码表达传输层语义，{@code code} 表达业务语义。
 * {@code code == 0} 表示成功，其余取值见 {@link ErrorCode}。
 */
@Getter
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}
