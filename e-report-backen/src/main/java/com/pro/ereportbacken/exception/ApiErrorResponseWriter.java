package com.pro.ereportbacken.exception;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = requestTraceId(request);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Request-Id", traceId);
        objectMapper.writeValue(response.getWriter(), payload(request, status, message, traceId));
    }

    private Map<String, Object> payload(HttpServletRequest request, HttpStatus status, String message, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", UtcTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("traceId", traceId);
        body.put("errors", Map.of());
        return body;
    }

    private String requestTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader("X-Request-Id");
        return StringUtils.hasText(headerTraceId) ? headerTraceId : UUID.randomUUID().toString();
    }
}
