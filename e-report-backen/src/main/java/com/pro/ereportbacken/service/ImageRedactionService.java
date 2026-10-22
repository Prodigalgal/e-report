package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.model.pojo.ImageRedactionJob;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.ImageRedactionJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ImageRedactionService {

    public static final String DEFAULT_POLICY_VERSION = "cmcc-brand-v1";

    private final ImageRedactionJobRepository jobRepository;
    private final AppConfigService appConfigService;
    private final int maxAttempts;

    public ImageRedactionService(
            ImageRedactionJobRepository jobRepository,
            AppConfigService appConfigService,
            @Value("${app.image-redaction.max-attempts:3}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.appConfigService = appConfigService;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 10));
    }

    public boolean submissionEnabled(UUID tenantId) {
        try {
            return appConfigService.getTenantConfigValueAsBoolean(
                    tenantId,
                    AppConfigConstant.IMAGE_REDACTION_SUBMISSION_ENABLED,
                    true
            );
        } catch (RuntimeException exception) {
            // 配置数据库瞬时不可用时仍允许上传，但必须先进入待提交状态，避免原图绕过安全处理。
            log.error("读取图片脱敏提交开关失败，按关闭提交处理。tenantId={}", tenantId, exception);
            return false;
        }
    }

    @Transactional
    public ImageRedactionJob enqueue(FileTransferUploadSession session) {
        ImageRedactionJob existing = jobRepository
                .findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ImageRedactionStatus.DEFERRED
                    && submissionEnabled(session.getTenantId())) {
                activate(existing);
                return jobRepository.save(existing);
            }
            return existing;
        }

        ImageRedactionJob job = new ImageRedactionJob();
        job.setTenantId(session.getTenantId());
        job.setUploadSessionId(session.getId());
        job.setStatus(submissionEnabled(session.getTenantId())
                ? ImageRedactionStatus.QUEUED
                : ImageRedactionStatus.DEFERRED);
        job.setSourceStoredFileName(session.getStoredFileName());
        job.setSourceContentType(session.getContentType());
        job.setSourceFileSize(session.getTotalSize());
        job.setAttemptCount(0);
        job.setMaxAttempts(maxAttempts);
        job.setAvailableAt(UtcTime.now());
        job.setPolicyVersion(DEFAULT_POLICY_VERSION);
        return jobRepository.save(job);
    }

    @Transactional
    public int releaseDeferred(UUID tenantId, int batchSize) {
        if (!submissionEnabled(tenantId)) {
            return 0;
        }
        int normalizedBatchSize = Math.max(1, Math.min(batchSize, 500));
        var pageable = PageRequest.of(0, normalizedBatchSize, Sort.by(Sort.Direction.ASC, "createdAt"));
        var deferredJobs = jobRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                tenantId,
                ImageRedactionStatus.DEFERRED,
                pageable
        );
        if (deferredJobs.isEmpty()) {
            return 0;
        }
        deferredJobs.forEach(this::activate);
        jobRepository.saveAll(deferredJobs);
        return deferredJobs.size();
    }

    @Transactional(readOnly = true)
    public Optional<ImageRedactionJob> find(FileTransferUploadSession session) {
        return jobRepository.findByTenantIdAndUploadSessionId(session.getTenantId(), session.getId());
    }

    @Transactional(readOnly = true)
    public SanitizedFile claimableFile(FileTransferUploadSession session) {
        ImageRedactionJob job = find(session).orElse(null);
        if (job == null) {
            if (isImage(session.getContentType())) {
                throw new IllegalStateException("图片安全处理任务尚未提交，请稍后重试。");
            }
            return new SanitizedFile(
                    session.getOriginalFileName(),
                    session.getStoredFileName(),
                    session.getContentType(),
                    session.getTotalSize(),
                    session.getActualSha256(),
                    null,
                    null,
                    null
            );
        }
        if (job.getStatus() == ImageRedactionStatus.CANCELLED) {
            throw new IllegalStateException("图片上传已终止，请重新上传。");
        }
        if (job.getStatus() == ImageRedactionStatus.READY) {
            requireCompleteOutput(job);
            return new SanitizedFile(
                    job.getOutputOriginalFileName(),
                    job.getOutputStoredFileName(),
                    job.getOutputContentType(),
                    job.getOutputFileSize(),
                    job.getOutputSha256(),
                    job.getId(),
                    job.getStatus(),
                    publicMessage(job)
            );
        }
        return new SanitizedFile(
                sanitizedName(session.getOriginalFileName()),
                pendingOutputStoredFileName(job.getId()),
                "image/jpeg",
                session.getTotalSize(),
                session.getActualSha256(),
                job.getId(),
                job.getStatus(),
                publicMessage(job)
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, ImageRedactionState> states(Collection<UUID> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) return Collections.emptyMap();
        return jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(ImageRedactionJob::getId, this::state));
    }

    @Transactional(readOnly = true)
    public ImageRedactionState state(UUID jobId) {
        if (jobId == null) return ImageRedactionState.NOT_REQUIRED;
        return jobRepository.findById(jobId).map(this::state).orElse(ImageRedactionState.NOT_REQUIRED);
    }

    @Transactional
    public CleanupFiles cancelIfPossible(FileTransferUploadSession session) {
        ImageRedactionJob job = find(session).orElse(null);
        if (job == null) return CleanupFiles.NONE;
        if (job.getStatus() == ImageRedactionStatus.PROCESSING) {
            throw new IllegalStateException("图片正在进行安全处理，暂时不能终止上传。");
        }
        job.setStatus(ImageRedactionStatus.CANCELLED);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setCompletedAt(UtcTime.now());
        jobRepository.save(job);
        return new CleanupFiles(job.getSourceStoredFileName(), job.getOutputStoredFileName());
    }

    public ImageRedactionState state(FileTransferUploadSession session) {
        return find(session)
                .map(this::state)
                .orElseGet(() -> isImage(session.getContentType())
                        && session.getStatus() != FileTransferStatus.INITIATED
                        ? ImageRedactionState.deferred()
                        : ImageRedactionState.NOT_REQUIRED);
    }

    private ImageRedactionState state(ImageRedactionJob job) {
        return new ImageRedactionState(
                job.getStatus(),
                publicMessage(job),
                job.getStatus() == ImageRedactionStatus.READY ? job.getOutputStoredFileName() : pendingOutputStoredFileName(job.getId()),
                job.getStatus() == ImageRedactionStatus.READY ? job.getOutputOriginalFileName() : null,
                job.getStatus() == ImageRedactionStatus.READY ? job.getOutputContentType() : null,
                job.getStatus() == ImageRedactionStatus.READY ? job.getOutputFileSize() : null
        );
    }

    private void activate(ImageRedactionJob job) {
        job.setStatus(ImageRedactionStatus.QUEUED);
        job.setAvailableAt(UtcTime.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(null);
    }

    private boolean isImage(String contentType) {
        return StringUtils.hasText(contentType)
                && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/");
    }

    private void requireCompleteOutput(ImageRedactionJob job) {
        if (!StringUtils.hasText(job.getOutputStoredFileName())
                || !StringUtils.hasText(job.getOutputOriginalFileName())
                || !StringUtils.hasText(job.getOutputSha256())
                || job.getOutputFileSize() == null) {
            throw new IllegalStateException("图片安全处理结果不完整，请联系管理员检查处理任务。");
        }
    }

    private String pendingOutputStoredFileName(UUID jobId) {
        return "_sanitized/" + jobId + ".jpg";
    }

    private String sanitizedName(String originalName) {
        String safeName = originalName == null ? "现场材料" : originalName.replace('\\', '/');
        int separator = safeName.lastIndexOf('/');
        safeName = separator >= 0 ? safeName.substring(separator + 1) : safeName;
        int extension = safeName.lastIndexOf('.');
        String stem = extension > 0 ? safeName.substring(0, extension) : safeName;
        return (StringUtils.hasText(stem) ? stem.strip() : "现场材料") + "-已脱敏.jpg";
    }

    private String publicMessage(ImageRedactionJob job) {
        return switch (job.getStatus()) {
            case QUEUED, RETRY_PENDING -> "图片已上传，正在等待安全处理。";
            case DEFERRED -> "图片已上传，等待管理员开启后提交安全处理。";
            case PROCESSING -> "正在识别并模糊图片中的品牌标识。";
            case READY -> "图片安全处理已完成。";
            case FAILED -> StringUtils.hasText(job.getErrorMessage())
                    ? job.getErrorMessage()
                    : "图片安全处理失败，请移除后重新上传。";
            case CANCELLED -> "图片上传已终止。";
        };
    }

    public record SanitizedFile(
            String originalFileName,
            String storedFileName,
            String contentType,
            long fileSize,
            String sha256,
            UUID redactionJobId,
            ImageRedactionStatus redactionStatus,
            String redactionMessage) {
    }

    public record CleanupFiles(String sourceStoredFileName, String outputStoredFileName) {
        private static final CleanupFiles NONE = new CleanupFiles(null, null);
    }

    public record ImageRedactionState(
            ImageRedactionStatus status,
            String message,
            String outputStoredFileName,
            String outputOriginalFileName,
            String outputContentType,
            Long outputFileSize) {
        private static final ImageRedactionState NOT_REQUIRED = new ImageRedactionState(
                null, null, null, null, null, null);

        private static ImageRedactionState deferred() {
            return new ImageRedactionState(
                    ImageRedactionStatus.DEFERRED,
                    "图片已上传，等待管理员开启后提交安全处理。",
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
