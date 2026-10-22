package com.pro.ereportbacken.exception;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<GlobalExceptionHandler.ErrorDetails> handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String path = resolvePath(request);
        GlobalExceptionHandler.ErrorDetails details = new GlobalExceptionHandler.ErrorDetails(
                UtcTime.now(),
                status.value(),
                defaultMessageFor(status),
                path,
                requestTraceId(request),
                Map.of()
        );
        return new ResponseEntity<>(details, status);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolvePath(HttpServletRequest request) {
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalPath instanceof String path && StringUtils.hasText(path)) {
            return path;
        }
        return request.getRequestURI();
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
}
