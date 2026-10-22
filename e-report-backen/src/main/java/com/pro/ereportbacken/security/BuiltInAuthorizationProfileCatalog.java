package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.WorkOrderDuty;
import com.pro.ereportbacken.model.pojo.WorkOrderType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuiltInAuthorizationProfileCatalog {

    public static final String COUNTY_NOTIFICATION_RECIPIENT = "PROFILE_COUNTY_NOTIFICATION_RECIPIENT";
    public static final String TENANT_NOTIFICATION_RECIPIENT = "PROFILE_TENANT_NOTIFICATION_RECIPIENT";
    public static final String USER_VIEWER = "PROFILE_USER_VIEWER";
    public static final String USER_IMPORTER = "PROFILE_USER_IMPORTER";
    public static final String USER_EXPORTER = "PROFILE_USER_EXPORTER";
    public static final String WORK_ORDER_EXPORTER = "PROFILE_WORK_ORDER_EXPORTER";
    public static final String USER_DETAILS_EDITOR = "PROFILE_USER_DETAILS_EDITOR";
    public static final String USER_ORGANIZATION_MANAGER = "PROFILE_USER_ORGANIZATION_MANAGER";
    public static final String USER_LIFECYCLE_MANAGER = "PROFILE_USER_LIFECYCLE_MANAGER";
    public static final String USER_CREDENTIAL_MANAGER = "PROFILE_USER_CREDENTIAL_MANAGER";
    public static final String USER_AUTHORIZATION_MANAGER = "PROFILE_USER_AUTHORIZATION_MANAGER";
    public static final String AUTHORIZATION_PROFILE_VIEWER = "PROFILE_AUTHORIZATION_PROFILE_VIEWER";
    public static final String AUTHORIZATION_PROFILE_EDITOR = "PROFILE_AUTHORIZATION_PROFILE_EDITOR";
    public static final String ORGANIZATION_VIEWER = "PROFILE_ORGANIZATION_VIEWER";
    public static final String ORGANIZATION_EDITOR = "PROFILE_ORGANIZATION_EDITOR";
    public static final String DEPARTMENT_VIEWER = "PROFILE_DEPARTMENT_VIEWER";
    public static final String DEPARTMENT_EDITOR = "PROFILE_DEPARTMENT_EDITOR";
    public static final String SYSTEM_CONFIG_VIEWER = "PROFILE_SYSTEM_CONFIG_VIEWER";
    public static final String SYSTEM_CONFIG_EDITOR = "PROFILE_SYSTEM_CONFIG_EDITOR";
    public static final String NOTIFICATION_CONFIG_VIEWER = "PROFILE_NOTIFICATION_CONFIG_VIEWER";
    public static final String NOTIFICATION_CONFIG_EDITOR = "PROFILE_NOTIFICATION_CONFIG_EDITOR";
    public static final String SLA_VIEWER = "PROFILE_SLA_VIEWER";
    public static final String SLA_EDITOR = "PROFILE_SLA_EDITOR";
    public static final String DASHBOARD_VIEWER = "PROFILE_DASHBOARD_VIEWER";
    public static final String OBSERVABILITY_VIEWER = "PROFILE_OBSERVABILITY_VIEWER";
    public static final String API_TOKEN_MANAGER = "PROFILE_API_TOKEN_MANAGER";
    public static final String TENANT_VIEWER = "PROFILE_TENANT_VIEWER";
    public static final String TENANT_PROVISIONER = "PROFILE_TENANT_PROVISIONER";
    public static final String TENANT_STATUS_MANAGER = "PROFILE_TENANT_STATUS_MANAGER";

    public static final String ALL_REPORTERS = "ALL_REPORTERS";
    public static final String ALL_WORKERS = "ALL_WORKERS";
    public static final String ALL_DISPATCHERS = "ALL_DISPATCHERS";
    public static final String ALL_VERIFIERS = "ALL_VERIFIERS";
    public static final String ALL_CONTROLLERS = "ALL_CONTROLLERS";
    public static final String ALL_AUDITORS = "ALL_AUDITORS";
    public static final String COUNTY_ADMINISTRATOR = "COUNTY_ADMINISTRATOR";
    public static final String TENANT_ADMINISTRATOR = "TENANT_ADMINISTRATOR";
    public static final String PLATFORM_ADMINISTRATOR = "PLATFORM_ADMINISTRATOR";

    private BuiltInAuthorizationProfileCatalog() {
    }

    public static List<Template> templates(boolean includePlatformTemplates) {
        Map<String, Template> atomic = atomicTemplates(includePlatformTemplates);
        List<Template> templates = new ArrayList<>(atomic.values());
        templates.add(composite(ALL_REPORTERS, "全类型提单员", "提交、跟踪并验收本人提交的全部类型工单。",
                workOrderProfileKeys(WorkOrderDuty.REPORTER), atomic, 1_000));
        templates.add(composite(ALL_WORKERS, "通用故障处理员",
                "领取、处理和回单网络故障与破损箱体更换工单；关箱处理由装维岗位单独授权。",
                generalWorkerProfileKeys(), atomic, 1_010));
        templates.add(composite(ALL_DISPATCHERS, "全类型调度员", "派发和调整全部类型工单。",
                workOrderProfileKeys(WorkOrderDuty.DISPATCHER), atomic, 1_020));
        templates.add(composite(ALL_VERIFIERS, "全类型验收员", "验收全部类型工单。",
                workOrderProfileKeys(WorkOrderDuty.VERIFIER), atomic, 1_030));
        templates.add(composite(ALL_CONTROLLERS, "全类型管控员", "执行全部类型工单的异常管控动作。",
                workOrderProfileKeys(WorkOrderDuty.CONTROLLER), atomic, 1_040));
        templates.add(composite(ALL_AUDITORS, "全类型审计员", "查看全部类型工单的完整审计信息。",
                workOrderProfileKeys(WorkOrderDuty.AUDITOR), atomic, 1_050));
        templates.add(composite(COUNTY_ADMINISTRATOR, "区县管理员标准岗位",
                "区县默认管理员的业务与治理权限清单。", countyAdministratorProfileKeys(), atomic, 2_000));
        templates.add(composite(TENANT_ADMINISTRATOR, "市公司管理员标准岗位",
                "市公司默认管理员的业务与治理权限清单。", tenantAdministratorProfileKeys(), atomic, 2_010));
        if (includePlatformTemplates) {
            templates.add(composite(PLATFORM_ADMINISTRATOR, "平台租户管理标准岗位",
                    "平台租户创建和生命周期管理权限清单。",
                    Set.of(TENANT_VIEWER, TENANT_PROVISIONER, TENANT_STATUS_MANAGER), atomic, 2_020));
        }
        return templates.stream()
                .sorted(Comparator.comparingInt(Template::sortOrder).thenComparing(Template::code))
                .toList();
    }

    private static Map<String, Template> atomicTemplates(boolean includePlatformTemplates) {
        Map<String, Template> templates = new LinkedHashMap<>();
        int[] order = {10};
        add(templates, profile(COUNTY_NOTIFICATION_RECIPIENT, "区县通知接收人",
                "接收所属区县工单通知和未闭环日报。", order[0]++,
                PermissionCatalog.NOTIFICATION_RECIPIENT_COUNTY));
        add(templates, profile(TENANT_NOTIFICATION_RECIPIENT, "市公司通知接收人",
                "接收市公司级工单通知。", order[0]++, PermissionCatalog.NOTIFICATION_RECIPIENT_TENANT));
        add(templates, profile(API_TOKEN_MANAGER, "API Token 管理员", "管理本人 API Token。", order[0]++,
                PermissionCatalog.API_TOKEN_VIEW, PermissionCatalog.API_TOKEN_CREATE, PermissionCatalog.API_TOKEN_REVOKE));
        addManagementProfiles(templates, order);
        for (PermissionCatalog.WorkOrderEntry entry : PermissionCatalog.workOrderEntries()) {
            addWorkOrderProfiles(templates, entry, order);
        }
        if (includePlatformTemplates) {
            add(templates, profile(TENANT_VIEWER, "市公司租户查看员", "查看平台内市公司租户。", order[0]++,
                    PermissionCatalog.TENANT_MANAGE_VIEW));
            add(templates, profile(TENANT_PROVISIONER, "市公司租户创建员", "创建市公司租户。", order[0]++,
                    PermissionCatalog.TENANT_MANAGE_VIEW, PermissionCatalog.TENANT_MANAGE_CREATE));
            add(templates, profile(TENANT_STATUS_MANAGER, "市公司租户状态管理员", "启用或停用市公司租户。", order[0]++,
                    PermissionCatalog.TENANT_MANAGE_VIEW, PermissionCatalog.TENANT_MANAGE_ACTIVATE,
                    PermissionCatalog.TENANT_MANAGE_DEACTIVATE));
        }
        return templates;
    }

    private static void addManagementProfiles(Map<String, Template> templates, int[] order) {
        addProfile(templates, order, USER_VIEWER, "用户查看员", "查看组织范围内的用户。",
                PermissionCatalog.USER_MANAGE_VIEW);
        addProfile(templates, order, USER_IMPORTER, "用户批量导入员", "下载模板、预览并执行用户导入任务。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_MANAGE_CREATE,
                PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD, PermissionCatalog.USER_IMPORT_PREVIEW,
                PermissionCatalog.USER_IMPORT_EXECUTE, PermissionCatalog.USER_IMPORT_CANCEL,
                PermissionCatalog.USER_IMPORT_TASK_VIEW, PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD);
        addProfile(templates, order, USER_EXPORTER, "用户数据导出员", "创建、查看和下载用户导出任务。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_EXPORT_CREATE,
                PermissionCatalog.USER_EXPORT_TASK_VIEW, PermissionCatalog.USER_EXPORT_CANCEL,
                PermissionCatalog.USER_EXPORT_DOWNLOAD);
        Set<String> workOrderExports = new LinkedHashSet<>();
        PermissionCatalog.workOrderEntries().forEach(entry -> {
            workOrderExports.add(entry.viewPermission());
            workOrderExports.add(entry.exportPermission());
        });
        add(templates, profile(WORK_ORDER_EXPORTER, "工单数据导出员", "查看并导出全部类型工单。",
                order[0]++, workOrderExports.toArray(String[]::new)));
        addProfile(templates, order, USER_DETAILS_EDITOR, "用户资料维护员", "维护用户基本资料和联系方式。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_MANAGE_UPDATE_DETAILS,
                PermissionCatalog.USER_MANAGE_CONTACT_UPDATE, PermissionCatalog.USER_MANAGE_DEPARTMENT_MOVE);
        addProfile(templates, order, USER_ORGANIZATION_MANAGER, "用户组织调整员", "调整用户所属组织。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_MANAGE_ORGANIZATION_MOVE,
                PermissionCatalog.USER_AUTHORIZATION_VIEW, PermissionCatalog.USER_AUTHORIZATION_ADJUST,
                PermissionCatalog.USER_AUTHORIZATION_REVOKE);
        addProfile(templates, order, USER_LIFECYCLE_MANAGER, "用户账号管理员", "创建、启停、删除和恢复用户。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_MANAGE_CREATE,
                PermissionCatalog.USER_MANAGE_STATUS_ACTIVATE, PermissionCatalog.USER_MANAGE_STATUS_DEACTIVATE,
                PermissionCatalog.USER_MANAGE_STATUS_LOCK, PermissionCatalog.USER_MANAGE_STATUS_UNLOCK,
                PermissionCatalog.USER_MANAGE_DELETE, PermissionCatalog.USER_MANAGE_RESTORE);
        addProfile(templates, order, USER_CREDENTIAL_MANAGER, "用户凭据管理员", "重置用户密码并治理会话和受信设备。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_MANAGE_RESET_PASSWORD,
                PermissionCatalog.USER_MANAGE_SESSION_VIEW, PermissionCatalog.USER_MANAGE_SESSION_REVOKE,
                PermissionCatalog.USER_MANAGE_TRUSTED_DEVICE_VIEW,
                PermissionCatalog.USER_MANAGE_TRUSTED_DEVICE_REVOKE);
        addProfile(templates, order, USER_AUTHORIZATION_MANAGER, "用户授权维护员", "逐项管理用户 Grant。",
                PermissionCatalog.USER_MANAGE_VIEW, PermissionCatalog.USER_AUTHORIZATION_VIEW,
                PermissionCatalog.USER_AUTHORIZATION_GRANT, PermissionCatalog.USER_AUTHORIZATION_ADJUST,
                PermissionCatalog.USER_AUTHORIZATION_REVOKE, PermissionCatalog.USER_AUTHORIZATION_REATTACH,
                PermissionCatalog.USER_AUTHORIZATION_GRANT_OPTION_ISSUE,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW, PermissionCatalog.AUTHORIZATION_TEMPLATE_APPLY);
        addProfile(templates, order, AUTHORIZATION_PROFILE_VIEWER, "岗位模板查看员", "查看岗位模板和权限目录。",
                PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW);
        addProfile(templates, order, AUTHORIZATION_PROFILE_EDITOR, "岗位模板编辑员", "维护岗位模板。",
                PermissionCatalog.AUTHORIZATION_TEMPLATE_VIEW, PermissionCatalog.AUTHORIZATION_TEMPLATE_CREATE,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_CLONE, PermissionCatalog.AUTHORIZATION_TEMPLATE_UPDATE,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_ENABLE, PermissionCatalog.AUTHORIZATION_TEMPLATE_DISABLE,
                PermissionCatalog.AUTHORIZATION_TEMPLATE_DELETE, PermissionCatalog.AUTHORIZATION_TEMPLATE_APPLY);
        addProfile(templates, order, ORGANIZATION_VIEWER, "组织查看员", "查看组织目录。",
                PermissionCatalog.ORG_MANAGE_VIEW);
        addProfile(templates, order, ORGANIZATION_EDITOR, "组织编辑员", "维护组织目录。",
                PermissionCatalog.ORG_MANAGE_VIEW, PermissionCatalog.ORG_MANAGE_CREATE,
                PermissionCatalog.ORG_MANAGE_UPDATE, PermissionCatalog.ORG_MANAGE_MOVE,
                PermissionCatalog.ORG_MANAGE_ENABLE, PermissionCatalog.ORG_MANAGE_DISABLE,
                PermissionCatalog.ORG_MANAGE_MERGE, PermissionCatalog.ORG_MANAGE_DELETE);
        addProfile(templates, order, DEPARTMENT_VIEWER, "部门查看员", "查看所属组织范围内的部门树。",
                PermissionCatalog.DEPARTMENT_VIEW);
        addProfile(templates, order, DEPARTMENT_EDITOR, "部门维护员", "维护所属组织范围内的部门树。",
                PermissionCatalog.DEPARTMENT_VIEW, PermissionCatalog.DEPARTMENT_CREATE,
                PermissionCatalog.DEPARTMENT_UPDATE, PermissionCatalog.DEPARTMENT_MOVE,
                PermissionCatalog.DEPARTMENT_ENABLE, PermissionCatalog.DEPARTMENT_DISABLE,
                PermissionCatalog.DEPARTMENT_MERGE, PermissionCatalog.DEPARTMENT_DELETE);
        addProfile(templates, order, SYSTEM_CONFIG_VIEWER, "系统配置查看员", "查看系统配置。",
                PermissionCatalog.SYSTEM_CONFIG_VIEW);
        addProfile(templates, order, SYSTEM_CONFIG_EDITOR, "系统配置编辑员", "维护系统配置。",
                PermissionCatalog.SYSTEM_CONFIG_VIEW, PermissionCatalog.SYSTEM_CONFIG_CREATE,
                PermissionCatalog.SYSTEM_CONFIG_UPDATE, PermissionCatalog.SYSTEM_CONFIG_DELETE);
        addProfile(templates, order, NOTIFICATION_CONFIG_VIEWER, "通知配置查看员", "查看通知配置。",
                PermissionCatalog.NOTIFICATION_CONFIG_VIEW);
        addProfile(templates, order, NOTIFICATION_CONFIG_EDITOR, "通知配置编辑员", "维护通知配置。",
                PermissionCatalog.NOTIFICATION_CONFIG_VIEW, PermissionCatalog.NOTIFICATION_CHANNEL_UPDATE,
                PermissionCatalog.NOTIFICATION_SCHEDULE_UPDATE, PermissionCatalog.NOTIFICATION_SENDER_UPDATE);
        addProfile(templates, order, SLA_VIEWER, "时限查看员", "查看工单 SLA。",
                PermissionCatalog.SLA_POLICY_VIEW);
        addProfile(templates, order, SLA_EDITOR, "时限编辑员", "查看和修改工单 SLA。",
                PermissionCatalog.SLA_POLICY_VIEW, PermissionCatalog.SLA_POLICY_UPDATE);
        addProfile(templates, order, DASHBOARD_VIEWER, "工单仪表盘查看员", "查看组织范围内工单统计。",
                PermissionCatalog.DASHBOARD_STATISTICS_VIEW);
        addProfile(templates, order, OBSERVABILITY_VIEWER, "运行诊断查看员", "查看运行诊断和 Redis 状态。",
                PermissionCatalog.OBSERVABILITY_VIEW, PermissionCatalog.REDIS_DIAGNOSTICS_VIEW);
    }

    private static void addWorkOrderProfiles(Map<String, Template> templates,
                                             PermissionCatalog.WorkOrderEntry entry,
                                             int[] order) {
        String typeName = entry.type().getDisplayName();
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.REPORTER), typeName + "提单员",
                "提交、跟踪并验收本人提交的" + typeName + "。", order[0]++,
                entry.reportPermission(), entry.viewPermission(), entry.historyPermission(),
                entry.attachmentReadPermission(), entry.verificationApprovePermission(),
                entry.verificationRejectPermission(), entry.withdrawPermission()));
        String workerDisplayName = entry.type() == WorkOrderType.BOX_DOOR_OPEN
                ? "装维关箱处理员"
                : typeName + "处理员";
        String workerDescription = entry.type() == WorkOrderType.BOX_DOOR_OPEN
                ? "由装维人员领取、关闭箱门、挂起并提交关箱回单。"
                : "领取、处理、挂起并回单" + typeName + "。";
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.WORKER), workerDisplayName,
                workerDescription, order[0]++,
                entry.viewPermission(), entry.historyPermission(), entry.attachmentReadPermission(),
                entry.claimPermission(), entry.assignmentAcceptPermission(), entry.assignmentRejectPermission(),
                entry.responseSubmitPermission(), entry.holdPermission(), entry.resumePermission()));
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.DISPATCHER), typeName + "调度员",
                "派发和调整" + typeName + "。", order[0]++, entry.viewPermission(), entry.historyPermission(),
                entry.attachmentReadPermission(), entry.assignmentCreatePermission(), entry.assignmentReassignPermission()));
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.VERIFIER), typeName + "验收员",
                "验收" + typeName + "回单。", order[0]++, entry.viewPermission(), entry.historyPermission(),
                entry.attachmentReadPermission(), entry.verificationApprovePermission(), entry.verificationRejectPermission()));
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.CONTROLLER), typeName + "管控员",
                "执行" + typeName + "异常管控动作。", order[0]++, entry.viewPermission(), entry.historyPermission(),
                entry.attachmentReadPermission(), entry.cancelPermission(),
                entry.verificationSelfApprovePermission(), entry.verificationSelfRejectPermission()));
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.TRANSFER_COORDINATOR),
                typeName + "移交协调员", "发起、接收、拒绝或撤销" + typeName + "跨组织完整移交。", order[0]++,
                entry.viewPermission(), entry.historyPermission(), entry.attachmentReadPermission(),
                entry.transferCreatePermission(), entry.transferAcceptPermission(),
                entry.transferRejectPermission(), entry.transferCancelPermission()));
        add(templates, profile(workOrderProfileKey(entry.type(), WorkOrderDuty.AUDITOR), typeName + "审计员",
                "查看" + typeName + "完整审计信息。", order[0]++, entry.viewPermission(), entry.historyPermission(),
                entry.attachmentReadPermission(), entry.auditPermission()));
    }

    public static String workOrderProfileKey(WorkOrderType type, WorkOrderDuty duty) {
        return "PROFILE_" + type.name() + "_" + duty.name();
    }

    public static Set<String> administratorComponentProfileKeys(String administratorProfileSystemKey) {
        return switch (administratorProfileSystemKey) {
            case TENANT_ADMINISTRATOR -> tenantAdministratorProfileKeys();
            case COUNTY_ADMINISTRATOR -> countyAdministratorProfileKeys();
            default -> throw new IllegalArgumentException("不是默认管理员岗位模板：" + administratorProfileSystemKey);
        };
    }

    private static Set<String> tenantAdministratorProfileKeys() {
        Set<String> keys = new LinkedHashSet<>(Set.of(
                COUNTY_NOTIFICATION_RECIPIENT, TENANT_NOTIFICATION_RECIPIENT, WORK_ORDER_EXPORTER,
                USER_VIEWER, USER_IMPORTER, USER_EXPORTER, USER_DETAILS_EDITOR,
                USER_ORGANIZATION_MANAGER, USER_LIFECYCLE_MANAGER, USER_CREDENTIAL_MANAGER,
                USER_AUTHORIZATION_MANAGER, AUTHORIZATION_PROFILE_EDITOR, ORGANIZATION_EDITOR, DEPARTMENT_EDITOR,
                SYSTEM_CONFIG_EDITOR, NOTIFICATION_CONFIG_EDITOR, SLA_EDITOR,
                DASHBOARD_VIEWER, OBSERVABILITY_VIEWER, API_TOKEN_MANAGER));
        keys.addAll(allWorkOrderProfileKeys());
        return Set.copyOf(keys);
    }

    private static Set<String> countyAdministratorProfileKeys() {
        Set<String> keys = new LinkedHashSet<>(tenantAdministratorProfileKeys());
        keys.remove(TENANT_NOTIFICATION_RECIPIENT);
        keys.remove(SLA_EDITOR);
        keys.add(SLA_VIEWER);
        return Set.copyOf(keys);
    }

    private static Set<String> workOrderProfileKeys(WorkOrderDuty duty) {
        Set<String> keys = new LinkedHashSet<>();
        for (WorkOrderType type : WorkOrderType.values()) {
            keys.add(workOrderProfileKey(type, duty));
        }
        return Set.copyOf(keys);
    }

    private static Set<String> generalWorkerProfileKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (WorkOrderType type : WorkOrderType.values()) {
            if (type != WorkOrderType.BOX_DOOR_OPEN) {
                keys.add(workOrderProfileKey(type, WorkOrderDuty.WORKER));
            }
        }
        return Set.copyOf(keys);
    }

    private static Set<String> allWorkOrderProfileKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (WorkOrderDuty duty : WorkOrderDuty.values()) {
            keys.addAll(workOrderProfileKeys(duty));
        }
        return Set.copyOf(keys);
    }

    private static Template composite(String key,
                                      String displayName,
                                      String description,
                                      Set<String> componentKeys,
                                      Map<String, Template> templates,
                                      int sortOrder) {
        Set<String> permissionNames = new LinkedHashSet<>();
        for (String componentKey : componentKeys) {
            Template component = templates.get(componentKey);
            if (component == null) {
                throw new IllegalStateException("内置岗位模板引用不存在的模板：" + componentKey);
            }
            component.rules().forEach(rule -> permissionNames.add(rule.permissionName()));
        }
        return profile(key, displayName, description, sortOrder, permissionNames.toArray(String[]::new));
    }

    private static Template profile(String key,
                                    String displayName,
                                    String description,
                                    int sortOrder,
                                    String... permissionNames) {
        List<Rule> rules = java.util.Arrays.stream(permissionNames)
                .peek(PermissionCatalog::require)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(Rule::new)
                .toList();
        return new Template(key, key, displayName, description, true, rules, sortOrder);
    }

    private static void addProfile(Map<String, Template> templates,
                                   int[] order,
                                   String key,
                                   String displayName,
                                   String description,
                                   String... permissionNames) {
        add(templates, profile(key, displayName, description, order[0]++, permissionNames));
    }

    private static void add(Map<String, Template> templates, Template template) {
        if (templates.put(template.systemKey(), template) != null) {
            throw new IllegalStateException("内置岗位模板编码重复：" + template.systemKey());
        }
    }

    public record Template(
            String code,
            String systemKey,
            String displayName,
            String description,
            boolean builtIn,
            List<Rule> rules,
            int sortOrder
    ) {
        public Template {
            rules = List.copyOf(rules);
        }
    }

    public record Rule(String permissionName) {
    }
}
