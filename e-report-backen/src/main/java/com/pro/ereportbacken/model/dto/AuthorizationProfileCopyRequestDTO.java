package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthorizationProfileCopyRequestDTO(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Z0-9_]+") String code,
        @NotBlank @Size(max = 100) String displayName
) {
}
