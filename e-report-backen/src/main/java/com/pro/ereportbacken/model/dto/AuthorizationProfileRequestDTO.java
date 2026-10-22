package com.pro.ereportbacken.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AuthorizationProfileRequestDTO(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Z0-9_]+", message = "岗位模板编码只能包含大写字母、数字和下划线") String code,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 500) String description,
        @Size(max = 300) List<@Valid Rule> rules,
        @Size(max = 500) String changeSummary
) {
    public AuthorizationProfileRequestDTO {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(@NotBlank String permissionName) {
    }
}
