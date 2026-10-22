package com.pro.ereportbacken.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pro.ereportbacken.model.mcp.McpCapability;
import com.pro.ereportbacken.security.ApiTokenAuthenticationDetails;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.service.McpBackendApiClient;
import com.pro.ereportbacken.service.McpBackendApiClient.InvalidMcpArgumentsException;
import com.pro.ereportbacken.service.McpBackendApiClient.ResponseBodyTooLargeException;
import com.pro.ereportbacken.service.McpCapabilityCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/mcp")
public class McpStreamableHttpController {

    private static final Logger LOGGER = LogManager.getLogger(McpStreamableHttpController.class);
    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final String PROTOCOL_VERSION_META = "io.modelcontextprotocol/protocolVersion";
    private static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> TOP_LEVEL_ARGUMENTS = Set.of("pathVariables", "query", "body", "idempotencyKey");
    private static final int MAX_MCP_MESSAGE_BYTES = 1_048_576;

    private final ObjectMapper objectMapper;
    private final McpCapabilityCatalogService capabilityCatalog;
    private final McpBackendApiClient backendApiClient;
    private final String serverVersion;

    public McpStreamableHttpController(ObjectMapper objectMapper,
                                       McpCapabilityCatalogService capabilityCatalog,
                                       McpBackendApiClient backendApiClient,
                                       @Value("${spring.application.version:development}") String serverVersion) {
        this.objectMapper = objectMapper;
        this.capabilityCatalog = capabilityCatalog;
        this.backendApiClient = backendApiClient;
        this.serverVersion = serverVersion;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> post(@RequestBody byte[] rawMessage, HttpServletRequest request) {
        if (rawMessage.length > MAX_MCP_MESSAGE_BYTES) {
            return encodeResponse(errorResponse(request, null, 413, -32600,
                    "MCP request exceeds the 1 MiB limit", null));
        }
        JsonNode message;
        try {
            message = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawMessage);
        } catch (IOException exception) {
            return malformedJson(request);
        }
        return encodeResponse(processMessage(message, request));
    }

