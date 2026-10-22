package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.UserStatus;
import jakarta.validation.Valid;

public record UserAdministrationUpdateRequestDTO(
        @Valid UserUpdateRequestDTO details,
        UserStatus status
) {
}
