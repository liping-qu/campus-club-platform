package com.campus.club.exception;

import com.campus.club.common.ApiResponse;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * 分三类处理：
 * <ul>
 *   <li>业务异常 {@link BusinessException}：可预期，按业务错误码返回，日志记 warn。</li>
 *   <li>参数异常：统一收敛成 400，把字段级原因返回给调用方。</li>
 *   <li>系统异常：对外只返回「系统繁忙」，对内记录完整堆栈，避免泄漏实现细节。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        // 业务异常属于预期分支，用 warn 而不是 error，避免污染告警
        log.warn("业务异常 code={} message={}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .sorted()
                .limit(3)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_PARAM, detail.isEmpty() ? ErrorCode.INVALID_PARAM.getMessage() : detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .limit(3)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_PARAM, detail.isEmpty() ? ErrorCode.INVALID_PARAM.getMessage() : detail));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("请求参数格式错误: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_PARAM, "请求参数格式不正确"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.MALFORMED_BODY.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.MALFORMED_BODY));
    }

    /**
     * 访问不存在的路径或静态资源。
     * <p>
     * 如果不单独处理，会落到下面的 {@link Exception} 分支被当成系统故障返回 500，
     * 让调用方误以为服务端出错。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("请求的路径不存在: {}", ex.getResourcePath());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        // 系统异常必须保留完整堆栈，但绝不把堆栈或 SQL 内容返回给调用方
        log.error("系统异常", ex);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
