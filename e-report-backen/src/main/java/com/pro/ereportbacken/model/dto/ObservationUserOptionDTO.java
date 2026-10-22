package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record ObservationUserOptionDTO(
        UUID id,
        String name,
        String username,
        UUID orgUnitId,
        String orgUnitName
) {
}
