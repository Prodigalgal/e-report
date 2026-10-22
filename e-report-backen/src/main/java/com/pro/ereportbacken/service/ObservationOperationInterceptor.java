package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ObservationOutcome;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.service.ObservationServerOperationRecorder.OperationSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Log4j2
public class ObservationOperationInterceptor implements HandlerInterceptor {

    private static final String START_NANOS_ATTRIBUTE = ObservationOperationInterceptor.class.getName() + ".startNanos";
    private static final String TRACE_ID_ATTRIBUTE = ObservationOperationInterceptor.class.getName() + ".traceId";
    private static final String SPAN_ID_ATTRIBUTE = ObservationOperationInterceptor.class.getName() + ".spanId";
    private static final String PARENT_SPAN_ID_ATTRIBUTE = ObservationOperationInterceptor.class.getName() + ".parentSpanId";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObservationSettingsService settingsService;
    private final ObservationRequestIdentityResolver identityResolver;
    private final ObservationServerOperationRecorder recorder;
    private final Tracer tracer;

    public ObservationOperationInterceptor(ObservationSettingsService settingsService,
                                           ObservationRequestIdentityResolver identityResolver,
                                           ObservationServerOperationRecorder recorder,
                                           Tracer tracer) {
        this.settingsService = settingsService;
        this.identityResolver = identityResolver;
        this.recorder = recorder;
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldRecord(request, handler)) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                request.setAttribute(TRACE_ID_ATTRIBUTE, currentSpan.context().traceId());
                request.setAttribute(SPAN_ID_ATTRIBUTE, currentSpan.context().spanId());
                request.setAttribute(PARENT_SPAN_ID_ATTRIBUTE, currentSpan.context().parentId());
            }
        }
        exposeCorrelationHeaders(request, response);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        Object startedAt = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (!(startedAt instanceof Long startNanos)) {
            return;
        }
        try {
            ObservationSettingsService.Policy policy = settingsService.currentPolicy();
            if (!policy.enabled() || !policy.operationEnabled()) {
                return;
            }
            String route = routePattern(request);
            OperationDescriptor descriptor = describe(route);
            int status = response.getStatus();
            ObservationOutcome outcome = exception == null && status < 400
                    ? ObservationOutcome.SUCCESS
                    : ObservationOutcome.FAILURE;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            recorder.record(new OperationSnapshot(
                    identityResolver.resolve(),
                    platform(request.getHeader("X-Client-Platform")),
                    descriptor.category(),
                    descriptor.eventName(),
                    request.getMethod(),
                    route,
                    status,
                    durationMs,
                    outcome,
                    request.getHeader("X-Request-Id"),
                    stringAttribute(request, TRACE_ID_ATTRIBUTE),
                    stringAttribute(request, SPAN_ID_ATTRIBUTE),
                    stringAttribute(request, PARENT_SPAN_ID_ATTRIBUTE),
                    request.getHeader("X-Client-App-Version"),
                    request.getHeader("X-Client-App-Build"),
                    request.getHeader("X-Client-Network-Type"),
                    request.getHeader("User-Agent"),
                    exception == null ? null : exception.getClass().getSimpleName(),
                    request.getMethod() + " " + route + " -> " + status
            ));
        } catch (Exception recordingException) {
            log.debug("记录服务端操作埋点失败，业务响应不受影响: method={}, route={}",
                    request.getMethod(), routePattern(request), recordingException);
        }
    }

    private void exposeCorrelationHeaders(HttpServletRequest request, HttpServletResponse response) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) return;
        String traceId = currentSpan.context().traceId();
        String spanId = currentSpan.context().spanId();
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("traceparent", "00-" + traceId + "-" + spanId + "-01");
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) response.setHeader("X-Request-Id", requestId);
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private boolean shouldRecord(HttpServletRequest request, Object handler) {
        return handler instanceof HandlerMethod
                && MUTATING_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                && !request.getRequestURI().contains("/observability/");
    }

    private String routePattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? request.getRequestURI() : String.valueOf(pattern);
    }

    private ObservationPlatform platform(String value) {
        if (value != null) {
            try {
                return ObservationPlatform.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 使用服务端来源作为无法识别客户端的安全默认值。
            }
        }
        return ObservationPlatform.SERVER;
    }

    private OperationDescriptor describe(String route) {
        if (route.contains("/auth/login")) return new OperationDescriptor("AUTH", "AUTH_LOGIN");
        if (route.equals("/work-orders")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_CREATE");
        if (route.contains("/responses")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_RESPOND");
        if (route.contains("/claims")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_CLAIM");
        if (route.contains("/reassignments")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_REASSIGN");
        if (route.contains("/assignments")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_ASSIGN");
        if (route.contains("/verifications")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_VERIFY");
        if (route.contains("/holds")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_HOLD");
        if (route.contains("/resumptions")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_RESUME");
        if (route.contains("/cancellations")) return new OperationDescriptor("WORK_ORDER", "WORK_ORDER_CANCEL");
        if (route.contains("/file-transfers")) return new OperationDescriptor("ATTACHMENT", "ATTACHMENT_TRANSFER");
        if (route.contains("/location")) return new OperationDescriptor("LOCATION", "LOCATION_REQUEST");
        if (route.contains("/app-updates")) return new OperationDescriptor("UPDATE", "APP_UPDATE_MANAGE");
        if (route.contains("/admin/users")) return new OperationDescriptor("ADMIN", "USER_MANAGE");
        if (route.contains("/admin/authorization-profiles")) return new OperationDescriptor("ADMIN", "AUTHORIZATION_PROFILE_MANAGE");
        if (route.contains("/admin/configs") || route.contains("/admin/notifications")) {
            return new OperationDescriptor("ADMIN", "CONFIG_MANAGE");
        }
        return new OperationDescriptor("SYSTEM", "API_MUTATION");
    }

    private record OperationDescriptor(String category, String eventName) {
    }
}
