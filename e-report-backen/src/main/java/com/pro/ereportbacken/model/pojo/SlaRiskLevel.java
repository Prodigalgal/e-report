package com.pro.ereportbacken.model.pojo;

public enum SlaRiskLevel {
    NORMAL("正常"),
    DUE_SOON("临期"),
    BREACHED("已超时"),
    PAUSED("已暂停"),
    COMPLETED("已完成");

    private final String displayName;

    SlaRiskLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
