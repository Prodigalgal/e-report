package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.TenantGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantGroupRepository extends JpaRepository<TenantGroup, UUID> {
    Optional<TenantGroup> findByCode(String code);
}


