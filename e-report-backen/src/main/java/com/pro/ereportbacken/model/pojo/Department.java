package com.pro.ereportbacken.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "departments",
        indexes = {
                @Index(name = "idx_departments_tenant_org_path", columnList = "tenant_id,org_unit_id,path"),
                @Index(name = "idx_departments_parent_status", columnList = "parent_id,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_departments_tenant_org_code",
                        columnNames = {"tenant_id", "org_unit_id", "code"}),
                @UniqueConstraint(name = "uk_departments_tenant_id", columnNames = {"tenant_id", "id"})
        }
)
@Getter
@Setter
public class Department extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 700)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status = DepartmentStatus.ACTIVE;

    @Column(name = "merged_into_department_id")
    private UUID mergedIntoDepartmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
