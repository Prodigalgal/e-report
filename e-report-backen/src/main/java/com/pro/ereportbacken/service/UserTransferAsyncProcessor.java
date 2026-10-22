package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.UserTransferJob;
import com.pro.ereportbacken.repository.UserTransferJobRepository;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantUserDetails;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Log4j2
public class UserTransferAsyncProcessor {

    private final UserTransferJobRepository jobRepository;
    private final UserTransferJobStateService stateService;
    private final UserTransferPlanService planService;
    private final UserTransferCommandExecutor commandExecutor;
    private final FileTransferService fileTransferService;
    private final UserDetailsServiceImpl userDetailsService;
    private final SecurityHashingService hashingService;
    private final UserExportService userExportService;
    private final UserTransferErrorWorkbookService errorWorkbookService;
    private final ObjectMapper objectMapper;

    public UserTransferAsyncProcessor(UserTransferJobRepository jobRepository,
                                      UserTransferJobStateService stateService,
                                      UserTransferPlanService planService,
                                      UserTransferCommandExecutor commandExecutor,
                                      FileTransferService fileTransferService,
                                      UserDetailsServiceImpl userDetailsService,
                                      SecurityHashingService hashingService,
                                      UserExportService userExportService,
                                      UserTransferErrorWorkbookService errorWorkbookService,
                                      ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.stateService = stateService;
        this.planService = planService;
        this.commandExecutor = commandExecutor;
        this.fileTransferService = fileTransferService;
        this.userDetailsService = userDetailsService;
        this.hashingService = hashingService;
        this.userExportService = userExportService;
        this.errorWorkbookService = errorWorkbookService;
        this.objectMapper = objectMapper;
    }

    @Async("userTransferTaskExecutor")
    public void validate(UUID jobId) {
        UserTransferJob started = stateService.startValidation(jobId);
        if (started == null) {
            return;
        }
        try {
            runAsOwner(started, () -> {
                FileTransferService.CompletedFile file = fileTransferService.loadClaimedFile(
                        started.getInputFileId(), FileTransferPurpose.USER_IMPORT);
                UserTransferPlanService.TransferPlan plan = planService.build(file, started.getCreatedAt());
                String previewDigest = previewDigest(started.getInputSha256(), plan);
                stateService.completeValidation(jobId, plan, previewDigest);
                if (plan.invalidRows() > 0) {
                    attachErrorFile(jobId);
                }
            });
        } catch (RuntimeException exception) {
            log.warn("用户传输任务预览失败。jobId={}, message={}", jobId, exception.getMessage(), exception);
            stateService.fail(jobId, safeMessage(exception, "用户传输文件校验失败。"));
        }
    }

    @Async("userTransferTaskExecutor")
    public void execute(UUID jobId) {
        UserTransferJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("用户传输任务不存在。"));
        try {
            runAsOwner(job, () -> {
                UserTransferJob started = stateService.startQueuedExecution(jobId);
                if (started != null) {
                    executeAsOwner(started);
                }
            });
        } catch (RuntimeException exception) {
            log.error("用户传输任务执行失败。jobId={}, message={}", jobId, exception.getMessage(), exception);
            stateService.fail(jobId, safeMessage(exception, "用户传输任务执行失败。"));
        }
    }

    @Async("userTransferTaskExecutor")
    public void export(UUID jobId) {
        UserTransferJob started = stateService.startExport(jobId);
        if (started == null) {
            return;
        }
        try {
            runAsOwner(started, () -> {
                try {
                    UserTransferExportRequestDTO request = objectMapper.readValue(
                            started.getSummaryJson(), UserTransferExportRequestDTO.class);
                    UserExportService.PreparedExport prepared = userExportService.prepareJob(
                            request, started.getPreviewSnapshotDigest(), started.getPreviewExpiresAt());
                    UserExportService.UserExportArtifact artifact = userExportService.export(prepared);
                    stateService.completeExport(jobId, artifact.file(), Math.toIntExact(artifact.snapshot().userCount()));
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalStateException("导出任务筛选快照损坏。", exception);
                }
            });
        } catch (UserExportService.UserExportSnapshotChangedException exception) {
            log.warn("用户导出任务快照失效。jobId={}, message={}", jobId, exception.getMessage());
            stateService.markStale(jobId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("用户导出任务执行失败。jobId={}, message={}", jobId, exception.getMessage(), exception);
            stateService.fail(jobId, safeMessage(exception, "用户导出任务执行失败。"));
        }
    }

    private void executeAsOwner(UserTransferJob job) {
        FileTransferService.CompletedFile file = fileTransferService.loadClaimedFile(
                job.getInputFileId(), FileTransferPurpose.USER_IMPORT);
        UserTransferPlanService.TransferPlan plan = planService.build(file, job.getCreatedAt());
        String recalculatedDigest = previewDigest(job.getInputSha256(), plan);
        if (!Objects.equals(job.getCatalogDigest(), plan.catalogDigest())
                || !Objects.equals(job.getUserSnapshotDigest(), plan.userSnapshotDigest())
                || !Objects.equals(job.getPreviewSnapshotDigest(), recalculatedDigest)
                || plan.invalidRows() > 0) {
            stateService.markStale(job.getId(),
                    "用户、授权、岗位模板、组织目录或操作者权限已变化，请重新预览后执行。");
            return;
        }
        for (UserTransferPlanService.UserExecutionPlan execution : plan.executions()) {
            String phoneHash = hashingService.protectedHash("user-transfer-phone", execution.phone());
            try {
                UserTransferCommandExecutor.UserExecutionResult result = commandExecutor.execute(execution);
                stateService.markUserResult(job.getId(), phoneHash, true, "执行成功。", result.authorizationVersion());
            } catch (RuntimeException exception) {
                log.warn("用户传输单用户事务失败。jobId={}, phoneHash={}, message={}",
                        job.getId(), phoneHash, exception.getMessage());
                stateService.markUserResult(job.getId(), phoneHash, false,
                        safeMessage(exception, "该用户命令执行失败，事务已回滚。"), null);
            }
        }
        UserTransferJob executionState = jobRepository.findById(job.getId()).orElseThrow();
        if (executionState.getFailedRows() > 0) {
            attachErrorFile(job.getId());
        }
        stateService.completeExecution(job.getId());
    }

    private void attachErrorFile(UUID jobId) {
        try {
            stateService.attachErrorFile(jobId, errorWorkbookService.create(jobId));
        } catch (RuntimeException exception) {
            log.error("用户导入失败结果文件生成失败。jobId={}, message={}",
                    jobId, exception.getMessage(), exception);
        }
    }

    private void runAsOwner(UserTransferJob job, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        try {
            TenantUserDetails principal = userDetailsService.loadForSession(
                    job.getTenantId(), job.getCreatedByMembershipId());
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private String previewDigest(String inputSha256, UserTransferPlanService.TransferPlan plan) {
        return hashingService.sha256(String.join("|",
                String.valueOf(inputSha256), plan.templateVersion(),
                plan.catalogDigest(), plan.userSnapshotDigest(),
                plan.planDigest(),
                String.valueOf(plan.validRows()), String.valueOf(plan.invalidRows())));
    }

    private String safeMessage(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
