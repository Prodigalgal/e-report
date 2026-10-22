package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.UserTransferAuthorizationDiffDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportPreviewDTO;
import com.pro.ereportbacken.model.dto.UserTransferFieldChangeDTO;
import com.pro.ereportbacken.model.dto.UserTransferJobDTO;
import com.pro.ereportbacken.model.dto.UserTransferJobRowDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferPreviewSummaryDTO;
import com.pro.ereportbacken.model.dto.FileTransferDownloadDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.model.pojo.UserTransferJobRow;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Log4j2
public class UserTransferService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Duration TEMPLATE_RETENTION = Duration.ofHours(2);

    private final UserTransferWorkbookService workbookService;
    private final UserTransferPlanService planService;
    private final OrgUnitService orgUnitService;
    private final DepartmentService departmentService;
    private final FileTransferService fileTransferService;
    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobRowRepository rowRepository;
    private final UserTransferJobStateService stateService;
    private final UserTransferAsyncProcessor asyncProcessor;
    private final UserTransferPreviewTokenService tokenService;
    private final UserExportService userExportService;
    private final TenantContext tenantContext;
    private final WorkOrderAccessPolicy workOrderAccessPolicy;
    private final SecurityHashingService hashingService;
    private final ObjectMapper objectMapper;

    public UserTransferService(UserTransferWorkbookService workbookService,
                               UserTransferPlanService planService,
                               OrgUnitService orgUnitService,
                               DepartmentService departmentService,
                               FileTransferService fileTransferService,
                               UserTransferJobRepository jobRepository,
                               UserTransferJobRowRepository rowRepository,
                               UserTransferJobStateService stateService,
                               UserTransferAsyncProcessor asyncProcessor,
                               UserTransferPreviewTokenService tokenService,
                               UserExportService userExportService,
                               TenantContext tenantContext,
                               WorkOrderAccessPolicy workOrderAccessPolicy,
                               SecurityHashingService hashingService,
                               ObjectMapper objectMapper) {
        this.workbookService = workbookService;
        this.planService = planService;
        this.orgUnitService = orgUnitService;
        this.departmentService = departmentService;
        this.fileTransferService = fileTransferService;
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.stateService = stateService;
        this.asyncProcessor = asyncProcessor;
        this.tokenService = tokenService;
        this.userExportService = userExportService;
        this.tenantContext = tenantContext;
        this.workOrderAccessPolicy = workOrderAccessPolicy;
        this.hashingService = hashingService;
        this.objectMapper = objectMapper;
    }

    public FileTransferService.GeneratedFile createTemplate() {
        requireAuthority(PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD);
        UUID tenantId = tenantContext.requireTenantId();
        byte[] content = workbookService.createTemplate(
                tenantId,
                orgUnitService.findAll(),
                departmentService.findAccessible(),
                planService.visibleProfileCatalog(),
                planService.visiblePermissionCatalog()
        );
        return fileTransferService.registerGeneratedFile(
                FileTransferPurpose.USER_IMPORT_TEMPLATE,
                "用户批量治理模板-v" + UserTransferWorkbookService.TEMPLATE_VERSION + ".xlsx",
                XLSX_CONTENT_TYPE,
                content,
                TEMPLATE_RETENTION
        );
    }

    public UserTransferJobDTO createImport(UUID uploadId, String idempotencyKey) {
        requireAuthority(PermissionCatalog.USER_IMPORT_PREVIEW);
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key 必须填写且不能超过100字符。");
        }
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = workOrderAccessPolicy.currentMembershipId();
        String normalizedKey = idempotencyKey.trim();
        String requestHash = hashingService.sha256(uploadId.toString());
        UserTransferJob job = stateService.createImport(
                tenantId, membershipId, workOrderAccessPolicy.currentUsernameOrUnknown(),
                uploadId, normalizedKey, requestHash);
        try {
            asyncProcessor.validate(job.getId());
        } catch (org.springframework.core.task.TaskRejectedException exception) {
            stateService.fail(job.getId(), "用户传输任务队列已满，请稍后重试。");
        }
        return toDTO(job);
    }

    public UserTransferJobDTO createExport(UserTransferExportRequestDTO request, String idempotencyKey) {
        requireAuthority(PermissionCatalog.USER_EXPORT_CREATE);
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key 必须填写且不能超过100字符。");
        }
        try {
            UserExportService.PreparedExport prepared = userExportService.prepare(request);
            String requestJson = objectMapper.writeValueAsString(prepared.request());
            UUID tenantId = tenantContext.requireTenantId();
            UUID membershipId = workOrderAccessPolicy.currentMembershipId();
            UserTransferJob job = stateService.createExport(
                    tenantId, membershipId, workOrderAccessPolicy.currentUsernameOrUnknown(),
                    idempotencyKey.trim(), userExportService.requestHash(prepared.request()), requestJson,
                    prepared.snapshot().digest(), prepared.previewExpiresAt(), prepared.snapshot().userCount());
            try {
                asyncProcessor.export(job.getId());
            } catch (org.springframework.core.task.TaskRejectedException exception) {
                stateService.fail(job.getId(), "用户传输任务队列已满，请稍后重试。");
            }
            return toDTO(job);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("导出筛选条件序列化失败。", exception);
        }
    }

    @Transactional(readOnly = true)
    public UserTransferExportPreviewDTO previewExport(UserTransferExportRequestDTO request) {
        requireAuthority(PermissionCatalog.USER_EXPORT_CREATE);
        return userExportService.preview(request);
    }

    public UserTransferJobDTO find(UUID jobId) {
        UserTransferJob job = requireOwned(jobId);
        requireReadPermission(job.getJobType());
        return toDTO(job);
    }

    public Page<UserTransferJobDTO> findAll(UserTransferJobType jobType, Pageable pageable) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = workOrderAccessPolicy.currentMembershipId();
        UserTransferJobType permittedType = permittedJobType(jobType);
        Page<UserTransferJob> jobs = permittedType == null
                ? jobRepository.findByTenantIdAndCreatedByMembershipIdOrderByCreatedAtDesc(
                        tenantId, membershipId, pageable)
                : jobRepository.findByTenantIdAndCreatedByMembershipIdAndJobTypeOrderByCreatedAtDesc(
                        tenantId, membershipId, permittedType, pageable);
        return jobs.map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<UserTransferJobRowDTO> rows(UUID jobId,
                                            UserTransferValidationStatus validationStatus,
                                            UserTransferExecutionStatus executionStatus,
                                            Pageable pageable) {
        UserTransferJob job = requireOwned(jobId);
        requireAnyAuthority(PermissionCatalog.USER_IMPORT_TASK_VIEW, PermissionCatalog.USER_IMPORT_PREVIEW);
        requireImportJob(job);
        Page<UserTransferJobRow> rows = rowRepository.findFiltered(
                job.getTenantId(), job.getId(), validationStatus, executionStatus, pageable);
        return rows.map(this::toRowDTO);
    }

    public Page<UserTransferJobRowDTO> rows(UUID jobId,
                                            UserTransferValidationStatus validationStatus,
                                            Pageable pageable) {
        return rows(jobId, validationStatus, null, pageable);
    }

    public UserTransferJobDTO execute(UUID jobId, String previewToken) {
        requireAuthority(PermissionCatalog.USER_IMPORT_EXECUTE);
        UserTransferJob job = stateService.queueExecution(
                tenantContext.requireTenantId(), workOrderAccessPolicy.currentMembershipId(),
                jobId, previewToken, tokenService);
        asyncProcessor.execute(job.getId());
        return toDTO(job);
    }

    public UserTransferJobDTO cancel(UUID jobId) {
        UserTransferJob job = requireOwned(jobId);
        requireAuthority(job.getJobType() == UserTransferJobType.IMPORT
                ? PermissionCatalog.USER_IMPORT_CANCEL
                : PermissionCatalog.USER_EXPORT_CANCEL);
        return toDTO(stateService.cancel(
                tenantContext.requireTenantId(), workOrderAccessPolicy.currentMembershipId(), jobId));
    }

    public FileTransferService.CompletedFile loadResult(UUID jobId) {
        UserTransferJob job = requireOwned(jobId);
        requireAuthority(PermissionCatalog.USER_EXPORT_DOWNLOAD);
        if (job.getStatus() != UserTransferJobStatus.COMPLETED || job.getResultFileId() == null) {
            throw new IllegalStateException("任务结果文件尚未生成或已经失效。");
        }
        return fileTransferService.loadCompletedFile(job.getResultFileId(), FileTransferPurpose.USER_EXPORT);
    }

    public FileTransferService.CompletedFile loadErrorResult(UUID jobId) {
        UserTransferJob job = requireOwned(jobId);
        requireAuthority(PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD);
        requireImportJob(job);
        if (job.getErrorFileId() == null) {
            throw new IllegalStateException("任务失败结果文件尚未生成或已经失效。");
        }
        return fileTransferService.loadCompletedFile(job.getErrorFileId(), FileTransferPurpose.USER_IMPORT_ERROR);
    }

    private void requireImportJob(UserTransferJob job) {
        if (job.getJobType() != UserTransferJobType.IMPORT) {
            throw new IllegalArgumentException("导出任务没有逐行导入结果。");
        }
    }

    private UserTransferJob requireOwned(UUID jobId) {
        return jobRepository.findByTenantIdAndIdAndCreatedByMembershipId(
                        tenantContext.requireTenantId(), jobId, workOrderAccessPolicy.currentMembershipId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("用户传输任务不存在。"));
    }

    private UserTransferJobType permittedJobType(UserTransferJobType requestedType) {
        boolean canReadImports = canReadJobType(UserTransferJobType.IMPORT);
        boolean canReadExports = canReadJobType(UserTransferJobType.EXPORT);
        if (requestedType != null) {
            requireReadPermission(requestedType);
            return requestedType;
        }
        if (canReadImports && canReadExports) return null;
        if (canReadImports) return UserTransferJobType.IMPORT;
        if (canReadExports) return UserTransferJobType.EXPORT;
        throw new AccessDeniedException("当前账号没有查看用户批量任务的权限。");
    }

    private void requireReadPermission(UserTransferJobType jobType) {
        if (!canReadJobType(jobType)) {
            throw new AccessDeniedException("当前账号没有查看该类型用户批量任务的权限。");
        }
    }

    private boolean canReadJobType(UserTransferJobType jobType) {
        String taskViewPermission = jobType == UserTransferJobType.IMPORT
                ? PermissionCatalog.USER_IMPORT_TASK_VIEW
                : PermissionCatalog.USER_EXPORT_TASK_VIEW;
        String createPermission = jobType == UserTransferJobType.IMPORT
                ? PermissionCatalog.USER_IMPORT_PREVIEW
                : PermissionCatalog.USER_EXPORT_CREATE;
        return hasAuthority(taskViewPermission) || hasAuthority(createPermission);
    }

    private void requireAnyAuthority(String... authorities) {
        for (String authority : authorities) {
            if (hasAuthority(authority)) return;
        }
        throw new AccessDeniedException("当前账号没有执行该批量任务操作的权限。");
    }

    private boolean canDownloadImportErrorReport() {
        return hasAuthority(PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD);
    }

    private void requireAuthority(String authority) {
        if (!hasAuthority(authority)) {
            throw new AccessDeniedException("当前账号没有执行该批量任务操作的权限。");
        }
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private UserTransferJobDTO toDTO(UserTransferJob job) {
        boolean previewUsable = job.getStatus() == UserTransferJobStatus.PREVIEW_READY
                && job.getInvalidRows() == 0
                && job.getPreviewExpiresAt() != null
                && job.getPreviewExpiresAt().isAfter(UtcTime.now());
        FileTransferDownloadDTO resultFile = resultFile(job);
        FileTransferDownloadDTO errorFile = errorFile(job);
        return new UserTransferJobDTO(
                job.getId(), job.getJobType(), job.getStatus(), job.getInputFileId(),
                job.getResultFileId(), job.getErrorFileId(), resultFile, errorFile,
                job.getInputSha256(), job.getTemplateVersion(),
                job.getTotalRows(), job.getValidRows(), job.getInvalidRows(), job.getProcessedRows(),
                job.getSucceededRows(), job.getFailedRows(), job.getFailureMessage(),
                previewSummary(job),
                previewUsable && hasAuthority(PermissionCatalog.USER_IMPORT_EXECUTE)
                        ? tokenService.issue(job) : null, job.getPreviewExpiresAt(),
                job.getStartedAt(), job.getCompletedAt(), job.getExpiresAt(), job.getCreatedAt(), job.getUpdatedAt());
    }

    private FileTransferDownloadDTO errorFile(UserTransferJob job) {
        if (job.getJobType() != UserTransferJobType.IMPORT
                || job.getErrorFileId() == null
                || !canDownloadImportErrorReport()) {
            return null;
        }
        try {
            FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                    job.getErrorFileId(), FileTransferPurpose.USER_IMPORT_ERROR);
            return new FileTransferDownloadDTO(
                    file.fileId(), file.fileName(), file.contentType(), file.fileSize(), file.chunkSize(),
                    file.sha256(), "/admin/user-transfers/" + job.getId() + "/error-result", file.expiresAt());
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException exception) {
            log.debug("用户导入失败结果文件不可用。jobId={}, fileId={}, reason={}",
                    job.getId(), job.getErrorFileId(), exception.getMessage());
            return null;
        }
    }

    private FileTransferDownloadDTO resultFile(UserTransferJob job) {
        if (job.getJobType() != UserTransferJobType.EXPORT
                || job.getStatus() != UserTransferJobStatus.COMPLETED
                || job.getResultFileId() == null
                || !hasAuthority(PermissionCatalog.USER_EXPORT_DOWNLOAD)) {
            return null;
        }
        try {
            FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(
                    job.getResultFileId(), FileTransferPurpose.USER_EXPORT);
            return new FileTransferDownloadDTO(
                    file.fileId(), file.fileName(), file.contentType(), file.fileSize(), file.chunkSize(),
                    file.sha256(), "/admin/user-transfers/" + job.getId() + "/result", file.expiresAt());
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException exception) {
            log.debug("用户导出结果文件不可用。jobId={}, fileId={}, reason={}",
                    job.getId(), job.getResultFileId(), exception.getMessage());
            return null;
        }
    }

    private UserTransferJobRowDTO toRowDTO(UserTransferJobRow row) {
        try {
            List<UserTransferFieldChangeDTO> fields = readFieldChanges(row.getFieldDiffJson());
            UserTransferAuthorizationDiffDTO authorizationDiff = readAuthorizationDiff(row.getGrantDiffJson());
            List<String> errors = objectMapper.readValue(
                    emptyJson(row.getValidationErrorsJson(), "[]"), new TypeReference<>() {});
            return new UserTransferJobRowDTO(
                    row.getId(), row.getSubjectKeyHash(), row.getSubjectDisplay(),
                    row.getSheetName(), row.getRowNumber(), row.getCommandType(),
                    row.getValidationStatus(), row.getExecutionStatus(), fields, authorizationDiff, errors,
                    row.getExecutionMessage(), row.getBeforeVersion(), row.getAfterVersion());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("任务预览行数据损坏。", exception);
        }
    }

    private UserTransferPreviewSummaryDTO previewSummary(UserTransferJob job) {
        if (job.getJobType() != UserTransferJobType.IMPORT || !StringUtils.hasText(job.getSummaryJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(job.getSummaryJson(), UserTransferPreviewSummaryDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("用户传输任务摘要损坏。", exception);
        }
    }

    private List<UserTransferFieldChangeDTO> readFieldChanges(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = objectMapper.readTree(emptyJson(json, "[]"));
        if (root.isArray()) {
            return objectMapper.convertValue(root, new TypeReference<>() {});
        }
        if (!root.isObject()) {
            throw new IllegalStateException("任务字段 Diff 格式无效。");
        }
        List<UserTransferFieldChangeDTO> fieldChanges = new java.util.ArrayList<>();
        root.properties().forEach(entry -> fieldChanges.add(
                new UserTransferFieldChangeDTO(entry.getKey(), null, entry.getValue().asText())));
        return List.copyOf(fieldChanges);
    }

    private UserTransferAuthorizationDiffDTO readAuthorizationDiff(String json)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = objectMapper.readTree(emptyJson(json, "{}"));
        if (root.isObject()) {
            return objectMapper.convertValue(root, UserTransferAuthorizationDiffDTO.class);
        }
        if (!root.isArray()) {
            throw new IllegalStateException("任务授权 Diff 格式无效。");
        }
        List<String> authorizationMessages = objectMapper.convertValue(root, new TypeReference<>() {});
        return new UserTransferAuthorizationDiffDTO(
                authorizationMessages.isEmpty() ? null : String.join("；", authorizationMessages), List.of());
    }

    private String emptyJson(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
