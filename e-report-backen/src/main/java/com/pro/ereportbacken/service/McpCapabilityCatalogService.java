package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.mcp.McpCapability;
import com.pro.ereportbacken.security.ApiTokenAuthenticationDetails;
import com.pro.ereportbacken.security.ApiTokenAuthenticationFilter;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.PermissionRiskLevel;
import com.pro.ereportbacken.security.SystemAuthority;
import com.pro.ereportbacken.security.TenantUserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpCapabilityCatalogService {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final List<CapabilityDefinition> CAPABILITIES = capabilities();

    public List<McpCapability> currentCapabilities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof TenantUserDetails principal)
                || !(authentication.getDetails() instanceof ApiTokenAuthenticationDetails)
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority(
                        ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY))) {
            throw new AccessDeniedException("MCP 只接受有效的个人 API Token。");
        }
        Set<String> permissions = principal.authorizationSnapshot().authorities(false);
        return CAPABILITIES.stream()
                .filter(CapabilityDefinition::mcpSupported)
                .filter(definition -> permissions.containsAll(definition.requiredAllPermissions()))
                .filter(definition -> definition.requiredAnyPermissions().isEmpty()
                        || definition.requiredAnyPermissions().stream().anyMatch(permissions::contains))
                .map(definition -> definition.toCapability(permissions))
                .toList();
    }

    public Optional<McpCapability> currentCapability(String id) {
        return currentCapabilities().stream().filter(capability -> capability.id().equals(id)).findFirst();
    }

    public List<McpCapability> capabilityCatalog() {
        Set<String> allPermissions = PermissionCatalog.names();
        return CAPABILITIES.stream()
                .filter(CapabilityDefinition::mcpSupported)
                .map(definition -> definition.toCapability(allPermissions))
                .toList();
    }

    private static List<CapabilityDefinition> capabilities() {
        List<CapabilityDefinition> definitions = new ArrayList<>();
        definitions.add(any("work-order.search", "GET", "/work-orders/search", "分页查询可见工单", false,
                PermissionCatalog.viewPermissions(), contract(
                        List.of("searchTerm", "status[]", "reportType[]", "queue[]", "reportDateFrom", "reportDateTo", "page", "size", "sort"),
                        List.of(), List.of("content", "page", "size", "totalElements", "totalPages"))));
        definitions.add(any("work-order.export", "GET", "/work-orders/export", "导出授权范围内的工单电子表格", true,
                PermissionCatalog.exportPermissions(), contract(
                        List.of("searchTerm", "status[]", "reportType[]", "queue[]", "reportDateFrom", "reportDateTo", "sort"),
                        List.of(), List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))));
        definitions.add(any("work-order.detail", "GET", "/work-orders/{id}", "读取一张可见工单详情和 allowedActions", false,
                PermissionCatalog.viewPermissions(), contract(
                        List.of("id"), List.of(), List.of("allowedActions", "status", "currentAssignment", "handler", "attachments"))));
        definitions.add(any("work-order.history", "GET", "/work-orders/{id}/events", "读取工单流转事件", false,
                PermissionCatalog.historyPermissions(), contract(
                        List.of("id", "page", "size"), List.of(), List.of("content", "page", "totalElements", "totalPages"))));
        definitions.add(any("work-order.responses", "GET", "/work-orders/{id}/responses", "读取工单回单记录", false,
                PermissionCatalog.historyPermissions(), contract(
                        List.of("id"), List.of(), List.of("回单历史列表"))));
        definitions.add(any("work-order.attachment", "GET", "/work-orders/attachments/{workOrderId}/file/{attachmentId}",
                "按附件标识下载有权访问的工单附件", false, PermissionCatalog.attachmentReadPermissions(), contract(
                        List.of("workOrderId", "attachmentId", "Range（可选）"), List.of(), List.of("二进制附件"))));
        definitions.add(any("work-order.report", "POST", "/work-orders", "提交一张对应类型的工单", true,
                PermissionCatalog.reportPermissions(), contract(
                        List.of(), List.of("CreateWorkOrderRequestDTO", "attachments"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.claim", "POST", "/work-orders/{id}/claims", "主动接单", true,
                PermissionCatalog.claimPermissions(), contract(
                        List.of("id"), List.of("version"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.respond", "POST", "/work-orders/{id}/responses", "提交工单回单", true,
                PermissionCatalog.responseSubmitPermissions(), contract(
                        List.of("id"), List.of("RespondWorkOrderRequestDTO", "attachments"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.assign", "POST", "/work-orders/{id}/assignments", "派单到具体处理人员", true,
                PermissionCatalog.assignmentCreatePermissions(), contract(
                        List.of("id"), List.of("AssignWorkOrderRequestDTO"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.reassign", "POST", "/work-orders/{id}/reassignments", "调整当前处理人员", true,
                PermissionCatalog.assignmentReassignPermissions(), contract(
                        List.of("id"), List.of("AssignWorkOrderRequestDTO"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.verify", "POST", "/work-orders/{id}/verifications", "验收通过或驳回回单", true,
                union(PermissionCatalog.verificationApprovePermissions(), PermissionCatalog.verificationRejectPermissions()), contract(
                        List.of("id"), List.of("VerifyWorkOrderRequestDTO"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.hold", "POST", "/work-orders/{id}/holds", "挂起工单", true,
                PermissionCatalog.holdPermissions(), contract(
                        List.of("id"), List.of("ReasonedWorkOrderCommandDTO"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.resume", "POST", "/work-orders/{id}/resumptions", "恢复工单", true,
                PermissionCatalog.resumePermissions(), contract(
                        List.of("id"), List.of("version"), List.of("WorkOrderResponseDTO"))));
        definitions.add(any("work-order.cancel", "POST", "/work-orders/{id}/cancellations", "撤回或取消工单", true,
                union(PermissionCatalog.cancelPermissions(), PermissionCatalog.withdrawPermissions()), contract(
                        List.of("id"), List.of("ReasonedWorkOrderCommandDTO"), List.of("WorkOrderResponseDTO"))));
        definitions.add(all("dashboard.statistics", "GET", "/work-orders/statistics", "读取授权范围内的仪表盘统计", false,
                contract(List.of("countyOrgUnitId（可选）"), List.of(), List.of("WorkOrderStatisticsDTO")),
                PermissionCatalog.DASHBOARD_STATISTICS_VIEW));
        definitions.add(all("user.list", "GET", "/admin/users", "分页查询授权组织范围内的用户", false,
                contract(List.of("page", "size", "sort", "keyword", "status", "orgUnitId"), List.of(), List.of("PageResponseDTO<UserResponseDTO>")),
                PermissionCatalog.USER_MANAGE_VIEW));
        definitions.add(all("organization.list", "GET", "/admin/org-units", "读取授权范围内的组织目录", false,
                contract(List.of(), List.of(), List.of("组织树")), PermissionCatalog.ORG_MANAGE_VIEW));
        definitions.add(all("authorization-profile.list", "GET", "/admin/authorization-profiles/summaries",
                "读取当前用户可见的岗位权限模板摘要", false,
                contract(List.of(), List.of(), List.of("岗位模板摘要")), PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW));
        definitions.add(all("user-export.create", "POST", "/admin/user-transfers/exports", "创建用户导出任务", true,
                contract(List.of(), List.of("UserTransferExportRequestDTO"), List.of("任务 ID 和状态")),
                PermissionCatalog.USER_EXPORT_CREATE));
        definitions.add(new CapabilityDefinition(
                "app-update.list", "GET", "/admin/app-updates", "读取安装包发布记录",
                Set.of(),
                Set.of(SystemAuthority.SUPER_ADMINISTRATOR, PermissionCatalog.APP_UPDATE_VIEW), false,
                CapabilityContract.empty()));
        definitions.add(all("system-config.list", "GET", "/admin/configs", "读取授权范围内的系统配置", false,
                contract(List.of("page", "size", "keyword"), List.of(), List.of("配置项（敏感值不返回原值）")),
                PermissionCatalog.SYSTEM_CONFIG_VIEW));
        definitions.add(all("notification-config.read", "GET", "/admin/notifications/settings", "读取通知渠道和计划", false,
                contract(List.of(), List.of(), List.of("通知渠道、计划和脱敏状态")),
                PermissionCatalog.NOTIFICATION_CONFIG_VIEW));
        definitions.add(all("notification-schedule.update", "PUT",
                "/admin/notifications/settings/daily-outstanding-report/schedule", "修改每日未处理工单汇总发送时间", true,
                contract(List.of(), List.of("schedule"), List.of("更新后的计划")),
                PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE));
        definitions.add(all("notification-sender.update", "PUT", "/admin/notifications/settings/sender",
                "配置当前作用域邮件发件方", true,
                contract(List.of(), List.of("sender configuration"), List.of("脱敏后的发件方状态")),
                PermissionCatalog.NOTIFICATION_SENDER_UPDATE));
        definitions.add(all("sla-policy.read", "GET", "/admin/sla-policies", "读取故障时限策略", false,
                contract(List.of(), List.of(), List.of("SLA policy")), PermissionCatalog.SLA_POLICY_VIEW));
        definitions.add(all("observability.events", "GET", "/admin/observability/events", "查询授权范围内的运行诊断", false,
                contract(List.of("page", "size", "severity", "signalType", "from", "to"), List.of(), List.of("运行诊断事件（已脱敏）")),
                PermissionCatalog.OBSERVABILITY_VIEW));
        return List.copyOf(definitions);
    }

    private static CapabilityDefinition any(String id,
                                            String method,
                                            String path,
                                            String description,
                                            boolean highRisk,
                                            Collection<String> permissions) {
        return any(id, method, path, description, highRisk, permissions, CapabilityContract.empty());
    }

    private static CapabilityDefinition any(String id,
                                            String method,
                                            String path,
                                            String description,
                                            boolean highRisk,
                                            Collection<String> permissions,
                                            CapabilityContract contract) {
        return new CapabilityDefinition(id, method, path, description, Set.of(), Set.copyOf(permissions), highRisk, contract);
    }

    private static CapabilityDefinition all(String id,
                                            String method,
                                            String path,
                                            String description,
                                            boolean highRisk,
                                            String... permissions) {
        return all(id, method, path, description, highRisk, CapabilityContract.empty(), permissions);
    }

    private static CapabilityDefinition all(String id,
                                            String method,
                                            String path,
                                            String description,
                                            boolean highRisk,
                                            CapabilityContract contract,
                                            String... permissions) {
        return new CapabilityDefinition(id, method, path, description, Set.of(permissions), Set.of(), highRisk, contract);
    }

    private static CapabilityContract contract(List<String> queryParameters,
                                               List<String> requestBodyFields,
                                               List<String> responseHints) {
        return new CapabilityContract(queryParameters, requestBodyFields, responseHints,
                List.of("400", "401", "403", "404", "409", "413", "422", "429"));
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... groups) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (Set<String> group : groups) {
            merged.addAll(group);
        }
        return Set.copyOf(merged);
    }

    private record CapabilityDefinition(
            String id,
            String method,
            String path,
            String description,
            Set<String> requiredAllPermissions,
            Set<String> requiredAnyPermissions,
            boolean highRisk,
            CapabilityContract contract
    ) {
        private McpCapability toCapability(Set<String> effectivePermissions) {
            LinkedHashSet<String> matchedPermissions = new LinkedHashSet<>(requiredAllPermissions);
            requiredAnyPermissions.stream()
                    .filter(effectivePermissions::contains)
                    .forEach(matchedPermissions::add);
            boolean effectiveHighRisk = highRisk || matchedPermissions.stream()
                    .map(PermissionCatalog::find)
                    .flatMap(java.util.Optional::stream)
                    .anyMatch(entry -> entry.riskLevel() == PermissionRiskLevel.HIGH
                            || entry.riskLevel() == PermissionRiskLevel.CRITICAL);
            boolean requiresIdempotencyKey = method.equals("POST") || method.equals("PUT") || method.equals("DELETE");
            return new McpCapability(
                    id,
                    method,
                    path,
                    description,
                    effectiveHighRisk,
                    queryParameters(),
                    contract.requestBodyFields(),
                    contract.errorStatuses(),
                    requiresIdempotencyKey);
        }

        private List<String> queryParameters() {
            Set<String> pathVariables = new LinkedHashSet<>();
            Matcher matcher = PATH_VARIABLE.matcher(path);
            while (matcher.find()) {
                pathVariables.add(matcher.group(1));
            }
            return contract.queryParameters().stream()
                    .filter(parameter -> !pathVariables.contains(parameter
                            .replace("（可选）", "")
                            .replace("(可选)", "")
                            .replaceAll("\\[\\]$", "")))
                    .toList();
        }

        private boolean apiTokenAccessible() {
            return path.equals("/work-orders")
                    || path.startsWith("/work-orders/");
        }

        private boolean mcpSupported() {
            return apiTokenAccessible()
                    && !path.contains("/attachments/")
                    && !path.equals("/work-orders/export");
        }
    }

    private record CapabilityContract(
            List<String> queryParameters,
            List<String> requestBodyFields,
            List<String> responseHints,
            List<String> errorStatuses
    ) {
        private CapabilityContract {
            queryParameters = List.copyOf(queryParameters);
            requestBodyFields = List.copyOf(requestBodyFields);
            responseHints = List.copyOf(responseHints);
            errorStatuses = List.copyOf(errorStatuses);
        }

        private static CapabilityContract empty() {
            return new CapabilityContract(List.of(), List.of(), List.of(),
                    List.of("401", "403", "429"));
        }
    }
}
