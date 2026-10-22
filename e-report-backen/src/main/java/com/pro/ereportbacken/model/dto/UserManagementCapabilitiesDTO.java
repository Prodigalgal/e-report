package com.pro.ereportbacken.model.dto;

import java.util.Map;

public record UserManagementCapabilitiesDTO(
        String organizationName,
        Map<String, Decision> actions
) {
    public UserManagementCapabilitiesDTO {
        actions = Map.copyOf(actions);
    }

    public record Decision(boolean allowed, String reasonCode, String message) {
    }
}
