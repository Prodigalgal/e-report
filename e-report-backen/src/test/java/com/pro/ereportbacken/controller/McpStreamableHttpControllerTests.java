package com.pro.ereportbacken.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.security.ApiTokenAuthenticationDetails;
import com.pro.ereportbacken.security.ApiTokenAuthenticationFilter;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import com.pro.ereportbacken.service.McpBackendApiClient;
import com.pro.ereportbacken.service.McpCapabilityCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpStreamableHttpControllerTests {

    private static final String TOKEN = "Bearer e-report-personal-token";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exchangesProtocolJsonThroughTheProductionHttpConverters() throws Exception {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);
        var response = exchangeJson(objectMapper.writeValueAsBytes(message("tools/list", null, null)));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader("MCP-Protocol-Version")).isEqualTo("2026-07-28");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(body.path("result").path("tools").findValuesAsText("name"))
                .containsExactlyInAnyOrder("work-order.search", "work-order.detail");
    }

    @Test
    void rejectsEmptyMalformedAndTrailingJsonThroughHttp() throws Exception {
        for (String rawMessage : List.of("", "{", "{\"jsonrpc\":\"2.0\"} {}")) {
            var response = exchangeJson(rawMessage.getBytes(StandardCharsets.UTF_8));
            assertThat(response.getStatus()).isEqualTo(400);
            JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
            assertThat(body.path("error").path("code").asInt()).isEqualTo(-32700);
            assertThat(body.path("id").isNull()).isTrue();
        }
    }

    @Test
    void enforcesTheHttpByteLimitIncludingWhitespace() throws Exception {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);
        String message = objectMapper.writeValueAsString(message("tools/list", null, null));
        String atLimit = message + " ".repeat(1_048_576 - message.getBytes(StandardCharsets.UTF_8).length);
        assertThat(exchangeJson(atLimit.getBytes(StandardCharsets.UTF_8)).getStatus()).isEqualTo(200);

        var response = exchangeJson((atLimit + " ").getBytes(StandardCharsets.UTF_8));
        assertThat(response.getStatus()).isEqualTo(413);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(body.path("id").isNull()).isTrue();
    }

    private MockHttpServletResponse exchangeJson(byte[] content) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller(new RestTemplate()))
                .setMessageConverters(new ByteArrayHttpMessageConverter(), new JacksonJsonHttpMessageConverter())
                .build();
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2026-07-28")
                .header("Mcp-Method", "tools/list")
                .content(content)).andReturn().getResponse();
    }

    @Test
    void discoveryAndToolListReturnCurrentProtocolAndOnlyAuthorizedMcpTools() throws Exception {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);
        McpStreamableHttpController controller = controller(new RestTemplate());

        var discovery = controller.processMessage(message("server/discover", null, null), request("server/discover", null));
        var tools = controller.processMessage(message("tools/list", null, null), request("tools/list", null));

        assertThat(discovery.getBody().path("result").path("resultType").asText()).isEqualTo("complete");
        assertThat(discovery.getBody().path("result").path("supportedVersions").get(0).asText())
                .isEqualTo("2026-07-28");
        assertThat(tools.getBody().path("result").path("tools").findValuesAsText("name"))
                .containsExactlyInAnyOrder("work-order.search", "work-order.detail");
        assertThat(tools.getBody().path("result").path("tools").findValuesAsText("name"))
                .doesNotContain("work-order.export", "work-order.attachment", "user.list");
        JsonNode detailTool = findTool(tools.getBody().path("result").path("tools"), "work-order.detail");
        assertThat(detailTool.path("inputSchema").path("properties").path("pathVariables").path("required")
                .get(0).asText()).isEqualTo("id");
        assertThat(detailTool.path("inputSchema").path("properties").path("query").path("properties").has("id"))
                .isFalse();
    }

    @Test
    void rejectsHeaderBodyMismatchWithProtocolError() throws Exception {
        authenticateWithPermissions(PermissionCatalog.NETWORK_FAULT_VIEW);
        McpStreamableHttpController controller = controller(new RestTemplate());
        MockHttpServletRequest request = request("tools/list", null);
        request.removeHeader("Mcp-Method");
        request.addHeader("Mcp-Method", "tools/call");

        var response = controller.processMessage(message("tools/list", null, null), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().path("error").path("code").asInt()).isEqualTo(-32020);
    }

    @Test
    void rechecksCurrentPermissionsAndDoesNotForwardARevokedToolCall() throws Exception {
        authenticateWithPermissions();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        McpStreamableHttpController controller = controller(restTemplate);

        var response = controller.processMessage(
                message("tools/call", "work-order.search", objectMapper.createObjectNode()),
                request("tools/call", "work-order.search"));

        mockServer.verify();
        assertThat(response.getBody().path("result").path("isError").asBoolean()).isTrue();
        assertThat(response.getBody().path("result").path("content").get(0).path("text").asText())
                .contains("无权使用");
    }

    @Test
    void forwardsAuthorizedWriteWithBearerAndIdempotencyKey() throws Exception {
        String claimPermission = PermissionCatalog.claimPermissions().iterator().next();
        authenticateWithPermissions(claimPermission);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        UUID workOrderId = UUID.randomUUID();
        mockServer.expect(requestTo("http://127.0.0.1:8083/api/work-orders/" + workOrderId + "/claims"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", TOKEN))
                .andExpect(header("Idempotency-Key", "mcp-claim-001"))
                .andExpect(content().json("{\"version\":7}"))
                .andRespond(withSuccess("{\"status\":\"CLAIMED\"}", MediaType.APPLICATION_JSON));
        McpStreamableHttpController controller = controller(restTemplate);

        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.putObject("pathVariables").put("id", workOrderId.toString());
        arguments.putObject("body").put("version", 7);
        arguments.put("idempotencyKey", "mcp-claim-001");
        var response = controller.processMessage(
                message("tools/call", "work-order.claim", arguments),
                request("tools/call", "work-order.claim"));

        mockServer.verify();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.getBody().path("result").path("structuredContent").path("status").asText())
                .isEqualTo("CLAIMED");
    }

    @Test
    void rejectsMcpAttachmentPayloadBeforeBackendDispatch() throws Exception {
        authenticateWithPermissions(PermissionCatalog.reportPermissions().iterator().next());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        McpStreamableHttpController controller = controller(restTemplate);

        ObjectNode arguments = objectMapper.createObjectNode();
        ObjectNode body = arguments.putObject("body");
        body.putArray("attachments").addObject().put("fileName", "secret.pdf");
        arguments.put("idempotencyKey", "mcp-report-001");
        var response = controller.processMessage(
                message("tools/call", "work-order.report", arguments),
                request("tools/call", "work-order.report"));

        mockServer.verify();
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(response.getBody().path("error").path("message").asText()).contains("附件");
    }

    @Test
    void rejectsPathTraversalBeforeBackendDispatch() throws Exception {
        authenticateWithPermissions(PermissionCatalog.claimPermissions().iterator().next());
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        McpStreamableHttpController controller = controller(restTemplate);

        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.putObject("pathVariables").put("id", "../../admin/users");
        arguments.putObject("body").put("version", 1);
        arguments.put("idempotencyKey", "mcp-claim-002");
        var response = controller.processMessage(
                message("tools/call", "work-order.claim", arguments),
                request("tools/call", "work-order.claim"));

        mockServer.verify();
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(response.getBody().path("error").path("message").asText()).contains("路径段");
    }

    private McpStreamableHttpController controller(RestTemplate restTemplate) {
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return new McpStreamableHttpController(
                objectMapper,
                new McpCapabilityCatalogService(),
                new McpBackendApiClient(restTemplate, objectMapper),
                "test");
    }

    private ObjectNode message(String method, String toolName, JsonNode arguments) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        ObjectNode params = message.putObject("params");
        if (toolName != null) {
            params.put("name", toolName);
            if (arguments != null) {
                params.set("arguments", arguments);
            }
        }
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        return message;
    }

    private MockHttpServletRequest request(String method, String toolName) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.setContextPath("/api");
        request.setServletPath("/mcp");
        request.setLocalAddr("127.0.0.1");
        request.setLocalPort(8083);
        request.addHeader("Authorization", TOKEN);
        request.addHeader("Accept", "application/json, text/event-stream");
        request.addHeader("MCP-Protocol-Version", "2026-07-28");
        request.addHeader("Mcp-Method", method);
        if (toolName != null) {
            request.addHeader("Mcp-Name", toolName);
        }
        return request;
    }

    private JsonNode findTool(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        return objectMapper.nullNode();
    }

    private void authenticateWithPermissions(String... permissions) {
        UUID membershipId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UserAuthorizationSnapshot.EffectivePermissionGrant> grants = java.util.Arrays.stream(permissions)
                .map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        UUID.randomUUID(), permission, false, null, null))
                .toList();
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                membershipId,
                tenantId,
                UUID.randomUUID(),
                3L,
                "mcp-test-user",
                null,
                UserStatus.ACTIVE,
                grants);
        TenantUserDetails principal = new TenantUserDetails(
                membershipId,
                tenantId,
                snapshot.orgUnitId(),
                snapshot.authorizationVersion(),
                snapshot.username(),
                "",
                true,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList(),
                snapshot);
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY));
        java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
        authentication.setDetails(new ApiTokenAuthenticationDetails(UUID.randomUUID(), "test MCP token", null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
