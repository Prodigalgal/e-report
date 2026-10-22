package com.pro.ereportbacken.model.pojo;

public enum WorkOrderEventType {
    SUBMITTED("工单已提交"),
    CLAIMED("已主动接单"),
    ASSIGNED("已派单"),
    ASSIGNMENT_ACCEPTED("已接受派单"),
    ASSIGNMENT_REJECTED("已拒绝派单"),
    REASSIGNED("已调整处理人"),
    RESPONSE_SUBMITTED("已提交回单"),
    VERIFICATION_APPROVED("验收通过"),
    VERIFICATION_REJECTED("验收驳回"),
    HELD("工单已挂起"),
    RESUMED("已恢复处理"),
    CANCELLED("工单已取消"),
    ORGANIZATION_TRANSFER_REQUESTED("已发起组织移交"),
    ORGANIZATION_TRANSFER_ACCEPTED("目标组织已接收"),
    ORGANIZATION_TRANSFER_REJECTED("目标组织已拒绝"),
    ORGANIZATION_TRANSFER_CANCELLED("发起方已撤销移交"),
    SLA_BREACHED("处理时限已超时"),
    MIGRATED("历史工单已迁移");

    private final String label;

    WorkOrderEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
