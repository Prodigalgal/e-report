package com.pro.ereportbacken.model.dto;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.TenantStatus;

public record TenantGroupDTO(UUID id, String code, String name, TenantStatus status) {
}

