package com.pro.ereportbacken.exception;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
@Log4j2
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDetails> handleUnreadableRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "提交内容不完整或格式不正确。", ex, request, false, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "请求的数据不存在或已被删除。", ex, request, false, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetails> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = friendlyValidationMessage(error.getDefaultMessage());
            errors.put(fieldName, errorMessage);
        });
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "提交内容不完整或格式不正确。", ex, request, false, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDetails> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "请求参数取值不正确，请检查后重试。",
                ex,
                request,
                false,
                null
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorDetails> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = friendlyResponseStatusMessage(ex.getReason(), resolvedStatus);
        return buildErrorResponse(resolvedStatus, message, ex, request, resolvedStatus.is5xxServerError(), null);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ErrorDetails> handleMultipartException(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                WorkOrderAttachmentLimits.TOTAL_LIMIT_MESSAGE,
                ex,
                request,
                true,
                null
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDetails> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, friendlyIllegalArgumentMessage(ex.getMessage()), ex, request, false, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDetails> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        HttpStatus status = statusForIllegalState(ex.getMessage());
        return buildErrorResponse(status, friendlyIllegalStateMessage(ex.getMessage(), status), ex, request, status.is5xxServerError(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "当前账号没有执行该操作的权限。", ex, request, false, null);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorDetails> handleErrorResponseException(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return buildErrorResponse(resolvedStatus, defaultMessageFor(resolvedStatus), ex, request, resolvedStatus.is5xxServerError(), null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDetails> handleGenericRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理请求，请稍后重试。", ex, request, true, null);
    }

    private ResponseEntity<ErrorDetails> buildErrorResponse(HttpStatus status,
                                                            String message,
                                                            Exception ex,
                                                            HttpServletRequest request,
                                                            boolean logStack,
                                                            Map<String, String> errors) {
        String traceId = requestTraceId(request);
        String diagnostic = ex instanceof HttpMessageNotReadableException ? "Malformed request body" : ex.getMessage();
        if (logStack || status.is5xxServerError()) {
            log.error("Request failed. traceId={}, status={}, method={}, uri={}, message={}",
                    traceId, status.value(), request.getMethod(), request.getRequestURI(), diagnostic, ex);
        } else {
            log.warn("Request rejected. traceId={}, status={}, method={}, uri={}, message={}",
                    traceId, status.value(), request.getMethod(), request.getRequestURI(), diagnostic);
        }
        ErrorDetails errorDetails = new ErrorDetails(
                UtcTime.now(),
                status.value(),
                message,
                request.getRequestURI(),
                traceId,
                errors == null ? Map.of() : errors
        );
        return new ResponseEntity<>(errorDetails, status);
    }

    private String requestTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader("X-Request-Id");
        return StringUtils.hasText(headerTraceId) ? headerTraceId : UUID.randomUUID().toString();
    }

    private String defaultMessageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "提交内容不完整或格式不正确。";
            case UNAUTHORIZED -> "登录状态已失效，请重新登录。";
            case FORBIDDEN -> "当前账号没有执行该操作的权限。";
            case NOT_FOUND -> "请求的数据不存在或已被删除。";
            case CONFLICT -> "当前数据状态已变化，请刷新后重试。";
            case UNSUPPORTED_MEDIA_TYPE -> "当前应用版本过旧，无法使用新版上传接口。请升级到最新版本后重新提交。";
            case PAYLOAD_TOO_LARGE -> WorkOrderAttachmentLimits.TOTAL_LIMIT_MESSAGE;
            case TOO_MANY_REQUESTS -> "操作过于频繁，请稍后再试。";
            case SERVICE_UNAVAILABLE -> "外部服务暂时不可用，请稍后重试。";
            default -> status.is5xxServerError() ? "系统暂时无法处理请求，请稍后重试。" : "请求处理失败，请稍后重试。";
        };
    }

    private String friendlyResponseStatusMessage(String rawReason, HttpStatus status) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (StringUtils.hasText(reason) && containsChinese(reason)) {
            return reason;
        }
        return defaultMessageFor(status);
    }

    private String friendlyValidationMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (StringUtils.hasText(message) && containsChinese(message)) {
            return message;
        }
        return "字段填写不正确，请检查后重试。";
    }

    private HttpStatus statusForIllegalState(String rawMessage) {
        String message = normalize(rawMessage);
        if (message.contains("must be authenticated")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (message.contains("already resolved")
                || message.contains("already closed")
                || message.contains("still assigned")
                || message.contains("not in DELETED status")
                || message.contains("内置岗位")
                || message.contains("幂等键")
                || message.contains("预览")
                || message.contains("任务当前")
                || message.contains("当前任务状态")
                || message.contains("用户授权版本")
                || message.contains("仍被") && message.contains("用户使用")) {
            return HttpStatus.CONFLICT;
        }
        if (message.contains("amap") || message.contains("高德") || message.contains("r2")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String friendlyIllegalStateMessage(String rawMessage, HttpStatus status) {
        String message = normalize(rawMessage);
        if (message.contains("amap") || message.contains("高德")) {
            return "定位服务暂时不可用，请稍后重试或手动填写地址信息。";
        }
        if (message.contains("r2")) {
            return containsChinese(message) ? rawMessage.trim() : "安装包云存储暂时不可用，请稍后重试。";
        }
        if (containsChinese(message)) {
            return rawMessage.trim();
        }
        if (message.contains("must be authenticated")) {
            return "登录状态已失效，请重新登录。";
        }
        if (message.contains("already resolved or closed")) {
            return "该工单已处理或已关闭，请刷新后查看最新状态。";
        }
        if (message.contains("already closed")) {
            return "该工单已关闭，请勿重复操作。";
        }
        if (message.contains("not in DELETED status")) {
            return "该用户当前不是已删除状态，无法恢复。";
        }
        return defaultMessageFor(status);
    }

    private String friendlyIllegalArgumentMessage(String rawMessage) {
        String message = normalize(rawMessage);
        if (!StringUtils.hasText(message)) {
            return "提交内容不完整或格式不正确。";
        }
        if (containsChinese(message)) {
            return rawMessage.trim();
        }
        if (message.contains("work order type")) {
            return "工单类型不支持，请刷新页面后重试。";
        }
        if (message.contains("username") && message.contains("already exists")) {
            return "用户名已存在，请更换后重试。";
        }
        if (message.contains("username") && message.contains("blank")) {
            return "用户名不能为空。";
        }
        if (message.contains("permission") && message.contains("fixed permission catalog")) {
            return "选择的权限不在系统固定权限清单中，请刷新页面后重试。";
        }
        if (message.contains("permission") && message.contains("not found")) {
            return "选择的权限不存在，请刷新页面后重试。";
        }
        if (message.contains("invalid status value")) {
            return "用户状态不正确，请刷新页面后重试。";
        }
        if (message.contains("new password")) {
            return "新密码至少需要6位，且不能超过100位。";
        }
        if (message.contains("primary administrator")) {
            return "不能删除默认管理员账号。";
        }
        if (message.contains("config key")) {
            return "配置键不能为空。";
        }
        if (message.contains("bearer token") || message.contains("token")) {
            return "登录状态已失效，请重新登录。";
        }
        return "提交内容不完整或格式不正确。";
    }

    private boolean containsChinese(String message) {
        return message != null && message.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String normalize(String rawMessage) {
        return rawMessage == null ? "" : rawMessage.trim().toLowerCase();
    }

    public static class ErrorDetails {
        private final LocalDateTime timestamp;
        private final int status;
        private final String message;
        private final String path;
        private final String traceId;
        private final Map<String, String> errors;

        public ErrorDetails(LocalDateTime timestamp, int status, String message, String path, String traceId, Map<String, String> errors) {
            this.timestamp = timestamp;
            this.status = status;
            this.message = message;
            this.path = path;
            this.traceId = traceId;
            this.errors = new LinkedHashMap<>(errors);
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getMessage() { return message; }
        public String getPath() { return path; }
        public String getTraceId() { return traceId; }
        public Map<String, String> getErrors() { return errors; }
    }
}
