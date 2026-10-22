package com.pro.ereportbacken.model.dto;

public record UserDetailResponseDTO(
        UserResponseDTO user,
        UserWorkOrderPerformanceDTO performance,
        UserManagementCapabilitiesDTO capabilities
) {
}
