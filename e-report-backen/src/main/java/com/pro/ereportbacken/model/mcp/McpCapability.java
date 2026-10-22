package com.pro.ereportbacken.model.mcp;

import java.util.List;

public record McpCapability(
        String id,
        String method,
        String path,
        String description,
        boolean highRisk,
        List<String> queryParameters,
        List<String> requestBodyFields,
        List<String> errorStatuses,
        boolean requiresIdempotencyKey
) {
    public McpCapability {
        queryParameters = List.copyOf(queryParameters);
        requestBodyFields = List.copyOf(requestBodyFields);
        errorStatuses = List.copyOf(errorStatuses);
    }
}
