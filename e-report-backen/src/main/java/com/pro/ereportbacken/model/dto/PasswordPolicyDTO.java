package com.pro.ereportbacken.model.dto;

public record PasswordPolicyDTO(
        int minLength,
        int maxLength,
        boolean requireUppercase,
        boolean requireLowercase,
        boolean requireDigit,
        boolean requireSpecial,
        String sourceScope,
        String sourceOrgUnitName,
        boolean inherited,
        boolean localOverride
) {
}
