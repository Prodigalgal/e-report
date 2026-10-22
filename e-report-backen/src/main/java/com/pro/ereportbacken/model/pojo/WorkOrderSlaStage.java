package com.pro.ereportbacken.model.pojo;

public enum WorkOrderSlaStage {
    CLAIM("待接单或派单"),
    ASSIGNMENT_ACCEPTANCE("待接收派单"),
    RESPONSE("处理回单"),
    VERIFICATION("验收闭环"),
    REWORK("验收驳回整改"),
    COMPLETED("已结束");

    private final String displayName;

    WorkOrderSlaStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
