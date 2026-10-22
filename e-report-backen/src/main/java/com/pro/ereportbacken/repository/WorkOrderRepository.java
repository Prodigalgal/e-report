package com.pro.ereportbacken.repository;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query; // 确保导入
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param; // 确保导入
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List; // 确保导入
import java.util.Optional;
import java.util.Set;
import java.util.Collection;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {

    long countByTenantIdAndReportedByIdAndStatusCodeNotIn(
            UUID tenantId,
            UUID reportedById,
            Collection<String> terminalStatusCodes);

    /**
     * 查找所有符合规范的故障报告 (分页)，只预加载 ToOne 关联。
     * ToMany 关联 (attachments) 将在 Service 层通过第二步查询加载。
     */
    @Override
    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy", "closedBy"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);

    /**
     * 查找所有故障报告 (分页)，只预加载 ToOne 关联。
     */
    @Override
    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy", "closedBy"})
    Page<WorkOrder> findAll(Pageable pageable);

    /**
     * 根据ID查找故障报告，并预加载所有需要的关联。
     * 这个方法用于获取单个详情，可以安全地 fetch 集合。
     */
    @Override
    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy", "closedBy", "attachments"})
    Optional<WorkOrder> findById(UUID id);

    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy", "closedBy", "attachments", "currentAssignment", "currentAssignment.assignee", "pendingAssignment", "pendingAssignment.assignee", "currentResponse", "currentResponse.submittedBy"})
    Optional<WorkOrder> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fr FROM WorkOrder fr WHERE fr.tenantId = :tenantId AND fr.id = :id")
    Optional<WorkOrder> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fr FROM WorkOrder fr WHERE fr.tenantId = :tenantId AND fr.id IN :ids ORDER BY fr.id")
    List<WorkOrder> findAllByTenantIdAndIdInForUpdate(@Param("tenantId") UUID tenantId,
                                                       @Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fr FROM WorkOrder fr WHERE fr.tenantId = :tenantId AND fr.id = :id")
    Optional<WorkOrder> findForSlaUpdate(@Param("tenantId") UUID tenantId,
                                        @Param("id") UUID id);

    @Query("""
            SELECT fr.id FROM WorkOrder fr
            WHERE fr.tenantId = :tenantId
              AND fr.slaStage <> com.pro.ereportbacken.model.pojo.WorkOrderSlaStage.COMPLETED
              AND fr.slaDueAt IS NOT NULL
              AND fr.slaDueAt <= :now
              AND fr.slaBreachedAt IS NULL
            ORDER BY fr.slaDueAt ASC
            """)
    List<UUID> findSlaBreachCandidateIds(@Param("tenantId") UUID tenantId,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    @Query("SELECT DISTINCT fr FROM WorkOrder fr LEFT JOIN FETCH fr.materialUsages WHERE fr.tenantId = :tenantId AND fr.id = :id")
    Optional<WorkOrder> findWithMaterialUsagesByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT DISTINCT fr FROM WorkOrder fr " +
            "LEFT JOIN FETCH fr.reportedBy " +
            "LEFT JOIN FETCH fr.resolvedBy " +
            "LEFT JOIN FETCH fr.currentAssignment currentAssignment " +
            "LEFT JOIN FETCH currentAssignment.assignee " +
            "LEFT JOIN FETCH fr.pendingAssignment pendingAssignment " +
            "LEFT JOIN FETCH pendingAssignment.assignee " +
            "LEFT JOIN FETCH fr.currentResponse currentResponse " +
            "LEFT JOIN FETCH currentResponse.submittedBy " +
            "WHERE fr.tenantId = :tenantId AND fr.id IN :ids")
    List<WorkOrder> findListRowsByTenantIdAndIds(@Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

    @Query("""
            SELECT a.workOrder.id AS workOrderId,
                   COUNT(a.id) AS total
            FROM Attachment a
            WHERE a.tenantId = :tenantId AND a.workOrder.id IN :ids
            GROUP BY a.workOrder.id
            """)
    List<AttachmentCountProjection> countAttachmentsByTenantIdAndWorkOrderIdIn(@Param("tenantId") UUID tenantId,
                                                                               @Param("ids") List<UUID> ids);

    @Query("""
            SELECT fr.reportType AS reportType,
                   COUNT(fr) AS total,
                   SUM(CASE WHEN fr.status = '待处理' THEN 1 ELSE 0 END) AS pending,
                   SUM(CASE WHEN fr.status = '已解决' THEN 1 ELSE 0 END) AS resolved,
                   SUM(CASE WHEN fr.status = '已关闭' THEN 1 ELSE 0 END) AS closed,
                   COALESCE(SUM(fr.boxQuantity), 0) AS boxQuantityTotal,
                   SUM(CASE WHEN fr.replacementRequired = true THEN 1 ELSE 0 END) AS replacementRequired,
                   SUM(CASE WHEN fr.replacementCompleted = true THEN 1 ELSE 0 END) AS replacementCompleted
            FROM WorkOrder fr
            WHERE fr.tenantId = :tenantId AND fr.reportType IN :reportTypes
            GROUP BY fr.reportType
            """)
    List<WorkOrderTypeStatisticsProjection> summarizeByReportType(@Param("tenantId") UUID tenantId,
                                                                   @Param("reportTypes") Set<WorkOrderType> reportTypes);

    @Query("""
            SELECT COALESCE(fr.status, '未知') AS status,
                   COUNT(fr) AS total
            FROM WorkOrder fr
            WHERE fr.tenantId = :tenantId AND fr.reportType IN :reportTypes
            GROUP BY COALESCE(fr.status, '未知')
            """)
    List<WorkOrderStatusStatisticsProjection> summarizeByStatus(@Param("tenantId") UUID tenantId,
                                                                 @Param("reportTypes") Set<WorkOrderType> reportTypes);

    interface WorkOrderTypeStatisticsProjection {
        WorkOrderType getReportType();

        Long getTotal();

        Long getPending();

        Long getResolved();

        Long getClosed();

        Long getBoxQuantityTotal();

        Long getReplacementRequired();

        Long getReplacementCompleted();
    }

    interface WorkOrderStatusStatisticsProjection {
        String getStatus();

        Long getTotal();
    }

    interface AttachmentCountProjection {
        UUID getWorkOrderId();

        Long getTotal();
    }

    @Query("""
            SELECT DISTINCT workOrder.id
            FROM WorkOrder workOrder
            WHERE workOrder.tenantId = :tenantId
              AND (workOrder.reportingDepartmentId = :departmentId
                   OR workOrder.responsibleDepartmentId = :departmentId)
            """)
    Set<UUID> findDistinctIdsByDepartmentReference(
            @Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

}

