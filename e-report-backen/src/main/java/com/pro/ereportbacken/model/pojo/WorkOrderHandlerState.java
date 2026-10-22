package com.pro.ereportbacken.model.pojo;

public enum WorkOrderHandlerState {
    CURRENT("当前处理"),
    PENDING_ACCEPTANCE("待接收"),
    RESPONDED("已回单");

    private final String displayName;

    WorkOrderHandlerState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
