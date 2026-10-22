package com.pro.ereportbacken.model.pojo;

public enum VerificationDecision {
    APPROVED("验收通过"),
    REJECTED("验收驳回");

    private final String label;

    VerificationDecision(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
