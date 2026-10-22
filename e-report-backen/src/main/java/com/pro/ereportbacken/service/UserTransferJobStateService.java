package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.model.pojo.UserTransferJobRow;
import com.pro.ereportbacken.model.pojo.UserTransferJobStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.repository.UserTransferJobRowRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserTransferJobStateService {

    private static final Duration JOB_RETENTION = Duration.ofDays(7);
    private static final Duration PREVIEW_RETENTION = Duration.ofMinutes(30);
    private static final int MAX_ACTIVE_JOBS_PER_TENANT = 2;
    private static final int MAX_ACTIVE_JOBS_PER_OWNER = 2;
    private static final List<UserTransferJobStatus> ACTIVE_JOB_STATUSES = List.of(
            UserTransferJobStatus.QUEUED, UserTransferJobStatus.UPLOADED,
            UserTransferJobStatus.VALIDATING, UserTransferJobStatus.PREVIEW_READY,
            UserTransferJobStatus.EXECUTING);
    private static final List<UserTransferJobStatus> PROCESSING_STATUSES = List.of(
            UserTransferJobStatus.VALIDATING, UserTransferJobStatus.EXECUTING);

    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobRowRepository rowRepository;
    private final FileTransferService fileTransferService;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public UserTransferJobStateService(UserTransferJobRepository jobRepository,
                                       UserTransferJobRowRepository rowRepository,
                                       FileTransferService fileTransferService,
                                       TenantRepository tenantRepository,
                                       ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.fileTransferService = fileTransferService;
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserTransferJob createImport(UUID tenantId,
                                        UUID membershipId,
                                        String username,
                                        UUID uploadId,
                                        String idempotencyKey,
                                        String requestHash) {
        UserTransferJob existing = jobRepository
                .findByTenantIdAndCreatedByMembershipIdAndIdempotencyKey(
                        tenantId, membershipId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IllegalStateException("同一幂等键已用于不同用户传输请求。");
            }
            return existing;
        }
        lockTenantAndRequireAdmission(tenantId, membershipId);
        FileTransferService.ClaimedFile claimed = fileTransferService.claimCompletedFile(
                uploadId, com.pro.ereportbacken.model.pojo.FileTransferPurpose.USER_IMPORT, JOB_RETENTION);
        UserTransferJob job = new UserTransferJob();
        job.setTenantId(tenantId);
        job.setCreatedByMembershipId(membershipId);
        job.setCreatedByUsername(username);
        job.setJobType(UserTransferJobType.IMPORT);
        job.setStatus(UserTransferJobStatus.UPLOADED);
        job.setInputFileId(uploadId);
        job.setInputSha256(claimed.sha256());
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestHash(requestHash);
        job.setExpiresAt(UtcTime.now().plus(JOB_RETENTION));
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public UserTransferJob createExport(UUID tenantId,
                                        UUID membershipId,
                                        String username,
                                        String idempotencyKey,
                                        String requestHash,
                                        String requestJson,
                                        String previewSnapshotDigest,
                                        LocalDateTime previewExpiresAt,
                                        long userCount) {
        UserTransferJob existing = jobRepository
                .findByTenantIdAndCreatedByMembershipIdAndIdempotencyKey(
                        tenantId, membershipId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IllegalStateException("同一幂等键已用于不同用户传输请求。");
            }
            return existing;
        }
        lockTenantAndRequireAdmission(tenantId, membershipId);
        UserTransferJob job = new UserTransferJob();
        job.setTenantId(tenantId);
        job.setCreatedByMembershipId(membershipId);
        job.setCreatedByUsername(username);
        job.setJobType(UserTransferJobType.EXPORT);
        job.setStatus(UserTransferJobStatus.QUEUED);
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestHash(requestHash);
        job.setSummaryJson(requestJson);
        job.setPreviewSnapshotDigest(previewSnapshotDigest);
        job.setPreviewExpiresAt(previewExpiresAt);
        job.setTotalRows(Math.toIntExact(userCount));
        job.setExpiresAt(UtcTime.now().plusDays(1));
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public UserTransferJob startValidation(UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() != UserTransferJobStatus.UPLOADED) {
            return null;
        }
        if (!acquireTenantProcessingSlot(job)) {
            return null;
        }
        job.setStatus(UserTransferJobStatus.VALIDATING);
        job.setStartedAt(UtcTime.now());
        job.setFailureMessage(null);
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void completeValidation(UUID jobId,
                                   UserTransferPlanService.TransferPlan plan,
                                   String previewSnapshotDigest) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.CANCELLED) {
            return;
        }
        if (job.getStatus() != UserTransferJobStatus.VALIDATING) {
            throw new IllegalStateException("任务校验状态已变化，不能覆盖最新结果。");
        }
        rowRepository.deleteAllByTenantIdAndJobId(job.getTenantId(), job.getId());
        rowRepository.flush();
        List<UserTransferJobRow> rows = plan.rows().stream().map(preview -> toRow(job, preview)).toList();
        rowRepository.saveAll(rows);
        job.setTemplateVersion(plan.templateVersion());
        job.setCatalogDigest(plan.catalogDigest());
        job.setUserSnapshotDigest(plan.userSnapshotDigest());
        job.setPreviewSnapshotDigest(previewSnapshotDigest);
        job.setPreviewExpiresAt(UtcTime.now().plus(PREVIEW_RETENTION));
        job.setTotalRows(plan.rows().size());
        job.setValidRows(plan.validRows());
        job.setInvalidRows(plan.invalidRows());
        job.setProcessedRows(0);
        job.setSucceededRows(0);
        job.setFailedRows(0);
        job.setSummaryJson(json(plan.summary()));
        job.setStatus(UserTransferJobStatus.PREVIEW_READY);
        jobRepository.save(job);
    }

    @Transactional
    public UserTransferJob queueExecution(UUID tenantId,
                                          UUID membershipId,
                                          UUID jobId,
                                          String previewToken,
                                          UserTransferPreviewTokenService tokenService) {
        UserTransferJob job = requireLocked(jobId);
        requireOwner(job, tenantId, membershipId);
        if (job.getStatus() != UserTransferJobStatus.PREVIEW_READY) {
            throw new IllegalStateException("任务当前不处于可执行的预览状态。");
        }
        if (job.getInvalidRows() > 0) {
            throw new IllegalStateException("预览仍存在错误行，修正文件后请重新上传。");
        }
        if (job.getPreviewExpiresAt() == null || !job.getPreviewExpiresAt().isAfter(UtcTime.now())) {
            job.setStatus(UserTransferJobStatus.STALE);
            jobRepository.save(job);
            throw new IllegalStateException("预览确认已过期，请重新校验文件。");
        }
        if (!tokenService.matches(job, previewToken)) {
            throw new org.springframework.security.access.AccessDeniedException("预览确认令牌无效。");
        }
        job.setStatus(UserTransferJobStatus.QUEUED);
        job.setFailureMessage(null);
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public UserTransferJob startQueuedExecution(UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getJobType() != UserTransferJobType.IMPORT || job.getStatus() != UserTransferJobStatus.QUEUED) {
            return null;
        }
        if (job.getPreviewExpiresAt() == null || !job.getPreviewExpiresAt().isAfter(UtcTime.now())) {
            job.setStatus(UserTransferJobStatus.STALE);
            job.setFailureMessage("排队期间预览已过期，请重新校验文件。");
            job.setCompletedAt(UtcTime.now());
            jobRepository.save(job);
            return null;
        }
        if (!acquireTenantProcessingSlot(job)) {
            return null;
        }
        job.setStatus(UserTransferJobStatus.EXECUTING);
        job.setStartedAt(UtcTime.now());
        job.setFailureMessage(null);
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public UserTransferJob startExport(UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() != UserTransferJobStatus.QUEUED) {
            return null;
        }
        if (!acquireTenantProcessingSlot(job)) {
            return null;
        }
        job.setStatus(UserTransferJobStatus.EXECUTING);
        job.setStartedAt(UtcTime.now());
        job.setFailureMessage(null);
        return jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void completeExport(UUID jobId,
                               FileTransferService.GeneratedFile file,
                               int userCount) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.CANCELLED) {
            return;
        }
        if (job.getStatus() != UserTransferJobStatus.EXECUTING) {
            throw new IllegalStateException("导出任务状态已变化，不能覆盖最新结果。");
        }
        job.setResultFileId(file.fileId());
        job.setTotalRows(userCount);
        job.setValidRows(userCount);
        job.setProcessedRows(userCount);
        job.setSucceededRows(userCount);
        job.setStatus(UserTransferJobStatus.COMPLETED);
        job.setCompletedAt(UtcTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void recoverInterrupted(UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.VALIDATING) {
            job.setStatus(UserTransferJobStatus.UPLOADED);
            job.setFailureMessage("服务重启后已重新排队校验。");
        } else if (job.getStatus() == UserTransferJobStatus.QUEUED) {
            job.setFailureMessage("服务重启后已重新排队导出。");
        } else if (job.getStatus() == UserTransferJobStatus.EXECUTING) {
            if (job.getJobType() == UserTransferJobType.EXPORT) {
                job.setStatus(UserTransferJobStatus.QUEUED);
                job.setFailureMessage("服务重启后已重新排队导出。");
            } else {
                job.setStatus(UserTransferJobStatus.FAILED);
                job.setCompletedAt(UtcTime.now());
                job.setFailureMessage("服务在执行期间重启；已成功提交的用户保持生效，未完成用户未执行，请查看逐行结果后重新创建任务。");
            }
        }
        jobRepository.save(job);
    }

    @Transactional
    public void markStale(UUID jobId, String message) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.CANCELLED) return;
        job.setStatus(UserTransferJobStatus.STALE);
        job.setFailureMessage(limit(message));
        job.setCompletedAt(UtcTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void markUserResult(UUID jobId,
                               String phoneHash,
                               boolean success,
                               String message,
                               Long afterVersion) {
        UserTransferJob job = requireLocked(jobId);
        List<UserTransferJobRow> rows = rowRepository.findByTenantIdAndJobIdAndSubjectKeyHash(
                job.getTenantId(), job.getId(), phoneHash);
        rows.forEach(row -> {
            row.setExecutionStatus(success
                    ? UserTransferExecutionStatus.SUCCEEDED
                    : UserTransferExecutionStatus.FAILED);
            row.setExecutionMessage(limit(message));
            row.setAfterVersion(afterVersion);
        });
        rowRepository.saveAll(rows);
        job.setProcessedRows(job.getProcessedRows() + rows.size());
        if (success) {
            job.setSucceededRows(job.getSucceededRows() + rows.size());
        } else {
            job.setFailedRows(job.getFailedRows() + rows.size());
        }
        jobRepository.save(job);
    }

    @Transactional
    public void completeExecution(UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.CANCELLED) return;
        job.setStatus(job.getFailedRows() > 0
                ? UserTransferJobStatus.FAILED
                : UserTransferJobStatus.COMPLETED);
        if (job.getFailedRows() > 0) {
            job.setFailureMessage("部分用户执行失败，请查看任务行结果；成功用户已提交，失败用户事务已回滚。");
        }
        job.setCompletedAt(UtcTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void attachErrorFile(UUID jobId, FileTransferService.GeneratedFile file) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getJobType() != UserTransferJobType.IMPORT) {
            throw new IllegalArgumentException("只有导入任务可以登记失败结果文件。");
        }
        job.setErrorFileId(file.fileId());
        jobRepository.save(job);
    }

    @Transactional
    public void fail(UUID jobId, String message) {
        UserTransferJob job = requireLocked(jobId);
        if (job.getStatus() == UserTransferJobStatus.CANCELLED) return;
        job.setStatus(UserTransferJobStatus.FAILED);
        job.setFailureMessage(limit(message));
        job.setCompletedAt(UtcTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public UserTransferJob cancel(UUID tenantId, UUID membershipId, UUID jobId) {
        UserTransferJob job = requireLocked(jobId);
        requireOwner(job, tenantId, membershipId);
        if (job.getStatus() == UserTransferJobStatus.EXECUTING
                || job.getStatus() == UserTransferJobStatus.COMPLETED
                || job.getStatus() == UserTransferJobStatus.FAILED
                || job.getStatus() == UserTransferJobStatus.STALE) {
            throw new IllegalStateException("当前任务状态不能取消。");
        }
        job.setStatus(UserTransferJobStatus.CANCELLED);
        job.setCompletedAt(UtcTime.now());
        return jobRepository.save(job);
    }

    private UserTransferJobRow toRow(UserTransferJob job,
                                     UserTransferPlanService.PreviewRow preview) {
        UserTransferJobRow row = new UserTransferJobRow();
        row.setTenantId(job.getTenantId());
        row.setJobId(job.getId());
        row.setSheetName(preview.sheetName());
        row.setRowNumber(preview.rowNumber());
        row.setSubjectKeyHash(preview.phoneHash());
        row.setSubjectDisplay(preview.subjectDisplay());
        row.setCommandType(preview.commandType());
        row.setValidationStatus(preview.errors().isEmpty()
                ? UserTransferValidationStatus.VALID
                : UserTransferValidationStatus.INVALID);
        row.setFieldDiffJson(json(preview.fieldChanges()));
        row.setGrantDiffJson(json(preview.authorizationDiff()));
        row.setValidationErrorsJson(json(preview.errors()));
        row.setBeforeVersion(preview.beforeVersion());
        return row;
    }

    private UserTransferJob requireLocked(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("用户传输任务不存在。"));
    }

    private void lockTenantAndRequireAdmission(UUID tenantId, UUID membershipId) {
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("租户不存在。"));
        long tenantJobs = jobRepository.countByTenantIdAndStatusIn(tenantId, ACTIVE_JOB_STATUSES);
        if (tenantJobs >= MAX_ACTIVE_JOBS_PER_TENANT) {
            throw new IllegalStateException("当前组织已有较多批量任务，请等待已有任务完成后再提交。");
        }
        long ownerJobs = jobRepository.countByTenantIdAndCreatedByMembershipIdAndStatusIn(
                tenantId, membershipId, ACTIVE_JOB_STATUSES);
        if (ownerJobs >= MAX_ACTIVE_JOBS_PER_OWNER) {
            throw new IllegalStateException("您已有两个未完成的批量任务，请先处理或取消已有任务。");
        }
    }

    private boolean acquireTenantProcessingSlot(UserTransferJob job) {
        tenantRepository.findByIdForUpdate(job.getTenantId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("租户不存在。"));
        return jobRepository.countByTenantIdAndStatusIn(job.getTenantId(), PROCESSING_STATUSES) == 0;
    }

    private void requireOwner(UserTransferJob job, UUID tenantId, UUID membershipId) {
        if (!job.getTenantId().equals(tenantId)
                || !job.getCreatedByMembershipId().equals(membershipId)) {
            throw new org.springframework.security.access.AccessDeniedException("不能访问其他管理员的用户传输任务。");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务预览结果序列化失败。", exception);
        }
    }

    private String limit(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
