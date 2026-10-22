package com.pro.ereportbacken.notification;

import java.util.UUID;

public record MailSenderOrigin(UUID orgUnitId, String orgUnitName, boolean runtimeDefault) {
    public String scope() {
        if (runtimeDefault) return "RUNTIME_SECRET";
        return orgUnitId == null ? "TENANT_OVERRIDE" : "ORGANIZATION_OVERRIDE";
    }
}
