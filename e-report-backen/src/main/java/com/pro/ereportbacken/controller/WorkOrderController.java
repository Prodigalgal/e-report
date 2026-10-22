package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.*;
import com.pro.ereportbacken.service.FileDownloadResponseFactory;
import com.pro.ereportbacken.service.WorkOrderAttachmentService;
import com.pro.ereportbacken.service.WorkOrderService;
import com.pro.ereportbacken.service.WorkOrderExportService;
import com.pro.ereportbacken.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/work-orders")
@Log4j2
public class WorkOrderController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "reportTime", "submittedAt", "lastUpdatedTime", "slaDueAt",
            "statusCode", "status", "priority", "workOrderNo", "location",
            "contactPerson", "phone", "reportType", "resolutionTime", "closedAt", "id"
    );

    private final WorkOrderService workOrderService;
    private final WorkOrderAttachmentService workOrderAttachmentService;
    private final FileDownloadResponseFactory fileDownloadResponseFactory;
    private final IdempotencyService idempotencyService;
    private final WorkOrderExportService workOrderExportService;

    public WorkOrderController(WorkOrderService workOrderService,
                               WorkOrderAttachmentService workOrderAttachmentService,
                               FileDownloadResponseFactory fileDownloadResponseFactory,
                               IdempotencyService idempotencyService,
                               WorkOrderExportService workOrderExportService) {
        this.workOrderService = workOrderService;
        this.workOrderAttachmentService = workOrderAttachmentService;
        this.fileDownloadResponseFactory = fileDownloadResponseFactory;
        this.idempotencyService = idempotencyService;
        this.workOrderExportService = workOrderExportService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkOrderResponseDTO> createWorkOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateWorkOrderRequestDTO createRequest) {
        WorkOrderResponseDTO response = idempotencyService.execute(
                idempotencyKey,
                "POST:/work-orders",
                createRequest,
                WorkOrderResponseDTO.class,
                () -> workOrderService.createWorkOrder(createRequest));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/statistics")
    public ResponseEntity<WorkOrderStatisticsDTO> getWorkOrderStatistics(
            @RequestParam(required = false) UUID countyOrgUnitId) {
        return ResponseEntity.ok(workOrderService.getWorkOrderStatistics(countyOrgUnitId));
    }

    @GetMapping("/statistics/scopes")
    public ResponseEntity<List<WorkOrderStatisticsScopeDTO>> getWorkOrderStatisticsScopes() {
        return ResponseEntity.ok(workOrderService.getWorkOrderStatisticsScopes());
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponseDTO<WorkOrderListItemDTO>> searchWorkOrders(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String location,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String reason,
            @RequestParam(name = "reportType", required = false) List<String> reportTypes,
            @RequestParam(required = false) String reportDateFrom,
            @RequestParam(required = false) String reportDateTo,
            @RequestParam(name = "queue", required = false) List<String> queues,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "reportTime,desc") String[] sort) {
        WorkOrderFilterDTO filterDTO = buildFilter(
                searchTerm,
                location,
                statuses,
                contactPerson,
                phone,
                reason,
                reportTypes,
                reportDateFrom,
                reportDateTo
        );
        filterDTO.setQueues(normalizeFilterValues(queues));
        Pageable pageable = buildPageable(page, size, sort);
        return ResponseEntity.ok(PageResponseDTO.from(
                workOrderService.searchVisibleWorkOrders(filterDTO, pageable)));
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportWorkOrders(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String location,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String reason,
            @RequestParam(name = "reportType", required = false) List<String> reportTypes,
            @RequestParam(required = false) String reportDateFrom,
            @RequestParam(required = false) String reportDateTo,
            @RequestParam(name = "queue", required = false) List<String> queues,
            @RequestParam(name = "workOrderId", required = false) List<UUID> workOrderIds,
            @RequestParam(defaultValue = "reportTime,desc") String[] sort) {
        WorkOrderFilterDTO filterDTO = buildFilter(
                searchTerm, location, statuses, contactPerson, phone, reason,
                reportTypes, reportDateFrom, reportDateTo);
        filterDTO.setQueues(normalizeFilterValues(queues));
        Set<UUID> selectedWorkOrderIds = normalizeWorkOrderIds(workOrderIds);
        if (selectedWorkOrderIds != null) {
            filterDTO.setSelectedWorkOrderIds(selectedWorkOrderIds);
        }
        WorkOrderExportService.PreparedWorkOrderExport export = workOrderExportService.prepare(
                filterDTO, buildPageable(0, MAX_PAGE_SIZE, sort).getSort());
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(export.path())) {
                inputStream.transferTo(outputStream);
            } finally {
                try {
                    Files.deleteIfExists(export.path());
                } catch (IOException exception) {
                    log.warn("清理已响应的工单导出临时文件失败。file={}", export.path(), exception);
                }
            }
        };
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(WorkOrderExportService.XLSX_MEDIA_TYPE))
                .contentLength(export.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-File-Sha256", export.sha256())
                .header("X-Export-Row-Count", String.valueOf(export.rowCount()))
                .body(body);
    }

    private Set<UUID> normalizeWorkOrderIds(List<UUID> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            return null;
        }
        Set<UUID> normalized = new LinkedHashSet<>();
        for (UUID workOrderId : workOrderIds) {
            if (workOrderId != null) {
                normalized.add(workOrderId);
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private WorkOrderFilterDTO buildFilter(String searchTerm,
                                             String location,
                                             List<String> statuses,
                                             String contactPerson,
                                             String phone,
                                             String reason,
                                             List<String> reportTypes,
                                             String reportDateFrom,
                                             String reportDateTo) {
        WorkOrderFilterDTO filterDTO = new WorkOrderFilterDTO();
        filterDTO.setSearchTerm(searchTerm);
        filterDTO.setLocation(location);
        filterDTO.setStatuses(normalizeFilterValues(statuses));
        filterDTO.setContactPerson(contactPerson);
        filterDTO.setPhone(phone);
        filterDTO.setReason(reason);
        filterDTO.setReportTypes(normalizeFilterValues(reportTypes));
        filterDTO.setReportDateFrom(reportDateFrom);
        filterDTO.setReportDateTo(reportDateTo);
        return filterDTO;
    }

    private Set<String> normalizeFilterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String candidate = part.trim();
                if (candidate.equalsIgnoreCase("all")) {
                    return Collections.emptySet();
                }
                if (!candidate.isEmpty()) {
                    normalized.add(candidate);
                }
            }
        }
        return normalized;
    }

    private Pageable buildPageable(int page, int size, String[] sort) {
        List<Sort.Order> orders = new ArrayList<>();
        List<String> sortSpecifications = normalizeSortSpecifications(sort);
        if (!sortSpecifications.isEmpty()) {
            for (String sortOrder : sortSpecifications) {
                if (sortOrder == null || sortOrder.isBlank()) {
                    continue;
                }
                String[] sortParts = sortOrder.split(",");
                String property = sortParts[0].trim();
                if (!ALLOWED_SORT_FIELDS.contains(property)) {
                    throw new IllegalArgumentException("不支持按该字段排序：" + property);
                }
                String directionValue = sortParts.length > 1 ? sortParts[1].trim() : "desc";
                Sort.Direction direction;
                if (directionValue.equalsIgnoreCase("asc")) {
                    direction = Sort.Direction.ASC;
                } else if (directionValue.equalsIgnoreCase("desc")) {
                    direction = Sort.Direction.DESC;
                } else {
                    throw new IllegalArgumentException("排序方向只能是 asc 或 desc。" );
                }
                orders.add(new Sort.Order(direction, property));
            }
        }
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, "reportTime"));
        }
        if (orders.stream().noneMatch(order -> "id".equals(order.getProperty()))) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        }
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(orders));
    }

    private List<String> normalizeSortSpecifications(String[] sort) {
        if (sort == null || sort.length == 0) {
            return List.of();
        }
        List<String> specifications = new ArrayList<>();
        for (String rawValue : sort) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            String value = rawValue.trim();
            if ((value.equalsIgnoreCase("asc") || value.equalsIgnoreCase("desc"))
                    && !specifications.isEmpty()
                    && !specifications.get(specifications.size() - 1).contains(",")) {
                int lastIndex = specifications.size() - 1;
                specifications.set(lastIndex, specifications.get(lastIndex) + "," + value);
            } else {
                specifications.add(value);
            }
        }
        return specifications;
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponseDTO> getWorkOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(workOrderService.getWorkOrderById(id));
    }

    @PostMapping("/{id}/claims")
    public ResponseEntity<WorkOrderResponseDTO> claimWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VersionedWorkOrderCommandDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/claims",
                request, WorkOrderResponseDTO.class, () -> workOrderService.claimWorkOrder(id, request)));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<WorkOrderResponseDTO> assignWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AssignWorkOrderRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/assignments",
                request, WorkOrderResponseDTO.class, () -> workOrderService.assignWorkOrder(id, request)));
    }

    @PostMapping("/{id}/assignments/{assignmentId}/acceptance")
    public ResponseEntity<WorkOrderResponseDTO> acceptAssignment(
            @PathVariable UUID id,
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AssignmentDecisionRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey,
                "POST:/work-orders/" + id + "/assignments/" + assignmentId + "/acceptance",
                request, WorkOrderResponseDTO.class,
                () -> workOrderService.acceptAssignment(id, assignmentId, request)));
    }

    @PostMapping("/{id}/assignments/{assignmentId}/rejection")
    public ResponseEntity<WorkOrderResponseDTO> rejectAssignment(
            @PathVariable UUID id,
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AssignmentDecisionRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey,
                "POST:/work-orders/" + id + "/assignments/" + assignmentId + "/rejection",
                request, WorkOrderResponseDTO.class,
                () -> workOrderService.rejectAssignment(id, assignmentId, request)));
    }

    @PostMapping("/{id}/reassignments")
    public ResponseEntity<WorkOrderResponseDTO> reassignWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AssignWorkOrderRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/reassignments",
                request, WorkOrderResponseDTO.class, () -> workOrderService.reassignWorkOrder(id, request)));
    }

    @PostMapping("/{id}/verifications")
    public ResponseEntity<WorkOrderResponseDTO> verifyWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VerifyWorkOrderRequestDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/verifications",
                request, WorkOrderResponseDTO.class, () -> workOrderService.verifyWorkOrder(id, request)));
    }

    @PostMapping("/{id}/holds")
    public ResponseEntity<WorkOrderResponseDTO> holdWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReasonedWorkOrderCommandDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/holds",
                request, WorkOrderResponseDTO.class, () -> workOrderService.holdWorkOrder(id, request)));
    }

    @PostMapping("/{id}/resumptions")
    public ResponseEntity<WorkOrderResponseDTO> resumeWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VersionedWorkOrderCommandDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/resumptions",
                request, WorkOrderResponseDTO.class, () -> workOrderService.resumeWorkOrder(id, request)));
    }

    @PostMapping("/{id}/cancellations")
    public ResponseEntity<WorkOrderResponseDTO> cancelWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReasonedWorkOrderCommandDTO request) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/cancellations",
                request, WorkOrderResponseDTO.class, () -> workOrderService.cancelWorkOrder(id, request)));
    }

    @GetMapping("/{id}/assignment-candidates")
    public ResponseEntity<List<AssignmentCandidateDTO>> assignmentCandidates(
            @PathVariable UUID id,
            @RequestParam(required = false) String searchTerm) {
        return ResponseEntity.ok(workOrderService.assignmentCandidates(id, searchTerm));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<PageResponseDTO<WorkOrderEventDTO>> workOrderEvents(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return ResponseEntity.ok(PageResponseDTO.from(workOrderService.workOrderEvents(id, pageable)));
    }

    @GetMapping("/{id}/responses")
    public ResponseEntity<List<WorkOrderResponseHistoryDTO>> workOrderResponses(@PathVariable UUID id) {
        return ResponseEntity.ok(workOrderService.workOrderResponses(id));
    }

    @PostMapping(value = "/{id}/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkOrderResponseDTO> respondToWorkOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RespondWorkOrderRequestDTO responseRequest) {
        return ResponseEntity.ok(idempotencyService.execute(idempotencyKey, "POST:/work-orders/" + id + "/responses",
                responseRequest, WorkOrderResponseDTO.class, () -> workOrderService.respondToWorkOrder(id, responseRequest)));
    }

    /**
     * 保留单段存储键的历史下载入口。带目录分隔符的存储键统一使用附件 UUID 入口，
     * 避免与 /file/{attachmentId} 路由竞争，也避免网关对 escaped slash 做重定向。
     */
    @RequestMapping(value = "/attachments/{workOrderId}/{storedFileName}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> downloadFile(
            @PathVariable UUID workOrderId,
            @PathVariable String storedFileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        try {
            WorkOrderAttachmentService.AttachmentResource attachmentResource =
                    workOrderAttachmentService.loadAttachmentResource(workOrderId, storedFileName);
            return buildAttachmentDownloadResponse(attachmentResource, storedFileName, rangeHeader, request);
        } catch (FileNotFoundException ex) {
            log.warn("Download request: File not found for work order {}, storedName {}. Error: {}", workOrderId, storedFileName, ex.getMessage());
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            log.warn("Download request: Access denied for work order {}, storedName {}. User: {}", workOrderId, storedFileName, SecurityContextHolder.getContext().getAuthentication().getName(), ex);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException ex) {
            log.error("Download request: IO error for work order {}, storedName {}. Error: {}", workOrderId, storedFileName, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @RequestMapping(value = "/{workOrderId}/attachments/zip", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> downloadAllAttachmentsAsZip(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        try {
            WorkOrderAttachmentService.ZipArchiveResource zipArchive =
                    workOrderAttachmentService.createAttachmentsZipResource(workOrderId);
            return fileDownloadResponseFactory.build(
                    zipArchive.resource(),
                    new FileDownloadResponseFactory.DownloadDescriptor(
                            zipArchive.fileName(),
                            "application/zip",
                            zipArchive.fileSize(),
                            "attachment",
                            Map.of("X-File-Archive", "work-order-attachments")
                    ),
                    rangeHeader,
                    request.getMethod()
            );
        } catch (FileNotFoundException ex) {
            log.warn("ZIP Download request: Files not found for work order {}. Error: {}", workOrderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("附件或工单不存在。");
        } catch (AccessDeniedException ex) {
            log.warn("ZIP Download request: Access denied for work order {}. User: {}", workOrderId, SecurityContextHolder.getContext().getAuthentication().getName(), ex);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("当前账号没有下载该附件的权限。");
        } catch (IOException e) {
            log.error("ZIP Download request: IO error for work order {}. Error: {}", workOrderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("附件打包失败，请稍后重试。");
        }
    }

    /**
     * 通过附件 UUID 下载文件，避免将包含目录分隔符的存储键放入 URL 路径。
     * Envoy 对 URL 中的 escaped slash 会规范化并重定向，导致脱敏文件（_sanitized/…）无法命中原路由。
     */
    @RequestMapping(value = "/attachments/{workOrderId}/file/{attachmentId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> downloadFileById(
            @PathVariable UUID workOrderId,
            @PathVariable UUID attachmentId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        try {
            WorkOrderAttachmentService.AttachmentResource attachmentResource =
                    workOrderAttachmentService.loadAttachmentResource(workOrderId, attachmentId);
            return buildAttachmentDownloadResponse(attachmentResource, attachmentId.toString(), rangeHeader, request);
        } catch (FileNotFoundException ex) {
            log.warn("Download request: File not found for work order {}, attachment {}. Error: {}", workOrderId, attachmentId, ex.getMessage());
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            log.warn("Download request: Access denied for work order {}, attachment {}. User: {}", workOrderId, attachmentId, SecurityContextHolder.getContext().getAuthentication().getName(), ex);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException ex) {
            log.error("Download request: IO error for work order {}, attachment {}. Error: {}", workOrderId, attachmentId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<?> buildAttachmentDownloadResponse(
            WorkOrderAttachmentService.AttachmentResource attachmentResource,
            String fallbackFileName,
            String rangeHeader,
            HttpServletRequest request) throws IOException {
        Resource resource = attachmentResource.resource();
        String contentType = attachmentResource.contentType() == null || attachmentResource.contentType().isBlank()
                ? workOrderAttachmentService.probeContentType(resource.getFile().toPath())
                : attachmentResource.contentType();
        String originalFilename = attachmentResource.originalFileName() == null || attachmentResource.originalFileName().isBlank()
                ? fallbackFileName
                : attachmentResource.originalFileName();
        return fileDownloadResponseFactory.build(
                resource,
                new FileDownloadResponseFactory.DownloadDescriptor(
                        originalFilename,
                        contentType,
                        resource.contentLength(),
                        "inline",
                        Collections.emptyMap()
                ),
                rangeHeader,
                request.getMethod()
        );
    }
}


