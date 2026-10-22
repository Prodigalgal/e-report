package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.reporting.OutstandingWorkOrderReportRow;
import com.pro.ereportbacken.reporting.WorkOrderExportRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WorkOrderReportRepository extends Repository<WorkOrder, UUID> {

    @Query("""
            SELECT new com.pro.ereportbacken.reporting.OutstandingWorkOrderReportRow(
                workOrder.id,
                workOrder.workOrderNo,
                workOrder.reportType,
                workOrder.statusCode,
                workOrder.priority,
                workOrder.location,
                workOrder.reason,
                workOrder.contactPerson,
                workOrder.phone,
                reporter.name,
                reporter.username,
                assignee.name,
                assignee.username,
                workOrder.submittedAt,
                workOrder.lastUpdatedTime,
                workOrder.slaDueAt,
                workOrder.slaBreachedAt
            )
            FROM WorkOrder workOrder
            LEFT JOIN workOrder.reportedBy reporter
            LEFT JOIN workOrder.currentAssignment assignment
            LEFT JOIN assignment.assignee assignee
            WHERE workOrder.tenantId = :tenantId
              AND workOrder.reportingOrgId IN :reportingOrgIds
              AND workOrder.statusCode NOT IN :terminalStatusCodes
            ORDER BY workOrder.submittedAt ASC, workOrder.id ASC
            """)
    List<OutstandingWorkOrderReportRow> findOutstandingRows(
            @Param("tenantId") UUID tenantId,
            @Param("reportingOrgIds") Set<UUID> reportingOrgIds,
            @Param("terminalStatusCodes") Set<String> terminalStatusCodes);

    @Query("""
            SELECT new com.pro.ereportbacken.reporting.WorkOrderExportRow(
                workOrder.id,
                workOrder.workOrderNo,
                workOrder.reportType,
                workOrder.statusCode,
                workOrder.priority,
                workOrder.reportingOrgId,
                workOrder.responsibleOrgId,
                workOrder.location,
                workOrder.reason,
                workOrder.contactPerson,
                workOrder.phone,
                reporter.name,
                reporter.username,
                currentAssignee.name,
                currentAssignee.username,
                pendingAssignee.name,
                pendingAssignee.username,
                workOrder.submittedAt,
                workOrder.lastUpdatedTime,
                workOrder.slaStage,
                workOrder.slaDueAt,
                workOrder.slaBreachedAt,
                workOrder.slaEverBreached,
                workOrder.closedAt,
                workOrder.cancelledAt
            )
            FROM WorkOrder workOrder
            LEFT JOIN workOrder.reportedBy reporter
            LEFT JOIN workOrder.currentAssignment currentAssignment
            LEFT JOIN currentAssignment.assignee currentAssignee
            LEFT JOIN workOrder.pendingAssignment pendingAssignment
            LEFT JOIN pendingAssignment.assignee pendingAssignee
            WHERE workOrder.tenantId = :tenantId
              AND workOrder.id IN :workOrderIds
            """)
    List<WorkOrderExportRow> findExportRows(
            @Param("tenantId") UUID tenantId,
            @Param("workOrderIds") List<UUID> workOrderIds);
}
