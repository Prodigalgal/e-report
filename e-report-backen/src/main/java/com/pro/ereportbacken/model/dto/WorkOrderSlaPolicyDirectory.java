package com.pro.ereportbacken.model.dto;

import java.util.List;

public record WorkOrderSlaPolicyDirectory(List<WorkOrderSlaPolicyDTO> policies) {

    public WorkOrderSlaPolicyDirectory {
        policies = List.copyOf(policies);
    }
}
