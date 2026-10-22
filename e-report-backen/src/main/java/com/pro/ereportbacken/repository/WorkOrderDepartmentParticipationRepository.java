package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentParticipation;
import com.pro.ereportbacken.model.pojo.WorkOrderDepartmentRelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface WorkOrderDepartmentParticipationRepository
        extends JpaRepository<WorkOrderDepartmentParticipation, UUID> {

    List<WorkOrderDepartmentParticipation> findByTenantIdAndWorkOrderIdOrderByStartedAtAsc(
            UUID tenantId, UUID workOrderId);

    @Query("""
            SELECT DISTINCT participation.workOrderId
            FROM WorkOrderDepartmentParticipation participation
            WHERE participation.tenantId = :tenantId
              AND participation.departmentId = :departmentId
            """)
    Set<UUID> findDistinctWorkOrderIdsByDepartment(
            @Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT participation FROM WorkOrderDepartmentParticipation participation
            WHERE participation.tenantId = :tenantId
              AND participation.departmentId = :departmentId
            ORDER BY participation.id
            """)
    List<WorkOrderDepartmentParticipation> findByTenantIdAndDepartmentIdForUpdate(
            @Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE WorkOrderDepartmentParticipation participation
            SET participation.active = false,
                participation.endedAt = :endedAt
            WHERE participation.tenantId = :tenantId
              AND participation.workOrderId = :workOrderId
              AND participation.relationType = :relationType
              AND participation.active = true
            """)
    int endActiveRelation(@Param("tenantId") UUID tenantId,
                          @Param("workOrderId") UUID workOrderId,
                          @Param("relationType") WorkOrderDepartmentRelationType relationType,
                          @Param("endedAt") LocalDateTime endedAt);
}
