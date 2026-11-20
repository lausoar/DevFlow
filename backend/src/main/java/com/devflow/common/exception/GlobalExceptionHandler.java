package com.devflow.common.exception;

import com.devflow.common.result.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 *
 * HTTP 状态码与业务 code 保持一致：客户端不必再从 HTTP 200 的响应体里二次判断成败，
 * 401 可直接触发登录跳转、404 可作为"资源不可见"的统一口径（不泄露资源存在性）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：code 映射为同值的 HTTP 状态 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 唯一键冲突 → 409。
     *
     * 之前没有这个 handler，撞唯一键会落进最后的兜底分支变成 500。
     * 最典型的场景就是文档并发保存：两个请求都基于版本 N 插同号快照，
     * 第二个撞 uk_doc_ver —— 用户看到"服务器错误"，但那其实是并发冲突，
     * 应该提示"已被他人修改，请刷新"。状态码错会直接导致前端提示错。
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<R<Void>> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(409, "数据已被他人修改（唯一约束冲突），请刷新后重试"));
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return R.fail(400, message);
    }

    /** 绑定异常 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return R.fail(400, message);
    }

    /** 约束违反异常 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        return R.fail(400, e.getMessage());
    }

    /** 认证异常 */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleBadCredentials(BadCredentialsException e) {
        return R.unauthorized("用户名或密码错误");
    }

    /** 权限不足 */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.forbidden("权限不足");
    }

    /**
     * 框架层的 4xx 异常。
     *
     * 这些异常原先全部落进兜底分支变成 500「系统内部错误」，把"客户端请求写错了"
     * 显示成"服务器坏了"：调错方法、缺必填参数、body JSON 类型不对、路径不存在，
     * 用户和前端都无从判断该怎么修。统一映射到对应状态码并给出可行动的提示。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "该地址不支持 " + e.getMethod() + " 方法");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(400, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(400, "参数 " + e.getName() + " 取值格式不正确");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.fail(400, "请求体格式不正确（JSON 语法错误或字段类型不匹配）");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public R<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return R.fail(415, "不支持的 Content-Type，请使用 application/json");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(404, "接口不存在: /" + e.getResourcePath());
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统内部错误，请联系管理员");
    }

    /** 业务 code → HTTP 状态；未识别的 code 一律按 500 处理，避免客户端收到非法状态码 */
    private static HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 405 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 409 -> HttpStatus.CONFLICT;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> code >= 400 && code < 500
                    ? HttpStatus.valueOf(code)
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