    ResponseEntity<JsonNode> processMessage(JsonNode message, HttpServletRequest request) {
        JsonNode id = message != null ? message.get("id") : null;
        if (message == null || !message.isObject()
                || !message.path("jsonrpc").isTextual()
                || !"2.0".equals(message.path("jsonrpc").asText())
                || !message.path("method").isTextual()
                || !message.path("params").isObject()
                || id == null || !(id.isTextual() || id.isNumber())) {
            return errorResponse(request, id, 400, -32600, "Invalid JSON-RPC request", null);
        }
        String method = message.path("method").asText();
        ObjectNode params = (ObjectNode) message.path("params");
        ResponseEntity<JsonNode> headerError = validateHeaders(request, params, method, id);
        if (headerError != null) {
            return headerError;
        }

        String requestedVersion = request.getHeader("MCP-Protocol-Version");
        if (!PROTOCOL_VERSION.equals(requestedVersion)) {
            ObjectNode errorData = objectMapper.createObjectNode();
            ArrayNode supported = errorData.putArray("supported");
            supported.add(PROTOCOL_VERSION);
            errorData.put("requested", requestedVersion);
            return errorResponse(request, id, 400, -32022, "Unsupported protocol version", errorData);
        }
        if (!acceptsRequiredResponseTypes(request.getHeader(HttpHeaders.ACCEPT))) {
            return errorResponse(request, id, 406, -32600,
                    "Accept must include application/json and text/event-stream", null);
        }
        JsonNode metadata = params.get("_meta");
        if (metadata == null || !metadata.isObject()
                || !metadata.path(PROTOCOL_VERSION_META).isTextual()
                || !PROTOCOL_VERSION.equals(metadata.path(PROTOCOL_VERSION_META).asText())
                || !metadata.path("io.modelcontextprotocol/clientCapabilities").isObject()) {
            return errorResponse(request, id, 400, -32020,
                    "The request metadata must declare the protocol version and client capabilities", null);
        }

        return switch (method) {
            case "server/discover" -> resultResponse(request, id, discoveryResult());
            case "tools/list" -> resultResponse(request, id, toolsListResult());
            case "tools/call" -> callTool(request, id, params);
            case "ping" -> resultResponse(request, id, completeResult());
            default -> errorResponse(request, id, 404, -32601, "Method not found", null);
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<byte[]> malformedJson(HttpServletRequest request) {
        return encodeResponse(errorResponse(request, null, 400, -32700, "Parse error", null));
    }

    private ResponseEntity<byte[]> encodeResponse(ResponseEntity<JsonNode> response) {
        try {
            return new ResponseEntity<>(objectMapper.writeValueAsBytes(response.getBody()),
                    response.getHeaders(), response.getStatusCode());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode the MCP protocol response", exception);
        }
    }

    private ResponseEntity<JsonNode> callTool(HttpServletRequest request, JsonNode id, ObjectNode params) {
        String requestId = requestId(request);
        String toolName = params.path("name").isTextual() ? params.path("name").asText() : "";
        McpCapability capability = capabilityCatalog.currentCapability(toolName).orElse(null);
        if (capability == null) {
            audit(request, requestId, toolName, 403, 0);
            ObjectNode result = toolResult("当前 API Token 无权使用该工具，或该工具不存在。", null, true);
            return resultResponse(request, id, result);
        }

        JsonNode arguments = params.get("arguments");
        if (arguments == null) {
            arguments = objectMapper.createObjectNode();
        }
        if (!arguments.isObject()) {
            audit(request, requestId, capability.id(), 400, 0);
            return errorResponse(request, id, 400, -32602, "Tool arguments must be a JSON object", null);
        }
        if (!containsOnlyArguments(arguments)) {
            audit(request, requestId, capability.id(), 400, 0);
            return errorResponse(request, id, 400, -32602, "Tool arguments contain unsupported fields", null);
        }

        long startedAtNanos = System.nanoTime();
        try {
            McpBackendApiClient.BackendCallResult apiResult = backendApiClient.invoke(
                    capability, arguments, request, requestId);
            long durationMillis = elapsedMillis(startedAtNanos);
            audit(request, requestId, capability.id(), apiResult.status(), durationMillis);
            ObjectNode result = toolResult(apiResult.text(), apiResult.structuredContent(), apiResult.isError());
            return resultResponse(request, id, result);
        } catch (InvalidMcpArgumentsException exception) {
            audit(request, requestId, capability.id(), 400, elapsedMillis(startedAtNanos));
            return errorResponse(request, id, 400, -32602, exception.getMessage(), null);
        } catch (ResponseBodyTooLargeException exception) {
            audit(request, requestId, capability.id(), 502, elapsedMillis(startedAtNanos));
            ObjectNode result = toolResult("Backend 响应超过 MCP 允许的大小上限。", null, true);
            return resultResponse(request, id, result);
        } catch (RuntimeException exception) {
            audit(request, requestId, capability.id(), 502, elapsedMillis(startedAtNanos));
            ObjectNode result = toolResult("Backend 暂时无法处理 MCP 工具请求；请确认结果后再决定是否重试。", null, true);
            return resultResponse(request, id, result);
        }
    }

    private ResponseEntity<JsonNode> validateHeaders(HttpServletRequest request,
                                                     ObjectNode params,
                                                     String method,
                                                     JsonNode id) {
        String methodHeader = request.getHeader("Mcp-Method");
        if (methodHeader == null || !method.equals(methodHeader)) {
            return headerMismatch(request, id, "Mcp-Method does not match the JSON-RPC method");
        }
        String nameHeader = request.getHeader("Mcp-Name");
        if ("tools/call".equals(method)) {
            String bodyName = params.path("name").isTextual() ? params.path("name").asText() : null;
            String decodedName = decodeHeaderValue(nameHeader);
            if (bodyName == null || decodedName == null || !bodyName.equals(decodedName)) {
                return headerMismatch(request, id, "Mcp-Name does not match params.name");
            }
        } else if (nameHeader != null) {
            return headerMismatch(request, id, "Mcp-Name is only valid for tools/call");
        }
        String versionHeader = request.getHeader("MCP-Protocol-Version");
        JsonNode metadata = params.get("_meta");
        JsonNode versionInBody = metadata != null && metadata.isObject() ? metadata.get(PROTOCOL_VERSION_META) : null;
        if (versionHeader == null || versionInBody == null || !versionInBody.isTextual()
                || !versionHeader.equals(versionInBody.asText())) {
            return headerMismatch(request, id, "MCP-Protocol-Version does not match request metadata");
        }
        return null;
    }

    private ResponseEntity<JsonNode> headerMismatch(HttpServletRequest request, JsonNode id, String message) {
        return errorResponse(request, id, 400, -32020, message, null);
    }

    private String decodeHeaderValue(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String prefix = "=?base64?";
        String suffix = "?=";
        if (!headerValue.startsWith(prefix) || !headerValue.endsWith(suffix)) {
            return headerValue;
        }
        String encoded = headerValue.substring(prefix.length(), headerValue.length() - suffix.length());
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean acceptsRequiredResponseTypes(String acceptHeader) {
        if (acceptHeader == null) {
            return false;
        }
        try {
            List<MediaType> acceptedTypes = MediaType.parseMediaTypes(acceptHeader);
            return accepts(acceptedTypes, MediaType.APPLICATION_JSON)
                    && accepts(acceptedTypes, MediaType.TEXT_EVENT_STREAM);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean accepts(List<MediaType> acceptedTypes, MediaType required) {
        return acceptedTypes.stream().anyMatch(type -> type.getQualityValue() > 0 && type.isCompatibleWith(required));
    }

    private ObjectNode discoveryResult() {
        ObjectNode result = completeResult();
        ArrayNode versions = result.putArray("supportedVersions");
        versions.add(PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode metadata = result.putObject("_meta");
        metadata.set(SERVER_INFO_META, serverInfo());
        result.put("instructions", "按当前个人 API Token 暴露授权内的工单工具；每次调用都会由 Backend 重新执行认证与业务授权。MCP 不支持附件上传。");
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = completeResult();
        ArrayNode tools = result.putArray("tools");
        for (McpCapability capability : capabilityCatalog.currentCapabilities()) {
            ObjectNode tool = tools.addObject();
            tool.put("name", capability.id());
            tool.put("title", capability.id());
            tool.put("description", toolDescription(capability));
            tool.set("inputSchema", inputSchema(capability));
            ObjectNode annotations = tool.putObject("annotations");
            annotations.put("title", capability.description());
            annotations.put("readOnlyHint", capability.method().equals("GET"));
            annotations.put("destructiveHint", capability.highRisk());
            annotations.put("idempotentHint", false);
            annotations.put("openWorldHint", false);
        }
        result.putObject("_meta").set(SERVER_INFO_META, serverInfo());
        return result;
    }

    private String toolDescription(McpCapability capability) {
        StringBuilder description = new StringBuilder(capability.description())
                .append("。仅适用于当前 Token 被授予且资源可见的工单；Backend 会再次验证 allowedActions、组织范围、状态和幂等规则。");
        if (capability.highRisk()) {
            description.append("这是高风险操作，执行前应向用户清楚说明目标工单与提交内容并取得确认。");
        }
        if (!capability.requestBodyFields().isEmpty()) {
            description.append(" 请求体对应 Backend 字段契约：")
                    .append(String.join(", ", capability.requestBodyFields()))
                    .append("；具体字段由 Backend 校验。");
        }
        return description.toString();
    }

    private ObjectNode inputSchema(McpCapability capability) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        List<String> pathVariables = pathVariables(capability.path());
        if (!pathVariables.isEmpty()) {
            ObjectNode pathSchema = properties.putObject("pathVariables");
            pathSchema.put("type", "object");
            ObjectNode pathProperties = pathSchema.putObject("properties");
            ArrayNode pathRequired = pathSchema.putArray("required");
            for (String name : pathVariables) {
                pathProperties.putObject(name).put("type", "string");
                pathRequired.add(name);
            }
            pathSchema.put("additionalProperties", false);
            required.add("pathVariables");
        }

        if (!capability.queryParameters().isEmpty()) {
            ObjectNode querySchema = properties.putObject("query");
            querySchema.put("type", "object");
            ObjectNode queryProperties = querySchema.putObject("properties");
            for (String declared : capability.queryParameters()) {
                String name = queryInputName(declared);
                ObjectNode queryProperty = queryProperties.putObject(name);
                boolean array = declared.contains("[]");
                boolean integer = name.equals("page") || name.equals("size");
                if (array) {
                    queryProperty.put("type", "array");
                    queryProperty.putObject("items").put("type", "string");
                } else {
                    queryProperty.put("type", integer ? "integer" : "string");
                }
            }
            querySchema.put("additionalProperties", false);
        }

        if (!capability.requestBodyFields().isEmpty()) {
            ObjectNode bodySchema = properties.putObject("body");
            bodySchema.put("type", "object");
            bodySchema.put("description", "Backend 请求体；字段依照 description 中列出的 DTO/字段契约。MCP 不支持附件上传。");
            bodySchema.put("additionalProperties", true);
            required.add("body");
        }

        if (capability.requiresIdempotencyKey()) {
            ObjectNode keySchema = properties.putObject("idempotencyKey");
            keySchema.put("type", "string");
            keySchema.put("minLength", 1);
            keySchema.put("maxLength", 120);
            keySchema.put("description", "本次写操作的唯一幂等键；未知结果时不得换键重试。");
            required.add("idempotencyKey");
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private List<String> pathVariables(String path) {
        List<String> variables = new java.util.ArrayList<>();
        Matcher matcher = PATH_VARIABLE.matcher(path);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private String queryInputName(String declared) {
        return declared.replace("（可选）", "").replace("(可选)", "").replaceAll("\\[\\]$", "");
    }

    private ObjectNode toolResult(String text, JsonNode structuredContent, boolean isError) {
        ObjectNode result = completeResult();
        ArrayNode content = result.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", text == null || text.isBlank() ? "Backend 返回空结果。" : text);
        if (structuredContent != null && structuredContent.isObject()) {
            result.set("structuredContent", structuredContent);
        }
        result.put("isError", isError);
        result.putObject("_meta").set(SERVER_INFO_META, serverInfo());
        return result;
    }

    private ObjectNode completeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("resultType", "complete");
        return result;
    }

    private ObjectNode serverInfo() {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("name", "e-report");
        info.put("version", serverVersion);
        return info;
    }

    private ResponseEntity<JsonNode> resultResponse(HttpServletRequest request, JsonNode id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id.deepCopy());
        response.set("result", result);
        return response(request, response, HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> errorResponse(HttpServletRequest request,
                                                   JsonNode id,
                                                   int status,
                                                   int code,
                                                   String message,
                                                   JsonNode data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id.deepCopy());
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        return response(request, response, HttpStatus.valueOf(status));
    }

    private ResponseEntity<JsonNode> response(HttpServletRequest request, JsonNode body, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.getHeader("MCP-Protocol-Version") != null) {
            headers.set("MCP-Protocol-Version", PROTOCOL_VERSION);
        }
        return new ResponseEntity<>(body, headers, status);
    }

    private boolean containsOnlyArguments(JsonNode arguments) {
        var fields = arguments.fieldNames();
        while (fields.hasNext()) {
            if (!TOP_LEVEL_ARGUMENTS.contains(fields.next())) {
                return false;
            }
        }
        return true;
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches() ? requestId : UUID.randomUUID().toString();
    }

    private void audit(HttpServletRequest request,
                       String requestId,
                       String toolName,
                       int status,
                       long durationMillis) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        Object details = authentication == null ? null : authentication.getDetails();
        String membershipId = principal instanceof TenantUserDetails user ? user.membershipId().toString() : "unknown";
        String tokenId = details instanceof ApiTokenAuthenticationDetails token ? token.tokenId().toString() : "unknown";
        LOGGER.info("MCP tool call membershipId={} apiTokenId={} tool={} requestId={} status={} durationMs={}",
                membershipId, tokenId, sanitizeLogField(toolName), requestId, status, durationMillis);
    }

    private String sanitizeLogField(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }
}
