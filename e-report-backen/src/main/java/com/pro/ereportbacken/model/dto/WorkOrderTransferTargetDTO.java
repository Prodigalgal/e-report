package com.pro.ereportbacken.model.dto;

import java.util.UUID;

public record WorkOrderTransferTargetDTO(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String path
) {
}
