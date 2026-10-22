package com.pro.ereportbacken.model.pojo;

public enum WorkOrderPriority {
    URGENT("紧急"),
    HIGH("高优先"),
    ROUTINE("常规"),
    LOW("低优先");

    private final String displayName;

    WorkOrderPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
