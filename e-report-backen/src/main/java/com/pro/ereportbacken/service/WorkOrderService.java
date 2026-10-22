package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.dto.AttachmentDTO;
import com.pro.ereportbacken.model.dto.CreateWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.WorkOrderFilterDTO;
import com.pro.ereportbacken.model.dto.WorkOrderResponseDTO;
import com.pro.ereportbacken.model.dto.MaterialUsageDTO;
import com.pro.ereportbacken.model.dto.RespondWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.UserBasicInfoDTO;
import com.pro.ereportbacken.model.dto.WorkOrderListItemDTO;
import com.pro.ereportbacken.model.dto.WorkOrderStatisticsDTO;
import com.pro.ereportbacken.model.dto.WorkOrderStatisticsScopeDTO;
import com.pro.ereportbacken.model.dto.VerifyWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.VersionedWorkOrderCommandDTO;
import com.pro.ereportbacken.model.dto.AssignWorkOrderRequestDTO;
import com.pro.ereportbacken.model.dto.AssignmentDecisionRequestDTO;
import com.pro.ereportbacken.model.dto.ReasonedWorkOrderCommandDTO;
import com.pro.ereportbacken.model.dto.AssignmentCandidateDTO;
import com.pro.ereportbacken.model.dto.WorkOrderEventDTO;
import com.pro.ereportbacken.model.dto.WorkOrderResponseHistoryDTO;
import com.pro.ereportbacken.model.dto.WorkOrderDepartmentParticipationDTO;
import com.pro.ereportbacken.model.dto.WorkOrderOrganizationParticipationDTO;
import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.model.pojo.AttachmentStage;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.WorkOrderStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.model.pojo.WorkOrderHandlerState;
import com.pro.ereportbacken.model.pojo.MaterialCategory;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.WorkOrderMaterialUsage;
import com.pro.ereportbacken.repository.WorkOrderQueryRepository;
import com.pro.ereportbacken.repository.WorkOrderAssignmentRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.time.DisplayTime;
import com.pro.ereportbacken.security.WorkOrderAction;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class WorkOrderService {

    private static final int DASHBOARD_DAILY_WINDOW_DAYS = 7;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderQueryRepository workOrderQueryRepository;
    private final WorkOrderAssignmentRepository workOrderAssignmentRepository;
    private final WorkOrderAttachmentService workOrderAttachmentService;
    private final OutboxService outboxService;
    private final WorkOrderAccessPolicy workOrderAccessPolicy;
    private final CacheManager cacheManager;
    private final CacheInvalidationService cacheInvalidationService;
    private final WorkOrderLifecycleService lifecycleService;
    private final WorkOrderSlaService slaService;
    private final DashboardOrganizationScopeService dashboardScopeService;
    private final WorkOrderDepartmentParticipationService departmentParticipationService;
    private final WorkOrderOrganizationParticipationService organizationParticipationService;
    private final ImageRedactionService imageRedactionService;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            WorkOrderQueryRepository workOrderQueryRepository,
                            WorkOrderAssignmentRepository workOrderAssignmentRepository,
                            WorkOrderAttachmentService workOrderAttachmentService,
                            OutboxService outboxService,
                            WorkOrderAccessPolicy workOrderAccessPolicy,
                            CacheManager cacheManager,
                            CacheInvalidationService cacheInvalidationService,
                            WorkOrderLifecycleService lifecycleService,
                            WorkOrderSlaService slaService,
                            DashboardOrganizationScopeService dashboardScopeService,
                            WorkOrderDepartmentParticipationService departmentParticipationService,
                            WorkOrderOrganizationParticipationService organizationParticipationService,
                            ImageRedactionService imageRedactionService) {
        this.workOrderRepository = workOrderRepository;
        this.workOrderQueryRepository = workOrderQueryRepository;
        this.workOrderAssignmentRepository = workOrderAssignmentRepository;
        this.workOrderAttachmentService = workOrderAttachmentService;
        this.outboxService = outboxService;
        this.workOrderAccessPolicy = workOrderAccessPolicy;
        this.cacheManager = cacheManager;
        this.cacheInvalidationService = cacheInvalidationService;
        this.lifecycleService = lifecycleService;
        this.slaService = slaService;
        this.dashboardScopeService = dashboardScopeService;
        this.departmentParticipationService = departmentParticipationService;
        this.organizationParticipationService = organizationParticipationService;
        this.imageRedactionService = imageRedactionService;
    }


    @Transactional
    public WorkOrderResponseDTO createWorkOrder(CreateWorkOrderRequestDTO createRequest) {
        User currentUser = workOrderAccessPolicy.currentUserOrThrow();
        WorkOrderType reportType = Objects.requireNonNull(createRequest.getReportType(), "工单类型不能为空。");
        workOrderAccessPolicy.requirePermission(reportType, WorkOrderAction.REPORT);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTenantId(currentUser.getTenantId());
        workOrder.setReportingOrgId(currentUser.getOrgUnitId());
        workOrder.setResponsibleOrgId(currentUser.getOrgUnitId());
        workOrder.setOwnerOrgId(currentUser.getOrgUnitId());
        workOrder.setWorkOrderNo("WO-" + LocalDate.now(DisplayTime.ZONE_ID).toString().replace("-", "") + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        LocalDateTime submittedAt = UtcTime.now();
        workOrder.setSubmittedAt(submittedAt);
        workOrder.transitionTo(WorkOrderStatus.SUBMITTED);
        workOrder.setLocation(createRequest.getLocation().trim());
        workOrder.setGpsCoordinates(normalizeGpsCoordinates(createRequest.getGpsCoordinates()));
        workOrder.setReason(createRequest.getReason().trim());
        workOrder.setPhone(createRequest.getPhone().trim());
        workOrder.setContactPerson(createRequest.getContactPerson().trim());
        workOrder.setReportType(reportType);
        workOrder.setPriority(Objects.requireNonNull(createRequest.getPriority(), "工单优先级不能为空。"));
        slaService.initialize(workOrder, submittedAt);
        reportType.initializeTypeSpecificFields(workOrder, createRequest.getBoxQuantity());
        workOrder.setReportedBy(currentUser);
        workOrderAttachmentService.attachTransferredFiles(
                workOrder,
                createRequest.getUploadedAttachmentIds(),
                AttachmentStage.REPORT,
                true
        );
        WorkOrder savedWorkOrder = workOrderRepository.saveAndFlush(workOrder);
        organizationParticipationService.recordSubmitted(savedWorkOrder, currentUser.getId());
        departmentParticipationService.recordSubmitted(savedWorkOrder, currentUser);
        lifecycleService.recordSubmitted(savedWorkOrder, currentUser);
        outboxService.enqueueWorkOrderCreated(savedWorkOrder);
        clearStatisticsCache();
        log.info("Work order created successfully with ID: {} by user: {}", savedWorkOrder.getId(), currentUser.getUsername());
        return toWorkOrderResponseDTO(savedWorkOrder);
    }


    @Transactional(readOnly = true)
    public Page<WorkOrderListItemDTO> searchVisibleWorkOrders(WorkOrderFilterDTO filterDTO, Pageable pageable) {
        WorkOrderFilterDTO scopedFilter = workOrderAccessPolicy.applySearchScope(filterDTO);
        return findListItems(scopedFilter, pageable);
    }

    private Page<WorkOrderListItemDTO> findListItems(WorkOrderFilterDTO scopedFilter, Pageable pageable) {
        UUID tenantId = workOrderAccessPolicy.currentTenantId();
        Page<UUID> workOrderIdPage = workOrderQueryRepository.findIdsByCriteria(scopedFilter, pageable);

        if (workOrderIdPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, workOrderIdPage.getTotalElements());
        }

        List<UUID> workOrderIds = workOrderIdPage.getContent();
        List<WorkOrder> workOrderRows = workOrderRepository.findListRowsByTenantIdAndIds(tenantId, workOrderIds);
        hydrateActiveAssignments(tenantId, workOrderRows);
        Map<UUID, WorkOrder> workOrderById = workOrderRows.stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
        Map<UUID, Long> attachmentCountByWorkOrderId = workOrderRepository.countAttachmentsByTenantIdAndWorkOrderIdIn(tenantId, workOrderIds).stream()
                .collect(Collectors.toMap(
                        WorkOrderRepository.AttachmentCountProjection::getWorkOrderId,
                        projection -> value(projection.getTotal())
                ));
        Map<UUID, Set<String>> allowedActionsByWorkOrderId =
                workOrderAccessPolicy.allowedActionsByWorkOrderId(workOrderRows);

        List<WorkOrderListItemDTO> orderedRows = workOrderIds.stream()
                .map(workOrderById::get)
                .filter(Objects::nonNull)
                .map(workOrder -> toWorkOrderListItemDTO(
                        workOrder,
                        attachmentCountByWorkOrderId.getOrDefault(workOrder.getId(), 0L),
                        allowedActionsByWorkOrderId.getOrDefault(workOrder.getId(), Set.of())
                ))
                .collect(Collectors.toList());

        return new PageImpl<>(orderedRows, pageable, workOrderIdPage.getTotalElements());
    }

    private WorkOrderListItemDTO toWorkOrderListItemDTO(
            WorkOrder workOrder,
            long attachmentCount,
            Set<String> allowedActions) {
        WorkOrderListItemDTO item = new WorkOrderListItemDTO(
                workOrder.getId(),
                workOrder.getLocation(),
                workOrder.getGpsCoordinates(),
                workOrder.getReason(),
                workOrder.getPhone(),
                workOrder.getContactPerson(),
                workOrder.getStatus(),
                workOrder.type(),
                workOrder.getBoxQuantity(),
                workOrder.getReplacementRequired(),
                workOrder.getReplacementCompleted(),
                workOrder.getReportTime(),
                workOrder.getLastUpdatedTime(),
                workOrder.getResolutionTime(),
                toUserBasicInfoDTO(workOrder.getReportedBy()),
                toUserBasicInfoDTO(workOrder.getResolvedBy()),
                attachmentCount
        );
        WorkOrderStatus lifecycleStatus = workOrder.lifecycleStatus();
        item.setTenantId(workOrder.getTenantId());
        item.setWorkOrderNo(workOrder.getWorkOrderNo());
        item.setStatusCode(lifecycleStatus.getCode());
        item.setStatusLabel(lifecycleStatus.getLabel());
        item.setPhaseCode(resolvePhaseCode(workOrder));
        item.setVersion(workOrder.getVersion());
        item.setResponsibleOrgId(workOrder.getResponsibleOrgId());
        item.setOwnerOrgId(workOrder.getOwnerOrgId());
        item.setResponsibleDepartmentId(workOrder.getResponsibleDepartmentId());
        item.setCurrentAssignment(lifecycleService.assignmentDTO(workOrder.getCurrentAssignment()));
        item.setPendingAssignment(lifecycleService.assignmentDTO(workOrder.getPendingAssignment()));
        applyHandlerProjection(workOrder, item);
        item.setAllowedActions(allowedActions);
        applySlaSnapshot(workOrder, item);
        return item;
    }

    private UserBasicInfoDTO toUserBasicInfoDTO(User user) {
        if (user == null) {
            return null;
        }
        return new UserBasicInfoDTO(user.getId(), user.getUsername(), user.getName());
    }

    @Transactional(readOnly = true)
    public WorkOrderStatisticsDTO getWorkOrderStatistics(UUID countyOrgUnitId) {
        Set<WorkOrderType> allowedTypes = workOrderAccessPolicy.allowedTypesFor(WorkOrderAction.VIEW, WorkOrderAction.SUBMIT_RESPONSE);
        if (allowedTypes.isEmpty()) {
            throw new AccessDeniedException("当前账号没有查看工单仪表盘的权限。");
        }

        var selectedCounty = dashboardScopeService.resolveSelectedCounty(countyOrgUnitId);
        WorkOrderFilterDTO scopedFilter = workOrderAccessPolicy.applySearchScope(new WorkOrderFilterDTO());
        selectedCounty.ifPresent(scope -> scopedFilter.setReportingOrgUnitIds(scope.descendantIds()));
        if (selectedCounty.isEmpty() && !dashboardScopeService.hasTenantWideStatisticsAccess()) {
            dashboardScopeService.currentCountyScope(workOrderAccessPolicy.currentUserOrThrow().getOrgUnitId())
                    .ifPresent(scope -> scopedFilter.setReportingOrgUnitIds(scope.descendantIds()));
        }
        List<OrganizationHierarchyService.CountyScope> comparisonScopes = selectedCounty.isEmpty()
                ? dashboardScopeService.availableCountyScopes()
                : List.of();

        String cacheKey = statisticsCacheKey(allowedTypes, countyOrgUnitId);
        WorkOrderStatisticsDTO cachedStatistics = getCachedStatistics(cacheKey);
        if (cachedStatistics != null) {
            return cachedStatistics;
        }

        WorkOrderStatisticsDTO statistics = buildWorkOrderStatistics(allowedTypes, scopedFilter, comparisonScopes);
        cacheStatistics(cacheKey, statistics);
        return statistics;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderStatisticsScopeDTO> getWorkOrderStatisticsScopes() {
        Set<WorkOrderType> allowedTypes = workOrderAccessPolicy.allowedTypesFor(WorkOrderAction.VIEW, WorkOrderAction.SUBMIT_RESPONSE);
        if (allowedTypes.isEmpty()) {
            throw new AccessDeniedException("当前账号没有查看工单仪表盘的权限。");
        }
        return dashboardScopeService.availableScopes();
    }

    private WorkOrderStatisticsDTO buildWorkOrderStatistics(
            Set<WorkOrderType> allowedTypes,
            WorkOrderFilterDTO scopedFilter,
            List<OrganizationHierarchyService.CountyScope> comparisonScopes) {
        LocalDate today = LocalDate.now(DisplayTime.ZONE_ID);
        LocalDate startDate = today.minusDays(DASHBOARD_DAILY_WINDOW_DAYS - 1L);
        WorkOrderQueryRepository.ScopedStatistics scopedStatistics =
                workOrderQueryRepository.summarizeByCriteria(
                        scopedFilter, startDate, DASHBOARD_DAILY_WINDOW_DAYS);
        Map<WorkOrderType, WorkOrderQueryRepository.TypeSummary> summaryByType = scopedStatistics.byType().stream()
                .collect(Collectors.toMap(WorkOrderQueryRepository.TypeSummary::reportType, Function.identity()));

        List<WorkOrderStatisticsDTO.TypeCountDTO> byType = Arrays.stream(WorkOrderType.values())
                .filter(allowedTypes::contains)
                .map(type -> toTypeCountDTO(type, summaryByType.get(type)))
                .toList();

        long total = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::total).sum();
        long pending = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::pending).sum();
        long resolved = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::resolved).sum();
        long closed = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::closed).sum();
        long boxQuantityTotal = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::boxQuantityTotal).sum();
        long replacementRequired = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::replacementRequired).sum();
        long replacementCompleted = byType.stream().mapToLong(WorkOrderStatisticsDTO.TypeCountDTO::replacementCompleted).sum();
        long otherStatusTotal = Math.max(0, total - pending - resolved - closed);
        WorkOrderQueryRepository.SlaSummary sla = scopedStatistics.sla();
        long longestActiveElapsedSeconds = sla.oldestActiveSubmittedAt() == null
                ? 0L
                : Math.max(0L, java.time.Duration.between(sla.oldestActiveSubmittedAt(), UtcTime.now()).getSeconds());

        List<WorkOrderStatisticsDTO.StatusCountDTO> byStatus = scopedStatistics.byStatus().stream()
                .map(status -> new WorkOrderStatisticsDTO.StatusCountDTO(
                        WorkOrderStatus.fromValue(status.statusCode()).getLabel(),
                        status.total()))
                .sorted(Comparator.comparingInt(status -> statusRank(status.status())))
                .toList();

        return new WorkOrderStatisticsDTO(
                total,
                pending,
                resolved,
                closed,
                otherStatusTotal,
                resolutionRate(total, resolved + closed),
                boxQuantityTotal,
                replacementRequired,
                replacementCompleted,
                sla.active(),
                sla.within24Hours(),
                sla.from24To48Hours(),
                sla.from48To72Hours(),
                sla.over72Hours(),
                sla.currentSlaBreached(),
                sla.everSlaBreached(),
                longestActiveElapsedSeconds,
                byType,
                byStatus,
                buildDailyNewReports(startDate, today, scopedStatistics.daily()),
                buildCountyComparison(scopedFilter, comparisonScopes)
        );
    }

    private List<WorkOrderStatisticsDTO.CountyComparisonDTO> buildCountyComparison(
            WorkOrderFilterDTO scopedFilter,
            List<OrganizationHierarchyService.CountyScope> comparisonScopes) {
        if (comparisonScopes.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrganizationHierarchyService.CountyScope> scopeByOrgUnitId = new java.util.HashMap<>();
        comparisonScopes.forEach(scope -> scope.descendantIds().forEach(orgUnitId ->
                scopeByOrgUnitId.putIfAbsent(orgUnitId, scope)));

        Map<UUID, long[]> totalsByCountyId = new LinkedHashMap<>();
        comparisonScopes.forEach(scope -> totalsByCountyId.put(scope.county().id(), new long[5]));
        for (WorkOrderQueryRepository.ReportingOrganizationSummary summary
                : workOrderQueryRepository.summarizeReportingOrganizations(scopedFilter, UtcTime.now())) {
            OrganizationHierarchyService.CountyScope scope = scopeByOrgUnitId.get(summary.reportingOrgUnitId());
            if (scope == null) {
                continue;
            }
            long[] totals = totalsByCountyId.get(scope.county().id());
            totals[0] += summary.total();
            totals[1] += summary.active();
            totals[2] += summary.closed();
            totals[3] += summary.currentSlaBreached();
            totals[4] += summary.over72Hours();
        }
        return comparisonScopes.stream().map(scope -> {
            long[] totals = totalsByCountyId.get(scope.county().id());
            return new WorkOrderStatisticsDTO.CountyComparisonDTO(
                    scope.county().id(),
                    scope.county().name(),
                    totals[0],
                    totals[1],
                    totals[2],
                    resolutionRate(totals[0], totals[2]),
                    totals[3],
                    totals[4]
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderResponseDTO getWorkOrderById(UUID id) {
        WorkOrder workOrder = findDetailedWorkOrderById(id);
        workOrderAccessPolicy.requireReadAccess(workOrder);
        return toWorkOrderResponseDTO(workOrder);
    }

    @Transactional
    public WorkOrderResponseDTO claimWorkOrder(UUID id, VersionedWorkOrderCommandDTO request) {
        return lifecycleResult(lifecycleService.claim(id, request.getExpectedVersion()));
    }

    @Transactional
    public WorkOrderResponseDTO assignWorkOrder(UUID id, AssignWorkOrderRequestDTO request) {
        return lifecycleResult(lifecycleService.assign(id, request));
    }

    @Transactional
    public WorkOrderResponseDTO reassignWorkOrder(UUID id, AssignWorkOrderRequestDTO request) {
        return lifecycleResult(lifecycleService.reassign(id, request));
    }

    @Transactional
    public WorkOrderResponseDTO acceptAssignment(UUID id, UUID assignmentId, AssignmentDecisionRequestDTO request) {
        return lifecycleResult(lifecycleService.acceptAssignment(id, assignmentId, request));
    }

    @Transactional
    public WorkOrderResponseDTO rejectAssignment(UUID id, UUID assignmentId, AssignmentDecisionRequestDTO request) {
        return lifecycleResult(lifecycleService.rejectAssignment(id, assignmentId, request));
    }

    @Transactional
    public WorkOrderResponseDTO verifyWorkOrder(UUID id, VerifyWorkOrderRequestDTO request) {
        return lifecycleResult(lifecycleService.verify(id, request));
    }

    @Transactional
    public WorkOrderResponseDTO holdWorkOrder(UUID id, ReasonedWorkOrderCommandDTO request) {
        return lifecycleResult(lifecycleService.hold(id, request));
    }

    @Transactional
    public WorkOrderResponseDTO resumeWorkOrder(UUID id, VersionedWorkOrderCommandDTO request) {
        return lifecycleResult(lifecycleService.resume(id, request));
    }

    @Transactional
    public WorkOrderResponseDTO cancelWorkOrder(UUID id, ReasonedWorkOrderCommandDTO request) {
        return lifecycleResult(lifecycleService.cancel(id, request));
    }

    @Transactional(readOnly = true)
    public List<AssignmentCandidateDTO> assignmentCandidates(UUID id, String searchTerm) {
        return lifecycleService.assignmentCandidates(id, searchTerm);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderEventDTO> workOrderEvents(UUID id, Pageable pageable) {
        return lifecycleService.events(id, pageable);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderResponseHistoryDTO> workOrderResponses(UUID id) {
        return lifecycleService.responses(id);
    }

    private WorkOrderResponseDTO lifecycleResult(WorkOrder workOrder) {
        clearStatisticsCache();
        return toWorkOrderResponseDTO(workOrder);
    }

    @Transactional
    public WorkOrderResponseDTO respondToWorkOrder(UUID id, RespondWorkOrderRequestDTO responseRequest) {
        WorkOrder savedWorkOrder = lifecycleService.submitResponse(id, responseRequest);
        clearStatisticsCache();
        return toWorkOrderResponseDTO(savedWorkOrder);
    }

    private void applyMaterialUsages(WorkOrder workOrder, List<MaterialUsageDTO> materialUsageDTOs) {
        workOrder.getMaterialUsages().clear();
        if (materialUsageDTOs == null || materialUsageDTOs.isEmpty()) {
            return;
        }
        for (MaterialUsageDTO dto : materialUsageDTOs) {
            validateMaterialUsage(dto);
            WorkOrderMaterialUsage materialUsage = new WorkOrderMaterialUsage();
            materialUsage.setCategory(dto.getCategory());
            materialUsage.setSpecification(dto.getSpecification());
            materialUsage.setQuantity(dto.getQuantity());
            workOrder.addMaterialUsage(materialUsage);
        }
    }

    private void validateMaterialUsage(MaterialUsageDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("物料明细不能为空。");
        }
        MaterialCategory category = dto.getCategory();
        if (category == null) {
            throw new IllegalArgumentException("物料类别不能为空。");
        }
        if (!StringUtils.hasText(dto.getSpecification())) {
            throw new IllegalArgumentException("物料规格不能为空。");
        }
        if (!category.supports(dto.getSpecification())) {
            throw new IllegalArgumentException("物料规格 " + dto.getSpecification() + " 不属于 " + category.getDisplayName() + "。");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new IllegalArgumentException("物料数量必须大于0。");
        }
    }

    private WorkOrder findWorkOrderById(UUID id) {
        return workOrderRepository.findByTenantIdAndId(workOrderAccessPolicy.currentTenantId(), id)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found with id: " + id));
    }

    private WorkOrder findDetailedWorkOrderById(UUID id) {
        WorkOrder workOrder = findWorkOrderById(id);
        workOrderRepository.findWithMaterialUsagesByTenantIdAndId(workOrderAccessPolicy.currentTenantId(), id)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found with id: " + id));
        hydrateActiveAssignments(workOrder.getTenantId(), List.of(workOrder));
        return workOrder;
    }

    /**
     * 活动处理人以 assignment 的状态和槽位为事实来源，修复历史工单快照指针缺失或失效时的详情、列表展示。
     */
    private void hydrateActiveAssignments(UUID tenantId, List<WorkOrder> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) {
            return;
        }
        List<UUID> workOrderIds = workOrders.stream()
                .map(WorkOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        if (workOrderIds.isEmpty()) {
            return;
        }
        Map<UUID, WorkOrderAssignment> currentByWorkOrder = new HashMap<>();
        Map<UUID, WorkOrderAssignment> pendingByWorkOrder = new HashMap<>();
        for (WorkOrderAssignment assignment : workOrderAssignmentRepository
                .findActiveByTenantIdAndWorkOrderIdIn(tenantId, workOrderIds)) {
            if (assignment.getWorkOrder() == null || assignment.getWorkOrder().getId() == null) {
                continue;
            }
            UUID workOrderId = assignment.getWorkOrder().getId();
            if (assignment.isCurrent()) {
                currentByWorkOrder.put(workOrderId, assignment);
            } else if (assignment.isPending()) {
                pendingByWorkOrder.put(workOrderId, assignment);
            }
        }
        for (WorkOrder workOrder : workOrders) {
            workOrder.setCurrentAssignment(currentByWorkOrder.get(workOrder.getId()));
            workOrder.setPendingAssignment(pendingByWorkOrder.get(workOrder.getId()));
        }
    }

    private WorkOrderResponseDTO toWorkOrderResponseDTO(WorkOrder workOrder) {
        WorkOrderType reportType = workOrder.type();
        boolean attachmentReadable = workOrderAccessPolicy.hasActionAccess(
                workOrder, WorkOrderAction.READ_ATTACHMENT);
        Map<UUID, ImageRedactionService.ImageRedactionState> redactionStates = attachmentReadable
                ? imageRedactionService.states(
                workOrder.getAttachments().stream()
                        .map(Attachment::getImageRedactionJobId)
                        .filter(Objects::nonNull)
                        .toList())
                : Map.of();
        List<AttachmentDTO> attachments = attachmentReadable
                ? workOrder.getAttachments().stream()
                .map(attachment -> toAttachmentDTO(
                        attachment,
                        redactionStates.get(attachment.getImageRedactionJobId())))
                .toList()
                : List.of();
        List<MaterialUsageDTO> materialUsages = workOrder.getMaterialUsages().stream()
                .map(this::toMaterialUsageDTO)
                .toList();
        WorkOrderResponseDTO response = new WorkOrderResponseDTO(
                workOrder.getId(),
                workOrder.getLocation(),
                workOrder.getGpsCoordinates(),
                workOrder.getReason(),
                workOrder.getPhone(),
                workOrder.getContactPerson(),
                workOrder.getStatus(),
                workOrder.getReportTime(),
                workOrder.getLastUpdatedTime(),
                workOrder.getResolutionDetails(),
                workOrder.getResolutionMethod(),
                workOrder.getResolutionTime(),
                workOrder.getClosureReason(),
                workOrder.getClosureTime(),
                toUserBasicInfoDTO(workOrder.getReportedBy()),
                toUserBasicInfoDTO(workOrder.getResolvedBy()),
                toUserBasicInfoDTO(workOrder.getClosedBy()),
                attachments
        );
        response.setReportType(reportType.name());
        response.setReportTypeName(reportType.getDisplayName());
        response.setBoxQuantity(workOrder.getBoxQuantity());
        response.setReplacementRequired(workOrder.getReplacementRequired());
        response.setReplacementCompleted(workOrder.getReplacementCompleted());
        response.setReplacementResult(workOrder.getReplacementResult());
        response.setMaterialUsages(materialUsages);
        WorkOrderStatus lifecycleStatus = workOrder.lifecycleStatus();
        response.setTenantId(workOrder.getTenantId());
        response.setWorkOrderNo(workOrder.getWorkOrderNo());
        response.setStatusCode(lifecycleStatus.getCode());
        response.setStatusLabel(lifecycleStatus.getLabel());
        response.setPhaseCode(resolvePhaseCode(workOrder));
        response.setVersion(workOrder.getVersion());
        response.setReportingOrgId(workOrder.getReportingOrgId());
        response.setResponsibleOrgId(workOrder.getResponsibleOrgId());
        response.setOwnerOrgId(workOrder.getOwnerOrgId());
        response.setReportingDepartmentId(workOrder.getReportingDepartmentId());
        response.setResponsibleDepartmentId(workOrder.getResponsibleDepartmentId());
        response.setDepartmentParticipations(departmentParticipationService
                .history(workOrder.getTenantId(), workOrder.getId()).stream()
                .map(participation -> new WorkOrderDepartmentParticipationDTO(
                        participation.getId(), participation.getDepartmentId(),
                        participation.getDepartmentNameSnapshot(), participation.getRelationType().name(),
                        Boolean.TRUE.equals(participation.getActive()), participation.getStartedAt(),
                        participation.getEndedAt(), participation.getReason()))
                .toList());
        response.setOrganizationParticipations(organizationParticipationService
                .history(workOrder.getTenantId(), workOrder.getId()).stream()
                .map(participation -> new WorkOrderOrganizationParticipationDTO(
                        participation.getId(), participation.getOrgUnitId(),
                        participation.getOrganizationNameSnapshot(), participation.getRelationType().name(),
                        Boolean.TRUE.equals(participation.getActive()), participation.getStartedAt(),
                        participation.getEndedAt(), participation.getReason()))
                .toList());
        response.setReworkCount(workOrder.getReworkCount());
        response.setAllowedActions(workOrderAccessPolicy.allowedActions(workOrder));
        response.setAllowedViews(workOrderAccessPolicy.allowedViews(workOrder));
        response.setCurrentAssignment(lifecycleService.assignmentDTO(workOrder.getCurrentAssignment()));
        response.setPendingAssignment(lifecycleService.assignmentDTO(workOrder.getPendingAssignment()));
        applyHandlerProjection(workOrder, response);
        response.setCurrentResponseId(workOrder.getCurrentResponse() == null ? null : workOrder.getCurrentResponse().getId());
        applySlaSnapshot(workOrder, response);
        return response;
    }

    private void applySlaSnapshot(WorkOrder workOrder, WorkOrderListItemDTO target) {
        WorkOrderSlaService.SlaSnapshot snapshot = slaService.snapshot(workOrder, UtcTime.now());
        target.setPriority(snapshot.priority().name());
        target.setPriorityName(snapshot.priority().getDisplayName());
        target.setSlaStage(snapshot.stage().name());
        target.setSlaStageName(snapshot.stage().getDisplayName());
        target.setSlaRiskLevel(snapshot.riskLevel().name());
        target.setSlaRiskName(snapshot.riskLevel().getDisplayName());
        target.setSlaDueAt(snapshot.dueAt());
        target.setSlaPaused(snapshot.paused());
        target.setSlaBreached(snapshot.breached());
        target.setSlaEverBreached(snapshot.everBreached());
        target.setSlaRemainingSeconds(snapshot.remainingSeconds());
        target.setSlaBreachSeconds(snapshot.breachSeconds());
        target.setElapsedSeconds(snapshot.elapsedSeconds());
        target.setTotalHoldSeconds(snapshot.totalHoldSeconds());
    }

    private void applySlaSnapshot(WorkOrder workOrder, WorkOrderResponseDTO target) {
        WorkOrderSlaService.SlaSnapshot snapshot = slaService.snapshot(workOrder, UtcTime.now());
        target.setPriority(snapshot.priority().name());
        target.setPriorityName(snapshot.priority().getDisplayName());
        target.setSlaStage(snapshot.stage().name());
        target.setSlaStageName(snapshot.stage().getDisplayName());
        target.setSlaRiskLevel(snapshot.riskLevel().name());
        target.setSlaRiskName(snapshot.riskLevel().getDisplayName());
        target.setSlaDueAt(snapshot.dueAt());
        target.setSlaPaused(snapshot.paused());
        target.setSlaBreached(snapshot.breached());
        target.setSlaEverBreached(snapshot.everBreached());
        target.setSlaRemainingSeconds(snapshot.remainingSeconds());
        target.setSlaBreachSeconds(snapshot.breachSeconds());
        target.setElapsedSeconds(snapshot.elapsedSeconds());
        target.setTotalHoldSeconds(snapshot.totalHoldSeconds());
    }

    private void applyHandlerProjection(WorkOrder workOrder, WorkOrderListItemDTO target) {
        WorkOrderHandlerProjection projection = resolveHandler(workOrder);
        if (projection == null) {
            return;
        }
        target.setHandler(toUserBasicInfoDTO(projection.user()));
        target.setHandlerState(projection.state().name());
        target.setHandlerStateName(projection.state().getDisplayName());
    }

    private void applyHandlerProjection(WorkOrder workOrder, WorkOrderResponseDTO target) {
        WorkOrderHandlerProjection projection = resolveHandler(workOrder);
        if (projection == null) {
            return;
        }
        target.setHandler(toUserBasicInfoDTO(projection.user()));
        target.setHandlerState(projection.state().name());
        target.setHandlerStateName(projection.state().getDisplayName());
    }

    private WorkOrderHandlerProjection resolveHandler(WorkOrder workOrder) {
        if (workOrder.getCurrentAssignment() != null && workOrder.getCurrentAssignment().isCurrent()) {
            return new WorkOrderHandlerProjection(
                    workOrder.getCurrentAssignment().getAssignee(),
                    WorkOrderHandlerState.CURRENT
            );
        }
        if (workOrder.getPendingAssignment() != null && workOrder.getPendingAssignment().isPending()) {
            return new WorkOrderHandlerProjection(
                    workOrder.getPendingAssignment().getAssignee(),
                    WorkOrderHandlerState.PENDING_ACCEPTANCE
            );
        }
        if (workOrder.getCurrentResponse() != null) {
            return new WorkOrderHandlerProjection(
                    workOrder.getCurrentResponse().getSubmittedBy(),
                    WorkOrderHandlerState.RESPONDED
            );
        }
        if (workOrder.getResolvedBy() != null) {
            return new WorkOrderHandlerProjection(workOrder.getResolvedBy(), WorkOrderHandlerState.RESPONDED);
        }
        return null;
    }

    private record WorkOrderHandlerProjection(User user, WorkOrderHandlerState state) {
    }

    private String resolvePhaseCode(WorkOrder workOrder) {
        boolean hasCurrentAssignment = workOrder.getCurrentAssignment() != null
                && workOrder.getCurrentAssignment().isCurrent();
        boolean hasPendingAssignment = workOrder.getPendingAssignment() != null
                && workOrder.getPendingAssignment().isPending();
        return switch (workOrder.lifecycleStatus()) {
            case SUBMITTED -> hasPendingAssignment ? "PENDING_ACCEPTANCE" : "CLAIMABLE";
            case IN_PROGRESS -> hasPendingAssignment ? "REASSIGNING" : hasCurrentAssignment ? "PROCESSING" : "CLAIMABLE";
            case PENDING_VERIFICATION -> "PENDING_VERIFICATION";
            case ON_HOLD -> "ON_HOLD";
            case CLOSED -> "CLOSED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private AttachmentDTO toAttachmentDTO(
            Attachment attachment,
            ImageRedactionService.ImageRedactionState redactionState) {
        String stage = attachment.getAttachmentStage() == null
                ? AttachmentStage.REPORT.name()
                : attachment.getAttachmentStage().name();
        String fileName = redactionState != null && redactionState.outputOriginalFileName() != null
                ? redactionState.outputOriginalFileName()
                : attachment.getFileName();
        String storedFileName = redactionState != null && redactionState.outputStoredFileName() != null
                ? redactionState.outputStoredFileName()
                : attachment.getStoredFileName();
        String fileType = redactionState != null && redactionState.outputContentType() != null
                ? redactionState.outputContentType()
                : attachment.getFileType();
        long fileSize = redactionState != null && redactionState.outputFileSize() != null
                ? redactionState.outputFileSize()
                : attachment.getFileSize();
        return new AttachmentDTO(
                attachment.getId(),
                fileName,
                storedFileName,
                fileType,
                fileSize,
                stage,
                redactionState == null || redactionState.status() == null ? null : redactionState.status().name(),
                redactionState == null ? null : redactionState.message()
        );
    }

    private MaterialUsageDTO toMaterialUsageDTO(WorkOrderMaterialUsage materialUsage) {
        MaterialCategory category = materialUsage.getCategory();
        return new MaterialUsageDTO(
                category,
                category == null ? null : category.getDisplayName(),
                materialUsage.getSpecification(),
                materialUsage.getQuantity()
        );
    }

    private WorkOrderStatisticsDTO.TypeCountDTO toTypeCountDTO(
            WorkOrderType type,
            WorkOrderQueryRepository.TypeSummary summary) {
        if (summary == null) {
            return new WorkOrderStatisticsDTO.TypeCountDTO(type, type.getDisplayName(), 0, 0, 0, 0, 0, 0, 0);
        }
        return new WorkOrderStatisticsDTO.TypeCountDTO(
                type,
                type.getDisplayName(),
                summary.total(),
                summary.pending(),
                summary.resolved(),
                summary.closed(),
                summary.boxQuantityTotal(),
                summary.replacementRequired(),
                summary.replacementCompleted()
        );
    }

    private List<WorkOrderStatisticsDTO.DailyCountDTO> buildDailyNewReports(
            LocalDate startDate,
            LocalDate today,
            Map<LocalDate, Long> scopedDailyCounts) {
        Map<LocalDate, Long> dailyCounts = new LinkedHashMap<>();
        for (int dayOffset = 0; dayOffset < DASHBOARD_DAILY_WINDOW_DAYS; dayOffset++) {
            dailyCounts.put(startDate.plusDays(dayOffset), 0L);
        }

        scopedDailyCounts.forEach((reportDate, total) -> {
            if (!reportDate.isBefore(startDate) && !reportDate.isAfter(today)) {
                dailyCounts.put(reportDate, total);
            }
        });

        return dailyCounts.entrySet().stream()
                .map(entry -> new WorkOrderStatisticsDTO.DailyCountDTO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private double resolutionRate(long total, long handledTotal) {
        if (total <= 0) {
            return 0;
        }
        return Math.round((handledTotal * 1000.0) / total) / 10.0;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private int statusRank(String status) {
        if (WorkOrderStatus.SUBMITTED.matches(status)) {
            return 0;
        }
        if (WorkOrderStatus.PENDING_VERIFICATION.matches(status)) {
            return 1;
        }
        if (WorkOrderStatus.CLOSED.matches(status)) {
            return 2;
        }
        return 3;
    }

    private String statisticsCacheKey(Set<WorkOrderType> allowedTypes, UUID countyOrgUnitId) {
        return "tenant:" + workOrderAccessPolicy.currentTenantId()
                + ":membership:" + workOrderAccessPolicy.currentMembershipId()
                + ":auth:" + workOrderAccessPolicy.currentAuthorizationVersion()
                + ":county:" + (countyOrgUnitId == null ? "ALL" : countyOrgUnitId)
                + ":types:" + allowedTypes.stream()
                .map(WorkOrderType::name)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private void clearStatisticsCache() {
        cacheInvalidationService.clearAfterCommit(CacheNames.WORK_ORDER_STATISTICS);
    }

    private WorkOrderStatisticsDTO getCachedStatistics(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.WORK_ORDER_STATISTICS);
            return cache == null ? null : cache.get(cacheKey, WorkOrderStatisticsDTO.class);
        } catch (RuntimeException e) {
            log.warn("Failed to read work order statistics cache. Falling back to database query.", e);
            return null;
        }
    }

    private void cacheStatistics(String cacheKey, WorkOrderStatisticsDTO statistics) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.WORK_ORDER_STATISTICS);
            if (cache != null) {
                cache.put(cacheKey, statistics);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to write work order statistics cache. Continue without cached statistics.", e);
        }
    }

    private String normalizeGpsCoordinates(String coordinates) {
        if (!StringUtils.hasText(coordinates)) {
            throw new IllegalArgumentException("定位坐标不能为空。");
        }
        String[] parts = coordinates.trim().split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("定位坐标格式不正确，请按“经度,纬度”填写。");
        }
        try {
            double longitude = Double.parseDouble(parts[0].trim());
            double latitude = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("定位坐标超出有效范围。");
            }
            return parts[0].trim() + "," + parts[1].trim();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("定位坐标格式不正确，请按“经度,纬度”填写。", exception);
        }
    }

}

