package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pro.ereportbacken.model.mcp.McpCapability;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpBackendApiClient {

    private static final int MAX_REQUEST_BODY_BYTES = 1_048_576;
    private static final int MAX_RESPONSE_BODY_BYTES = 1_048_576;
    private static final int MAX_QUERY_VALUES = 50;
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
    private static final Pattern SAFE_IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,120}");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public McpBackendApiClient(@Qualifier("mcpRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public BackendCallResult invoke(McpCapability capability,
                                    JsonNode rawArguments,
                                    HttpServletRequest inboundRequest,
                                    String requestId) {
        ObjectNode arguments = requireObject(rawArguments, "arguments");
        requireOnlyFields(arguments, Set.of("pathVariables", "query", "body", "idempotencyKey"), "arguments");

        String path = resolvePath(capability.path(), arguments.get("pathVariables"));
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(inboundRequest.getLocalAddr())
                .port(inboundRequest.getLocalPort())
                .path(inboundRequest.getContextPath())
                .path(path);
        appendQuery(uriBuilder, capability.queryParameters(), arguments.get("query"));
        URI uri = uriBuilder.build().encode(StandardCharsets.UTF_8).toUri();

        JsonNode body = arguments.get("body");
        byte[] requestBody = serializeBody(capability, body);
        String idempotencyKey = idempotencyKey(capability, arguments.get("idempotencyKey"));
        String authorization = inboundRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidMcpArgumentsException("MCP 请求缺少有效的 Bearer Token。");
        }

        HttpMethod method = HttpMethod.valueOf(capability.method());
        return restTemplate.execute(uri, method, request -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
            if (requestId != null && requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
                request.getHeaders().set("X-Request-Id", requestId);
            }
            if (idempotencyKey != null) {
                request.getHeaders().set("Idempotency-Key", idempotencyKey);
            }
            if (requestBody.length > 0) {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                StreamUtils.copy(requestBody, request.getBody());
            }
        }, response -> {
            byte[] responseBody = response.getBody().readNBytes(MAX_RESPONSE_BODY_BYTES + 1);
            if (responseBody.length > MAX_RESPONSE_BODY_BYTES) {
                throw new ResponseBodyTooLargeException();
            }
            MediaType responseContentType = response.getHeaders().getContentType();
            String text = new String(responseBody, StandardCharsets.UTF_8);
            JsonNode structuredContent = null;
            if (responseContentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(responseContentType)
                    && responseBody.length > 0) {
                try {
                    JsonNode parsed = objectMapper.readTree(responseBody);
                    if (parsed != null && parsed.isObject()) {
                        structuredContent = parsed;
                    }
                    text = objectMapper.writeValueAsString(parsed);
                } catch (IOException ignored) {
                    structuredContent = null;
                }
            } else if (responseContentType != null && !responseContentType.isCompatibleWith(MediaType.TEXT_PLAIN)) {
                text = "Backend 返回了 MCP 不支持的内容类型。";
            }
            return new BackendCallResult(response.getStatusCode().value(), text, structuredContent);
        });
    }

    private String resolvePath(String template, JsonNode rawPathVariables) {
        ObjectNode pathVariables = rawPathVariables == null
                ? objectMapper.createObjectNode()
                : requireObject(rawPathVariables, "pathVariables");
        Matcher matcher = PATH_VARIABLE.matcher(template);
        StringBuilder path = new StringBuilder();
        Set<String> expected = new java.util.LinkedHashSet<>();
        int previousEnd = 0;
        while (matcher.find()) {
            path.append(template, previousEnd, matcher.start());
            String variableName = matcher.group(1);
            expected.add(variableName);
            JsonNode value = pathVariables.get(variableName);
            if (value == null || !value.isTextual() || !SAFE_PATH_SEGMENT.matcher(value.textValue()).matches()) {
                throw new InvalidMcpArgumentsException("pathVariables." + variableName + " 必须是安全的单一路径段。");
            }
            path.append(value.textValue());
            previousEnd = matcher.end();
        }
        path.append(template.substring(previousEnd));
        requireOnlyFields(pathVariables, expected, "pathVariables");
        return path.toString();
    }

    private void appendQuery(UriComponentsBuilder uriBuilder, List<String> declaredParameters, JsonNode rawQuery) {
        ObjectNode query = rawQuery == null ? objectMapper.createObjectNode() : requireObject(rawQuery, "query");
        Map<String, String> wireNames = new LinkedHashMap<>();
        Set<String> arrayNames = new java.util.LinkedHashSet<>();
        for (String declared : declaredParameters) {
            String name = declared.replace("（可选）", "").replace("(可选)", "");
            boolean array = name.endsWith("[]");
            String inputName = array ? name.substring(0, name.length() - 2) : name;
            wireNames.put(inputName, inputName);
            if (array) {
                arrayNames.add(inputName);
            }
        }
        requireOnlyFields(query, wireNames.keySet(), "query");
        int queryValueCount = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = query.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            List<JsonNode> values = queryValues(field.getValue(), arrayNames.contains(field.getKey()), field.getKey());
            for (JsonNode value : values) {
                if (value.isNull()) {
                    continue;
                }
                if (!value.isValueNode() || value.isBinary()) {
                    throw new InvalidMcpArgumentsException("query." + field.getKey() + " 只接受字符串、数字或布尔值。");
                }
                if (++queryValueCount > MAX_QUERY_VALUES) {
                    throw new InvalidMcpArgumentsException("query 参数项不能超过 " + MAX_QUERY_VALUES + " 个。");
                }
                uriBuilder.queryParam(wireNames.get(field.getKey()), value.asText());
            }
        }
    }

    private List<JsonNode> queryValues(JsonNode value, boolean arrayExpected, String name) {
        if (value.isArray()) {
            if (!arrayExpected) {
                throw new InvalidMcpArgumentsException("query." + name + " 不接受数组。");
            }
            List<JsonNode> values = new ArrayList<>();
            value.forEach(values::add);
            return values;
        }
        if (arrayExpected && !value.isNull()) {
            throw new InvalidMcpArgumentsException("query." + name + " 必须是数组。");
        }
        return List.of(value);
    }

    private byte[] serializeBody(McpCapability capability, JsonNode body) {
        boolean bodyRequired = !capability.requestBodyFields().isEmpty();
        if (!bodyRequired) {
            if (body != null) {
                throw new InvalidMcpArgumentsException("此工具不接受 body 参数。");
            }
            return new byte[0];
        }
        ObjectNode objectBody = requireObject(body, "body");
        JsonNode attachments = objectBody.get("attachments");
        if (attachments != null && !attachments.isNull()
                && (!attachments.isArray() || !attachments.isEmpty())) {
            throw new InvalidMcpArgumentsException("MCP 当前不支持上传或关联工单附件。");
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(objectBody);
            if (bytes.length > MAX_REQUEST_BODY_BYTES) {
                throw new InvalidMcpArgumentsException("body 不能超过 1 MiB。");
            }
            return bytes;
        } catch (IOException exception) {
            throw new InvalidMcpArgumentsException("body 不是有效的 JSON 对象。");
        }
    }

    private String idempotencyKey(McpCapability capability, JsonNode rawKey) {
        if (!capability.requiresIdempotencyKey()) {
            if (rawKey != null) {
                throw new InvalidMcpArgumentsException("此只读工具不接受 idempotencyKey 参数。");
            }
            return null;
        }
        if (rawKey == null || !rawKey.isTextual()
                || !SAFE_IDEMPOTENCY_KEY.matcher(rawKey.textValue()).matches()) {
            throw new InvalidMcpArgumentsException("写操作必须提供 1 到 120 位字母、数字或 . _ : - 组成的 idempotencyKey。");
        }
        return rawKey.textValue();
    }

    private ObjectNode requireObject(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) {
            throw new InvalidMcpArgumentsException(fieldName + " 必须是 JSON 对象。");
        }
        return (ObjectNode) node;
    }

    private void requireOnlyFields(ObjectNode object, Set<String> allowed, String fieldName) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new InvalidMcpArgumentsException(fieldName + " 包含不支持的字段：" + name + "。");
            }
        }
    }

    public record BackendCallResult(int status, String text, JsonNode structuredContent) {
        public boolean isError() {
            return status < 200 || status >= 300;
        }
    }

    public static class InvalidMcpArgumentsException extends RuntimeException {
        public InvalidMcpArgumentsException(String message) {
            super(message);
        }
    }

    public static class ResponseBodyTooLargeException extends RuntimeException {
    }
}
