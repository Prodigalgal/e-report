package com.pro.ereportbacken.security;

public record AdministrativeControlDecision(
        boolean allowed,
        String reasonCode,
        String message
) {
    public static AdministrativeControlDecision allow() {
        return new AdministrativeControlDecision(true, "ALLOWED", "允许执行该操作。");
    }

    public static AdministrativeControlDecision deny(String reasonCode, String message) {
        return new AdministrativeControlDecision(false, reasonCode, message);
    }
}
