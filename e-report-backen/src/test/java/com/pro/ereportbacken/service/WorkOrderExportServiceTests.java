package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.pojo.WorkOrderPriority;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.reporting.WorkOrderExportRow;
import com.pro.ereportbacken.reporting.WorkOrderSpreadsheetExporter;
import com.pro.ereportbacken.repository.WorkOrderQueryRepository;
import com.pro.ereportbacken.repository.WorkOrderReportRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderExportServiceTests {

    @Test
    void exportsOnlySelectedIdsAfterApplyingTheExistingExportScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        WorkOrderAccessPolicy accessPolicy = mock(WorkOrderAccessPolicy.class);
        WorkOrderQueryRepository queryRepository = mock(WorkOrderQueryRepository.class);
        WorkOrderReportRepository reportRepository = mock(WorkOrderReportRepository.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
        filter.setSelectedWorkOrderIds(new LinkedHashSet<>(List.of(firstId, secondId)));
        when(accessPolicy.applyExportScope(filter)).thenReturn(filter);
        when(accessPolicy.currentTenantId()).thenReturn(tenantId);
        when(queryRepository.findIdsByCriteria(any(), any())).thenReturn(new PageImpl<>(
                List.of(firstId, secondId), PageRequest.of(0, 500), 2));
        when(reportRepository.findExportRows(eq(tenantId), eq(List.of(firstId, secondId))))
                .thenReturn(List.of(row(firstId, organizationId), row(secondId, organizationId)));
        when(hierarchyService.namesById(eq(tenantId), any())).thenReturn(Map.of(organizationId, "南靖县"));

        WorkOrderExportService.PreparedWorkOrderExport prepared = new WorkOrderExportService(
                accessPolicy,
                queryRepository,
                reportRepository,
                hierarchyService,
                new WorkOrderSpreadsheetExporter(),
                50_000
        ).prepare(filter, Sort.by(Sort.Order.desc("reportTime")));

        try {
            assertThat(prepared.rowCount()).isEqualTo(2);
            assertThat(Files.size(prepared.path())).isPositive();
        } finally {
            Files.deleteIfExists(prepared.path());
        }
        ArgumentCaptor<WorkOrderFilterDTO> filterCaptor = ArgumentCaptor.forClass(WorkOrderFilterDTO.class);
        verify(queryRepository).findIdsByCriteria(filterCaptor.capture(), any());
        assertThat(filterCaptor.getValue().getSelectedWorkOrderIds())
                .containsExactly(firstId, secondId);
    }

    @Test
    void rejectsAnySelectedIdOutsideTheEffectiveExportScope() {
        UUID tenantId = UUID.randomUUID();
        UUID visibleId = UUID.randomUUID();
        UUID outsideScopeId = UUID.randomUUID();
        WorkOrderAccessPolicy accessPolicy = mock(WorkOrderAccessPolicy.class);
        WorkOrderQueryRepository queryRepository = mock(WorkOrderQueryRepository.class);
        WorkOrderFilterDTO filter = new WorkOrderFilterDTO();
        filter.setSelectedWorkOrderIds(new LinkedHashSet<>(List.of(visibleId, outsideScopeId)));
        when(accessPolicy.applyExportScope(filter)).thenReturn(filter);
        when(accessPolicy.currentTenantId()).thenReturn(tenantId);
        when(queryRepository.findIdsByCriteria(any(), any())).thenReturn(new PageImpl<>(
                List.of(visibleId), PageRequest.of(0, 500), 1));

        WorkOrderExportService service = new WorkOrderExportService(
                accessPolicy,
                queryRepository,
                mock(WorkOrderReportRepository.class),
                mock(OrganizationHierarchyService.class),
                new WorkOrderSpreadsheetExporter(),
                50_000
        );

        assertThatThrownBy(() -> service.prepare(filter, Sort.by(Sort.Order.desc("reportTime"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("不在当前数据域");
    }

    private WorkOrderExportRow row(UUID id, UUID organizationId) {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 10, 8, 0);
        return new WorkOrderExportRow(
                id,
                "WO-20260910-" + id.toString().substring(0, 8),
                WorkOrderType.NETWORK_FAULT,
                WorkOrderStatus.SUBMITTED.getCode(),
                WorkOrderPriority.ROUTINE,
                organizationId,
                organizationId,
                "南靖县测试点",
                "光缆现场故障",
                "测试联系人",
                "15900000000",
                "测试提单人",
                "reporter",
                null,
                null,
                null,
                null,
                submittedAt,
                submittedAt,
                null,
                submittedAt.plusHours(24),
                null,
                false,
                null,
                null
        );
    }
}
