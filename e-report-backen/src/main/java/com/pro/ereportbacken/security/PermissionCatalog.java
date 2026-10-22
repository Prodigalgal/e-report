package com.pro.ereportbacken.security;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.AccessMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PermissionCatalog {

    public static final String DASHBOARD_STATISTICS_VIEW = "dashboard:statistics:view";

    public static final String SELF_PROFILE_VIEW = "self-profile:view";
    public static final String SELF_PROFILE_UPDATE = "self-profile:update";
    public static final String SELF_PASSWORD_CHANGE = "self-password:change";
    public static final String SELF_SESSION_VIEW = "self-session:view";
    public static final String SELF_SESSION_REVOKE = "self-session:revoke";

    public static final String NETWORK_FAULT_VIEW = "work-order:network-fault:view";
    public static final String NETWORK_FAULT_ATTACHMENT_READ = "work-order:network-fault:attachment:read";
    public static final String BOX_DOOR_OPEN_VIEW = "work-order:box-door-open:view";

    public static final String USER_MANAGE_VIEW = "user:manage:view";
    public static final String USER_MANAGE_CREATE = "user:manage:create";
    public static final String USER_MANAGE_UPDATE_DETAILS = "user:manage:details:update";
    public static final String USER_MANAGE_CONTACT_UPDATE = "user:manage:contact:update";
    public static final String USER_MANAGE_ORGANIZATION_MOVE = "user:manage:organization:move";
    public static final String USER_MANAGE_DEPARTMENT_MOVE = "user:manage:department:move";
    public static final String USER_MANAGE_STATUS_ACTIVATE = "user:manage:status:activate";
    public static final String USER_MANAGE_STATUS_DEACTIVATE = "user:manage:status:deactivate";
    public static final String USER_MANAGE_STATUS_LOCK = "user:manage:status:lock";
    public static final String USER_MANAGE_STATUS_UNLOCK = "user:manage:status:unlock";
    public static final String USER_MANAGE_RESET_PASSWORD = "user:manage:password:reset";
    public static final String USER_MANAGE_SESSION_VIEW = "user:manage:session:view";
    public static final String USER_MANAGE_SESSION_REVOKE = "user:manage:session:revoke";
    public static final String USER_MANAGE_TRUSTED_DEVICE_VIEW = "user:manage:trusted-device:view";
    public static final String USER_MANAGE_TRUSTED_DEVICE_REVOKE = "user:manage:trusted-device:revoke";
    public static final String USER_AUTHORIZATION_VIEW = "user:authorization:view";
    public static final String USER_AUTHORIZATION_GRANT = "user:authorization:grant";
    public static final String USER_AUTHORIZATION_ADJUST = "user:authorization:adjust";
    public static final String USER_AUTHORIZATION_REVOKE = "user:authorization:revoke";
    public static final String USER_AUTHORIZATION_REATTACH = "user:authorization:reattach";
    public static final String USER_AUTHORIZATION_GRANT_OPTION_ISSUE = "user:authorization:grant-option:issue";
    public static final String USER_MANAGE_DELETE = "user:manage:delete";
    public static final String USER_MANAGE_RESTORE = "user:manage:restore";
    public static final String USER_IMPORT_TEMPLATE_DOWNLOAD = "user:import:template:download";
    public static final String USER_IMPORT_PREVIEW = "user:import:preview";
    public static final String USER_IMPORT_EXECUTE = "user:import:execute";
    public static final String USER_IMPORT_CANCEL = "user:import:cancel";
    public static final String USER_IMPORT_TASK_VIEW = "user:import:task:view";
    public static final String USER_IMPORT_ERROR_REPORT_DOWNLOAD = "user:import:error-report:download";
    public static final String USER_EXPORT_CREATE = "user:export:create";
    public static final String USER_EXPORT_TASK_VIEW = "user:export:task:view";
    public static final String USER_EXPORT_CANCEL = "user:export:cancel";
    public static final String USER_EXPORT_DOWNLOAD = "user:export:download";

    public static final String API_TOKEN_VIEW = "api-token:view";
    public static final String API_TOKEN_CREATE = "api-token:create";
    public static final String API_TOKEN_REVOKE = "api-token:revoke";

    public static final String SYSTEM_CONFIG_VIEW = "system:config:view";
    public static final String SYSTEM_CONFIG_CREATE = "system:config:create";
    public static final String SYSTEM_CONFIG_UPDATE = "system:config:update";
    public static final String SYSTEM_CONFIG_DELETE = "system:config:delete";

    public static final String NOTIFICATION_CONFIG_VIEW = "notification:config:view";
    public static final String EMAIL_DELIVERY_VIEW = "notification:email-delivery:view";
    public static final String NOTIFICATION_CHANNEL_UPDATE = "notification:channel:update";
    public static final String NOTIFICATION_SCHEDULE_UPDATE = "notification:schedule:update";
    public static final String NOTIFICATION_SENDER_UPDATE = "notification:sender:update";
    public static final String NOTIFICATION_RECIPIENT_COUNTY = "notification:recipient:county";
    public static final String NOTIFICATION_RECIPIENT_TENANT = "notification:recipient:tenant";

    public static final String AUTHORIZATION_TEMPLATE_VIEW = "authorization-template:view";
    public static final String AUTHORIZATION_TEMPLATE_CREATE = "authorization-template:create";
    public static final String AUTHORIZATION_TEMPLATE_CLONE = "authorization-template:clone";
    public static final String AUTHORIZATION_TEMPLATE_UPDATE = "authorization-template:update";
    public static final String AUTHORIZATION_TEMPLATE_ENABLE = "authorization-template:enable";
    public static final String AUTHORIZATION_TEMPLATE_DISABLE = "authorization-template:disable";
    public static final String AUTHORIZATION_TEMPLATE_DELETE = "authorization-template:delete";
    public static final String AUTHORIZATION_TEMPLATE_APPLY = "authorization-template:apply";

    public static final String ORG_MANAGE_VIEW = "organization:manage:view";
    public static final String ORG_MANAGE_CREATE = "organization:manage:create";
    public static final String ORG_MANAGE_UPDATE = "organization:manage:update";
    public static final String ORG_MANAGE_MOVE = "organization:manage:move";
    public static final String ORG_MANAGE_ENABLE = "organization:manage:enable";
    public static final String ORG_MANAGE_DISABLE = "organization:manage:disable";
    public static final String ORG_MANAGE_MERGE = "organization:manage:merge";
    public static final String ORG_MANAGE_DELETE = "organization:manage:delete";

    public static final String DEPARTMENT_VIEW = "department:view";
    public static final String DEPARTMENT_CREATE = "department:create";
    public static final String DEPARTMENT_UPDATE = "department:update";
    public static final String DEPARTMENT_MOVE = "department:move";
    public static final String DEPARTMENT_ENABLE = "department:enable";
    public static final String DEPARTMENT_DISABLE = "department:disable";
    public static final String DEPARTMENT_MERGE = "department:merge";
    public static final String DEPARTMENT_DELETE = "department:delete";

    public static final String APP_UPDATE_VIEW = "app-update:view";
    public static final String APP_UPDATE_PUBLISH = "app-update:publish";
    public static final String APP_UPDATE_POLICY_UPDATE = "app-update:policy-update";
    public static final String APP_UPDATE_DELETE = "app-update:delete";
    public static final String APP_UPDATE_STORAGE_VIEW = "app-update:storage:view";
    public static final String APP_UPDATE_STORAGE_SYNC = "app-update:storage:sync";
    public static final String APP_UPDATE_STORAGE_DELETE = "app-update:storage:delete";

    public static final String TENANT_MANAGE_VIEW = "tenant:manage:view";
    public static final String TENANT_MANAGE_CREATE = "tenant:manage:create";
    public static final String TENANT_MANAGE_ACTIVATE = "tenant:manage:status:activate";
    public static final String TENANT_MANAGE_DEACTIVATE = "tenant:manage:status:deactivate";

    public static final String SLA_POLICY_VIEW = "sla-policy:view";
    public static final String SLA_POLICY_UPDATE = "sla-policy:update";
    public static final String OBSERVABILITY_VIEW = "observability:view";
    public static final String REDIS_DIAGNOSTICS_VIEW = "observability:redis:view";

    private static final List<WorkOrderEntry> WORK_ORDER_ENTRIES = List.of(
            workOrderEntry(
                    WorkOrderType.NETWORK_FAULT,
                    "network-fault"
            ),
            workOrderEntry(
                    WorkOrderType.BOX_DOOR_OPEN,
                    "box-door-open"
            ),
            workOrderEntry(
                    WorkOrderType.DAMAGED_BOX_REPLACEMENT,
                    "damaged-box-replacement"
            )
    );

    private static final List<Entry> ENTRIES = buildEntries();
    private static final Set<String> NAMES = ENTRIES.stream().map(Entry::name).collect(Collectors.toUnmodifiableSet());
    private static final Map<String, Entry> ENTRY_BY_NAME = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::name, Function.identity()));
    private static final Map<WorkOrderType, WorkOrderEntry> WORK_ORDER_BY_TYPE = WORK_ORDER_ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(WorkOrderEntry::type, Function.identity()));

    private PermissionCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Set<String> names() {
        return NAMES;
    }

    public static boolean contains(String permissionName) {
        return ENTRY_BY_NAME.containsKey(permissionName);
    }

    public static Optional<Entry> find(String permissionName) {
        return Optional.ofNullable(ENTRY_BY_NAME.get(permissionName));
    }

    public static AccessMode accessMode(String permissionName) {
        return require(permissionName).accessMode();
    }

    public static boolean systemScoped(String permissionName) {
        return require(permissionName).systemScoped();
    }

    public static boolean assignableToApiToken(String permissionName) {
        return find(permissionName)
                .filter(Entry::apiTokenAssignable)
                .isPresent();
    }

    public static Entry require(String permissionName) {
        return find(permissionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Permission is not part of the fixed catalog: " + permissionName));
    }

    public static List<WorkOrderEntry> workOrderEntries() {
        return WORK_ORDER_ENTRIES;
    }

    public static Set<String> historyPermissions() {
        return permissions(WorkOrderEntry::historyPermission);
    }

    public static Set<String> auditPermissions() {
        return permissions(WorkOrderEntry::auditPermission);
    }

    public static Set<String> withdrawPermissions() {
        return permissions(WorkOrderEntry::withdrawPermission);
    }

    public static Set<String> reportPermissions() {
        return permissions(WorkOrderEntry::reportPermission);
    }

    public static Set<String> responseSubmitPermissions() {
        return permissions(WorkOrderEntry::responseSubmitPermission);
    }

    public static Set<String> assignmentAcceptPermissions() {
        return permissions(WorkOrderEntry::assignmentAcceptPermission);
    }

    public static Set<String> assignmentRejectPermissions() {
        return permissions(WorkOrderEntry::assignmentRejectPermission);
    }

    public static Set<String> viewPermissions() {
        return permissions(WorkOrderEntry::viewPermission);
    }

    public static Set<String> exportPermissions() {
        return permissions(WorkOrderEntry::exportPermission);
    }

    public static Set<String> attachmentReadPermissions() {
        return permissions(WorkOrderEntry::attachmentReadPermission);
    }

    public static Set<String> claimPermissions() {
        return permissions(WorkOrderEntry::claimPermission);
    }

    public static Set<String> assignmentCreatePermissions() {
        return permissions(WorkOrderEntry::assignmentCreatePermission);
    }

    public static Set<String> assignmentReassignPermissions() {
        return permissions(WorkOrderEntry::assignmentReassignPermission);
    }

    public static Set<String> verificationApprovePermissions() {
        return permissions(WorkOrderEntry::verificationApprovePermission);
    }

    public static Set<String> verificationRejectPermissions() {
        return permissions(WorkOrderEntry::verificationRejectPermission);
    }

    public static Set<String> cancelPermissions() {
        return permissions(WorkOrderEntry::cancelPermission);
    }

    public static Set<String> holdPermissions() {
        return permissions(WorkOrderEntry::holdPermission);
    }

    public static Set<String> resumePermissions() {
        return permissions(WorkOrderEntry::resumePermission);
    }

    public static Optional<WorkOrderEntry> workOrderEntry(WorkOrderType type) {
        return Optional.ofNullable(WORK_ORDER_BY_TYPE.get(type));
    }

    public static WorkOrderEntry requireWorkOrderEntry(WorkOrderType type) {
        return workOrderEntry(type)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported work order type: " + type));
    }

    private static Set<String> permissions(Function<WorkOrderEntry, String> selector) {
        return WORK_ORDER_ENTRIES.stream().map(selector).collect(Collectors.toUnmodifiableSet());
    }

    private static WorkOrderEntry workOrderEntry(WorkOrderType type,
                                                  String slug) {
        String prefix = "work-order:" + slug + ":";
        return new WorkOrderEntry(
                type,
                slug,
                prefix + "report",
                prefix + "response:submit",
                prefix + "view",
                prefix + "export",
                prefix + "history:view",
                prefix + "audit:view",
                prefix + "attachment:read",
                prefix + "claim",
                prefix + "assignment:accept",
                prefix + "assignment:reject",
                prefix + "assignment:create",
                prefix + "assignment:reassign",
                prefix + "verification:approve",
                prefix + "verification:reject",
                prefix + "verification:self-approve",
                prefix + "verification:self-reject",
                prefix + "cancel",
                prefix + "hold",
                prefix + "resume",
                prefix + "withdraw",
                prefix + "transfer:create",
                prefix + "transfer:accept",
                prefix + "transfer:reject",
                prefix + "transfer:cancel"
        );
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        entries.add(readEntry(DASHBOARD_STATISTICS_VIEW, "管理仪表盘查看", "查看授权组织范围内的工单统计、时限和区县对比"));
        entries.add(readEntry(SELF_PROFILE_VIEW, "个人信息查看", "查看本人个人信息"));
        entries.add(writeEntry(SELF_PROFILE_UPDATE, "个人信息修改", "修改本人姓名、手机号和邮箱", PermissionRiskLevel.MEDIUM));
        entries.add(writeEntry(SELF_PASSWORD_CHANGE, "本人密码修改", "校验当前密码后修改本人密码", PermissionRiskLevel.HIGH));
        entries.add(readEntry(SELF_SESSION_VIEW, "本人会话查看", "查看本人当前和历史登录会话"));
        entries.add(writeEntry(SELF_SESSION_REVOKE, "本人会话撤销", "撤销本人其他登录会话", PermissionRiskLevel.HIGH));
        for (WorkOrderEntry workOrder : WORK_ORDER_ENTRIES) {
            String typeName = workOrder.type().getDisplayName();
            entries.add(entry(workOrder.reportPermission(), typeName + "报单", "提交" + typeName + "报单", AccessMode.W, PermissionRiskLevel.MEDIUM, true));
            entries.add(entry(workOrder.viewPermission(), typeName + "查看", "查看组织范围或参与关系覆盖的" + typeName, AccessMode.R, PermissionRiskLevel.LOW, true));
            entries.add(entry(workOrder.exportPermission(), typeName + "导出", "导出组织范围或参与关系覆盖的" + typeName + "电子表格", AccessMode.R, PermissionRiskLevel.HIGH, true));
            entries.add(entry(workOrder.historyPermission(), typeName + "流转查看", "查看组织范围或参与关系覆盖的" + typeName + "流转、回单和验收结果", AccessMode.R, PermissionRiskLevel.MEDIUM, true));
            entries.add(entry(workOrder.auditPermission(), typeName + "内部审计", "查看组织范围内的" + typeName + "完整操作人和内部审计字段", AccessMode.R, PermissionRiskLevel.HIGH, true));
            entries.add(entry(workOrder.attachmentReadPermission(), typeName + "附件读取", "预览或下载组织范围或参与关系覆盖的" + typeName + "附件", AccessMode.R, PermissionRiskLevel.MEDIUM, true));
            entries.add(writeEntry(workOrder.claimPermission(), typeName + "主动接单", "主动接取组织范围内未分配的" + typeName, PermissionRiskLevel.MEDIUM));
            entries.add(writeEntry(workOrder.assignmentAcceptPermission(), typeName + "接受派单", "接受指派给本人的" + typeName, PermissionRiskLevel.MEDIUM));
            entries.add(writeEntry(workOrder.assignmentRejectPermission(), typeName + "拒绝派单", "拒绝指派给本人的" + typeName, PermissionRiskLevel.MEDIUM));
            entries.add(entry(workOrder.responseSubmitPermission(), typeName + "提交回单", "提交本人当前负责的" + typeName + "处理回单", AccessMode.W, PermissionRiskLevel.HIGH, true));
            entries.add(entry(workOrder.assignmentCreatePermission(), typeName + "派单", "将未分配的" + typeName + "派给合格处理人员", AccessMode.W, PermissionRiskLevel.HIGH, true));
            entries.add(entry(workOrder.assignmentReassignPermission(), typeName + "调单", "调整" + typeName + "的目标处理人员", AccessMode.W, PermissionRiskLevel.CRITICAL, true));
            entries.add(entry(workOrder.verificationApprovePermission(), typeName + "验收通过", "验收通过" + typeName + "处理回单并闭环", AccessMode.W, PermissionRiskLevel.CRITICAL, true));
            entries.add(entry(workOrder.verificationRejectPermission(), typeName + "验收驳回", "驳回" + typeName + "处理回单并进入返工", AccessMode.W, PermissionRiskLevel.HIGH, true));
            entries.add(entry(workOrder.verificationSelfApprovePermission(), typeName + "本人回单验收通过", "允许验收本人提交的" + typeName + "回单", AccessMode.W, PermissionRiskLevel.CRITICAL, true));
            entries.add(entry(workOrder.verificationSelfRejectPermission(), typeName + "本人回单验收驳回", "允许驳回本人提交的" + typeName + "回单", AccessMode.W, PermissionRiskLevel.CRITICAL, true));
            entries.add(writeEntry(workOrder.cancelPermission(), typeName + "取消作废", "取消组织范围内的非终态" + typeName, PermissionRiskLevel.CRITICAL));
            entries.add(writeEntry(workOrder.holdPermission(), typeName + "处理挂起", "挂起本人当前负责的" + typeName, PermissionRiskLevel.HIGH));
            entries.add(writeEntry(workOrder.resumePermission(), typeName + "恢复处理", "恢复本人当前挂起的" + typeName, PermissionRiskLevel.HIGH));
            entries.add(writeEntry(workOrder.withdrawPermission(), typeName + "提单撤回", "撤回本人提交且尚未进入处理的" + typeName, PermissionRiskLevel.HIGH));
            entries.add(writeEntry(workOrder.transferCreatePermission(), typeName + "完整移交发起", "向其他组织发起" + typeName + "完整移交", PermissionRiskLevel.CRITICAL));
            entries.add(writeEntry(workOrder.transferAcceptPermission(), typeName + "完整移交接收", "代表目标组织接收" + typeName + "完整移交", PermissionRiskLevel.CRITICAL));
            entries.add(writeEntry(workOrder.transferRejectPermission(), typeName + "完整移交拒绝", "代表目标组织拒绝" + typeName + "完整移交", PermissionRiskLevel.HIGH));
            entries.add(writeEntry(workOrder.transferCancelPermission(), typeName + "完整移交撤销", "在目标组织处理前撤销" + typeName + "完整移交", PermissionRiskLevel.HIGH));
        }

        entries.add(readEntry(USER_MANAGE_VIEW, "用户查看", "查看授权组织范围内的用户列表和详情"));
        entries.add(writeEntry(USER_MANAGE_CREATE, "用户创建", "在授权组织范围内创建用户", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(USER_MANAGE_UPDATE_DETAILS, "用户资料修改", "更新授权组织范围内用户的基本信息", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(USER_MANAGE_CONTACT_UPDATE, "用户联系方式修改", "修改用户手机号和邮箱等身份联系方式", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_ORGANIZATION_MOVE, "用户所属组织调整", "将用户迁移到另一个授权组织，并显式处理既有授权范围", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_DEPARTMENT_MOVE, "用户所属部门调整", "调整用户在所属组织内的部门", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(USER_MANAGE_STATUS_ACTIVATE, "用户启用", "将未启用或已停用用户恢复为可登录状态", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_STATUS_DEACTIVATE, "用户停用", "停用用户并阻止其继续登录", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_STATUS_LOCK, "用户锁定", "因安全原因锁定用户账号", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_STATUS_UNLOCK, "用户解锁", "解除用户账号锁定", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_RESET_PASSWORD, "用户密码重置", "重置授权组织范围内用户的登录密码", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(USER_MANAGE_SESSION_VIEW, "用户会话查看", "查看授权用户的登录会话"));
        entries.add(writeEntry(USER_MANAGE_SESSION_REVOKE, "用户会话撤销", "撤销授权用户的登录会话", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(USER_MANAGE_TRUSTED_DEVICE_VIEW, "用户受信设备查看", "查看授权用户的受信设备"));
        entries.add(writeEntry(USER_MANAGE_TRUSTED_DEVICE_REVOKE, "用户受信设备撤销", "撤销授权用户的受信设备", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(USER_AUTHORIZATION_VIEW, "用户授权查看", "查看当前操作者可管理的用户授权及最终权限摘要"));
        entries.add(writeEntry(USER_AUTHORIZATION_GRANT, "用户授权授予", "新增不超过自身委派边界的权限授权", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_AUTHORIZATION_ADJUST, "用户授权调整", "缩放自身可管理授权的范围、关系或有效期", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_AUTHORIZATION_REVOKE, "用户授权撤销", "撤销自身可管理的授权来源", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_AUTHORIZATION_REATTACH, "用户授权重新挂接", "将上游永久失效的授权子树重新挂接到兼容父授权", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_AUTHORIZATION_GRANT_OPTION_ISSUE, "转授权能力授予", "允许目标用户继续转授指定原子权限", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_DELETE, "用户删除", "软删除授权组织范围内的用户", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_MANAGE_RESTORE, "用户恢复", "恢复授权组织范围内已删除用户", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(USER_IMPORT_TEMPLATE_DOWNLOAD, "用户导入模板下载", "生成并下载按当前委派边界裁剪的用户导入模板"));
        entries.add(writeEntry(USER_IMPORT_PREVIEW, "用户导入预览", "上传并校验用户及授权导入工作簿", PermissionRiskLevel.MEDIUM));
        entries.add(writeEntry(USER_IMPORT_EXECUTE, "用户导入执行", "执行已预览的用户及授权批量变更", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(USER_IMPORT_CANCEL, "用户导入取消", "取消本人创建且尚未结束的导入任务", PermissionRiskLevel.MEDIUM));
        entries.add(readEntry(USER_IMPORT_TASK_VIEW, "用户导入任务查看", "查看本人可见的用户导入任务及进度"));
        entries.add(readEntry(USER_IMPORT_ERROR_REPORT_DOWNLOAD, "用户导入错误表下载", "下载用户导入错误明细电子表格"));
        entries.add(readEntry(USER_EXPORT_CREATE, "用户导出创建", "创建授权组织范围内的用户数据导出任务"));
        entries.add(readEntry(USER_EXPORT_TASK_VIEW, "用户导出任务查看", "查看本人可见的用户导出任务及进度"));
        entries.add(writeEntry(USER_EXPORT_CANCEL, "用户导出取消", "取消本人尚未完成的用户导出任务", PermissionRiskLevel.MEDIUM));
        entries.add(readEntry(USER_EXPORT_DOWNLOAD, "用户导出下载", "下载本人创建的用户数据导出结果"));

        entries.add(entry(API_TOKEN_VIEW, "API Token 查看", "查看本人创建的 API Token 及其状态", AccessMode.R,
                PermissionRiskLevel.MEDIUM, true));
        entries.add(entry(API_TOKEN_CREATE, "API Token 创建", "为本人创建不超过当前有效权限的 API Token", AccessMode.W,
                PermissionRiskLevel.CRITICAL, true));
        entries.add(entry(API_TOKEN_REVOKE, "API Token 撤销", "撤销本人创建的 API Token", AccessMode.W,
                PermissionRiskLevel.HIGH, true));

        entries.add(readEntry(SYSTEM_CONFIG_VIEW, "系统配置查看", "查看授权组织范围内的系统配置"));
        entries.add(writeEntry(SYSTEM_CONFIG_CREATE, "系统配置创建", "在授权组织范围内创建系统配置项", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(SYSTEM_CONFIG_UPDATE, "系统配置修改", "更新授权组织范围内的系统配置项", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(SYSTEM_CONFIG_DELETE, "系统配置删除", "删除授权组织范围内的系统配置项", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(NOTIFICATION_CONFIG_VIEW, "通知配置查看", "查看授权组织范围内的通知渠道和定时通知配置"));
        entries.add(readEntry(EMAIL_DELIVERY_VIEW, "邮件发送台账查看", "查看职责组织范围内的邮件发送结果和脱敏详情，默认仅超级管理员持有"));
        entries.add(writeEntry(NOTIFICATION_CHANNEL_UPDATE, "通知渠道开关修改", "单独开启或关闭授权组织范围内的通知渠道", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(NOTIFICATION_SCHEDULE_UPDATE, "通知计划修改", "修改授权组织范围内的未闭环工单日报计划", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(NOTIFICATION_SENDER_UPDATE, "邮件发件方修改", "配置授权组织范围内的邮件服务器、发件账号和授权码", PermissionRiskLevel.CRITICAL));
        entries.add(entry(NOTIFICATION_RECIPIENT_COUNTY, "区县通知接收", "接收所属区县工单通知和未闭环工单日报", AccessMode.NONE, PermissionRiskLevel.LOW, true));
        entries.add(entry(NOTIFICATION_RECIPIENT_TENANT, "市公司通知接收", "接收无法归属区县的市公司级工单通知", AccessMode.NONE, PermissionRiskLevel.LOW, true));

        entries.add(readEntry(AUTHORIZATION_TEMPLATE_VIEW, "岗位模板查看", "查看岗位模板及其原子权限清单"));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_CREATE, "岗位模板创建", "创建自定义岗位模板", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_CLONE, "岗位模板复制", "复制可见岗位模板为自定义模板", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_UPDATE, "岗位模板修改", "修改岗位模板的名称、说明和权限清单", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_ENABLE, "岗位模板启用", "启用已停用的岗位模板", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_DISABLE, "岗位模板停用", "停止岗位模板用于后续批量添加", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_DELETE, "岗位模板删除", "删除未被运行时授权引用的岗位模板", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(AUTHORIZATION_TEMPLATE_APPLY, "岗位模板应用", "将模板权限清单加入用户授权变更", PermissionRiskLevel.HIGH));

        entries.add(readEntry(ORG_MANAGE_VIEW, "组织查看", "查看授权范围内的组织目录"));
        entries.add(writeEntry(ORG_MANAGE_CREATE, "组织创建", "在授权范围内创建组织节点", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(ORG_MANAGE_UPDATE, "组织资料修改", "修改授权范围内组织节点的名称和资料", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(ORG_MANAGE_MOVE, "组织移动", "调整授权范围内组织节点的父级关系", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(ORG_MANAGE_ENABLE, "组织启用", "启用授权范围内的组织节点", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(ORG_MANAGE_DISABLE, "组织停用", "停用授权范围内的组织节点", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(ORG_MANAGE_MERGE, "组织合并", "预检并合并授权范围内的组织节点", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(ORG_MANAGE_DELETE, "组织删除", "删除满足约束的组织节点", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(DEPARTMENT_VIEW, "部门查看", "查看所属组织内的部门树"));
        entries.add(writeEntry(DEPARTMENT_CREATE, "部门创建", "在所属组织内创建部门", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(DEPARTMENT_UPDATE, "部门资料修改", "修改所属组织内的部门资料", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(DEPARTMENT_MOVE, "部门移动", "调整所属组织内部门的父级关系", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(DEPARTMENT_ENABLE, "部门启用", "启用已停用部门", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(DEPARTMENT_DISABLE, "部门停用", "停用部门并停止承接新工单", PermissionRiskLevel.HIGH));
        entries.add(writeEntry(DEPARTMENT_MERGE, "部门合并", "预检并合并部门、人员与工单责任关系", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(DEPARTMENT_DELETE, "部门删除", "预检并删除无在岗用户的部门", PermissionRiskLevel.CRITICAL));

        entries.add(systemEntry(APP_UPDATE_VIEW, "安装包查看", "查看系统级安卓应用安装包发布记录", AccessMode.R));
        entries.add(systemEntry(APP_UPDATE_PUBLISH, "安装包发布", "登记并发布系统级安卓应用安装包", AccessMode.W));
        entries.add(systemEntry(APP_UPDATE_POLICY_UPDATE, "安装包策略修改", "修改系统级安装包升级策略", AccessMode.W));
        entries.add(systemEntry(APP_UPDATE_DELETE, "安装包发布记录删除", "删除系统级安装包发布记录", AccessMode.W));
        entries.add(systemEntry(APP_UPDATE_STORAGE_VIEW, "安装包存储查看", "查看系统级安装包对象存储状态", AccessMode.R));
        entries.add(systemEntry(APP_UPDATE_STORAGE_SYNC, "安装包存储同步", "将系统级发布记录同步到对象存储", AccessMode.W));
        entries.add(systemEntry(APP_UPDATE_STORAGE_DELETE, "安装包对象删除", "删除系统级安装包对象", AccessMode.W));

        entries.add(readEntry(TENANT_MANAGE_VIEW, "市公司租户查看", "查看平台内市公司租户和租户组"));
        entries.add(writeEntry(TENANT_MANAGE_CREATE, "市公司租户创建", "创建市公司租户并初始化管理员", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(TENANT_MANAGE_ACTIVATE, "市公司租户启用", "启用已停用的市公司租户", PermissionRiskLevel.CRITICAL));
        entries.add(writeEntry(TENANT_MANAGE_DEACTIVATE, "市公司租户停用", "停用市公司租户及其登录能力", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(SLA_POLICY_VIEW, "故障时限策略查看", "查看当前市公司的故障时限策略"));
        entries.add(writeEntry(SLA_POLICY_UPDATE, "故障时限策略修改", "修改当前市公司的故障时限策略", PermissionRiskLevel.CRITICAL));
        entries.add(readEntry(OBSERVABILITY_VIEW, "运行诊断查看", "查看授权组织范围内的客户端错误、警告和操作诊断"));
        entries.add(readEntry(REDIS_DIAGNOSTICS_VIEW, "Redis 运行诊断查看", "查看 Redis 连接、缓存和轻量观测队列状态"));
        return List.copyOf(entries);
    }

    private static Entry readEntry(String name, String displayName, String description) {
        return entry(name, displayName, description, AccessMode.R, PermissionRiskLevel.MEDIUM, true);
    }

    private static Entry writeEntry(String name,
                                    String displayName,
                                    String description,
                                    PermissionRiskLevel riskLevel) {
        return entry(name, displayName, description, AccessMode.W, riskLevel, true);
    }

    private static Entry systemEntry(String name,
                                     String displayName,
                                     String description,
                                     AccessMode accessMode) {
        return new Entry(name, displayName, description, accessMode, PermissionRiskLevel.CRITICAL,
                true, true, false);
    }

    private static Entry entry(String name,
                               String displayName,
                               String description,
                               AccessMode accessMode,
                               PermissionRiskLevel riskLevel,
                               boolean delegable) {
        return new Entry(name, displayName, description, accessMode, riskLevel,
                delegable, false, apiTokenAssignable(name));
    }

    private static boolean apiTokenAssignable(String name) {
        if (DASHBOARD_STATISTICS_VIEW.equals(name)) {
            return true;
        }
        if (!name.startsWith("work-order:")) {
            return false;
        }
        return name.endsWith(":view")
                || name.endsWith(":history:view")
                || name.endsWith(":attachment:read")
                || name.endsWith(":report")
                || name.endsWith(":response:submit");
    }

    public record Entry(
            String name,
            String displayName,
            String description,
            AccessMode accessMode,
            PermissionRiskLevel riskLevel,
            boolean delegable,
            boolean systemScoped,
            boolean apiTokenAssignable
    ) {
    }

    public record WorkOrderEntry(
            WorkOrderType type,
            String slug,
            String reportPermission,
            String responseSubmitPermission,
            String viewPermission,
            String exportPermission,
            String historyPermission,
            String auditPermission,
            String attachmentReadPermission,
            String claimPermission,
            String assignmentAcceptPermission,
            String assignmentRejectPermission,
            String assignmentCreatePermission,
            String assignmentReassignPermission,
            String verificationApprovePermission,
            String verificationRejectPermission,
            String verificationSelfApprovePermission,
            String verificationSelfRejectPermission,
            String cancelPermission,
            String holdPermission,
            String resumePermission,
            String withdrawPermission,
            String transferCreatePermission,
            String transferAcceptPermission,
            String transferRejectPermission,
            String transferCancelPermission
    ) {
        public Set<String> allPermissions() {
            return Set.of(
                    reportPermission, responseSubmitPermission, viewPermission, exportPermission, historyPermission, auditPermission,
                    attachmentReadPermission, claimPermission, assignmentAcceptPermission, assignmentRejectPermission,
                    assignmentCreatePermission, assignmentReassignPermission, verificationApprovePermission,
                    verificationRejectPermission, verificationSelfApprovePermission,
                    verificationSelfRejectPermission, cancelPermission,
                    holdPermission, resumePermission, withdrawPermission,
                    transferCreatePermission, transferAcceptPermission,
                    transferRejectPermission, transferCancelPermission
            );
        }

        public Set<String> frontlinePermissions() {
            return Set.of(reportPermission, viewPermission, historyPermission, attachmentReadPermission,
                    verificationApprovePermission, verificationRejectPermission, withdrawPermission);
        }

        public Set<String> dispatcherPermissions() {
            return Set.of(viewPermission, historyPermission, attachmentReadPermission,
                    assignmentCreatePermission, assignmentReassignPermission);
        }

        public Set<String> transferCoordinatorPermissions() {
            return Set.of(viewPermission, historyPermission, attachmentReadPermission,
                    transferCreatePermission, transferAcceptPermission,
                    transferRejectPermission, transferCancelPermission);
        }

        public Set<String> supervisorPermissions() {
            return Set.of(viewPermission, historyPermission, attachmentReadPermission,
                    assignmentCreatePermission, assignmentReassignPermission,
                    verificationApprovePermission, verificationRejectPermission,
                    verificationSelfApprovePermission, verificationSelfRejectPermission,
                    cancelPermission, holdPermission, resumePermission);
        }

        public Set<String> verifierPermissions() {
            return Set.of(viewPermission, historyPermission, attachmentReadPermission,
                    verificationApprovePermission, verificationRejectPermission);
        }

        public Set<String> controllerPermissions() {
            return Set.of(viewPermission, historyPermission, attachmentReadPermission,
                    verificationSelfApprovePermission, verificationSelfRejectPermission, cancelPermission);
        }
    }
}
