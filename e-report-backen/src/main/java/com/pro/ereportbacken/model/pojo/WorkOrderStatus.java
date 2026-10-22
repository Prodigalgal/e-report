package com.pro.ereportbacken.model.pojo;

import java.util.Arrays;

public enum WorkOrderStatus {
    SUBMITTED("已提交", "待处理"),
    IN_PROGRESS("处理中", "处理中"),
    PENDING_VERIFICATION("待验收", "已解决"),
    ON_HOLD("已挂起", "已挂起"),
    CLOSED("已闭环", "已关闭"),
    CANCELLED("已取消", "已取消");

    private final String label;
    private final String externalValue;

    WorkOrderStatus(String label, String externalValue) {
        this.label = label;
        this.externalValue = externalValue;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return externalValue;
    }

    public boolean matches(String value) {
        return name().equalsIgnoreCase(value) || label.equals(value) || externalValue.equals(value);
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /**
     * 生命周期允许的单向转换。幂等写入同一状态始终允许；已闭环/已取消工单不允许重新打开。
     */
    public boolean canTransitionTo(WorkOrderStatus nextStatus) {
        if (nextStatus == null || this == nextStatus) {
            return nextStatus != null;
        }
        return switch (this) {
            case SUBMITTED -> nextStatus == IN_PROGRESS
                    || nextStatus == PENDING_VERIFICATION
                    || nextStatus == CANCELLED;
            case IN_PROGRESS -> nextStatus == SUBMITTED
                    || nextStatus == PENDING_VERIFICATION
                    || nextStatus == ON_HOLD
                    || nextStatus == CANCELLED;
            case PENDING_VERIFICATION -> nextStatus == IN_PROGRESS || nextStatus == CLOSED || nextStatus == CANCELLED;
            case ON_HOLD -> nextStatus == IN_PROGRESS || nextStatus == SUBMITTED || nextStatus == CANCELLED;
            case CLOSED, CANCELLED -> false;
        };
    }

    public static WorkOrderStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.matches(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported work order status: " + value));
    }
}
