package com.pro.ereportbacken.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VersionedWorkOrderCommandDTO {
    @NotNull(message = "工单版本不能为空")
    private Long expectedVersion;
}
