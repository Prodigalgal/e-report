package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import com.pro.ereportbacken.model.pojo.AttachmentStage;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.AttachmentRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Log4j2
public class WorkOrderAttachmentService {

    private final WorkOrderRepository workOrderRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final FileTransferService fileTransferService;
    private final WorkOrderAccessPolicy workOrderAccessPolicy;
    private final ImageRedactionService imageRedactionService;

    public WorkOrderAttachmentService(WorkOrderRepository workOrderRepository,
                                      AttachmentRepository attachmentRepository,
                                      FileStorageService fileStorageService,
                                      FileTransferService fileTransferService,
                                      WorkOrderAccessPolicy workOrderAccessPolicy,
                                      ImageRedactionService imageRedactionService) {
        this.workOrderRepository = workOrderRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.fileTransferService = fileTransferService;
        this.workOrderAccessPolicy = workOrderAccessPolicy;
        this.imageRedactionService = imageRedactionService;
    }

    public void attachTransferredFiles(WorkOrder workOrder,
                                       List<UUID> uploadedAttachmentIds,
                                       AttachmentStage stage,
                                       boolean required) {
        List<FileTransferService.ClaimedFile> claimedFiles = claimCompletedFiles(uploadedAttachmentIds);
        if (required && claimedFiles.isEmpty()) {
            throw new IllegalArgumentException(stage == AttachmentStage.REPORT
                    ? "请至少上传一份现场附件。"
                    : "请至少上传一张回单照片。");
        }
        validateTotalSize(claimedFiles);
        claimedFiles.forEach(claimedFile -> workOrder.addAttachment(toAttachment(claimedFile, stage)));
    }

    @Transactional(readOnly = true)
    public AttachmentResource loadAttachmentResource(UUID workOrderId, String storedFileName) throws FileNotFoundException {
        Attachment attachment = attachmentRepository.findByTenantIdAndWorkOrder_IdAndStoredFileName(
                        workOrderAccessPolicy.currentTenantId(),
                        workOrderId,
                        storedFileName
                )
                .orElseThrow(() -> new FileNotFoundException(
                        "附件不存在或不属于当前工单：" + storedFileName));
        return loadAttachmentResource(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentResource loadAttachmentResource(UUID workOrderId, UUID attachmentId) throws FileNotFoundException {
        Attachment attachment = attachmentRepository.findByTenantIdAndWorkOrder_IdAndId(
                        workOrderAccessPolicy.currentTenantId(),
                        workOrderId,
                        attachmentId
                )
                .orElseThrow(() -> new FileNotFoundException(
                        "附件不存在或不属于当前工单：" + attachmentId));
        return loadAttachmentResource(attachment);
    }

    private AttachmentResource loadAttachmentResource(Attachment attachment) throws FileNotFoundException {
        workOrderAccessPolicy.requireAttachmentAccess(attachment.getWorkOrder());
        ImageRedactionService.ImageRedactionState state = imageRedactionService.state(attachment.getImageRedactionJobId());
        requireAttachmentReady(attachment, state);
        String resolvedStoredFileName = state.outputStoredFileName() == null
                ? attachment.getStoredFileName()
                : state.outputStoredFileName();
        Resource resource = fileStorageService.loadFileAsResource(resolvedStoredFileName);
        return new AttachmentResource(
                resource,
                state.outputOriginalFileName() == null ? attachment.getFileName() : state.outputOriginalFileName(),
                state.outputContentType() == null ? attachment.getFileType() : state.outputContentType());
    }

    public String probeContentType(Path filePath) throws IOException {
        String contentType = Files.probeContentType(filePath);
        return contentType == null ? "application/octet-stream" : contentType;
    }

    @Transactional(readOnly = true)
    public ZipArchiveResource createAttachmentsZipResource(UUID workOrderId) throws IOException {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        workOrderAccessPolicy.requireAttachmentAccess(workOrder);
        List<Attachment> attachments = workOrder.getAttachments();
        if (attachments.isEmpty()) {
            throw new FileNotFoundException("当前工单没有可下载的附件：" + workOrderId);
        }

        UUID tenantId = workOrder.getTenantId();
        String zipFileName = "work_order_" + workOrderId + "_attachments.zip";
        Path zipPath = Files.createTempFile(
                prepareZipCacheDirectory(),
                "tenant_" + tenantId + "_work_order_" + workOrderId + "_",
                ".zip"
        );
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Attachment attachment : attachments) {
                appendAttachment(zipOutputStream, workOrderId, attachment);
            }
            zipOutputStream.finish();
        } catch (IOException exception) {
            Files.deleteIfExists(zipPath);
            throw exception;
        }
        zipPath.toFile().deleteOnExit();
        log.info("Successfully created ZIP file {} for work order {}", zipPath, workOrderId);
        return new ZipArchiveResource(new UrlResource(zipPath.toUri()), zipFileName, Files.size(zipPath));
    }

    private List<FileTransferService.ClaimedFile> claimCompletedFiles(List<UUID> uploadedAttachmentIds) {
        if (uploadedAttachmentIds == null || uploadedAttachmentIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> distinctUploadIds = uploadedAttachmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUploadIds.size() > WorkOrderAttachmentLimits.MAX_FILE_COUNT) {
            throw new IllegalArgumentException(
                    WorkOrderAttachmentLimits.countLimitMessage(distinctUploadIds.size()));
        }
        return distinctUploadIds.stream()
                .map(uploadId -> fileTransferService.claimCompletedFile(
                        uploadId,
                        FileTransferPurpose.WORK_ORDER_ATTACHMENT
                ))
                .toList();
    }

    private void validateTotalSize(List<FileTransferService.ClaimedFile> claimedFiles) {
        long totalBytes = claimedFiles.stream().mapToLong(FileTransferService.ClaimedFile::fileSize).sum();
        if (totalBytes > WorkOrderAttachmentLimits.MAX_TOTAL_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    WorkOrderAttachmentLimits.totalLimitMessage(totalBytes)
            );
        }
    }

    private Attachment toAttachment(FileTransferService.ClaimedFile claimedFile, AttachmentStage stage) {
        Attachment attachment = new Attachment();
        attachment.setFileName(claimedFile.originalFileName());
        attachment.setStoredFileName(claimedFile.storedFileName());
        attachment.setFileType(claimedFile.contentType());
        attachment.setFileSize(claimedFile.fileSize());
        attachment.setFilePath(claimedFile.storedFileName());
        attachment.setImageRedactionJobId(claimedFile.imageRedactionJobId());
        attachment.setAttachmentStage(stage);
        return attachment;
    }

    private WorkOrder findWorkOrder(UUID workOrderId) {
        return workOrderRepository.findByTenantIdAndId(workOrderAccessPolicy.currentTenantId(), workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("工单不存在：" + workOrderId));
    }

    private void appendAttachment(ZipOutputStream zipOutputStream,
                                  UUID workOrderId,
                                  Attachment attachment) throws IOException {
        ImageRedactionService.ImageRedactionState state = imageRedactionService.state(attachment.getImageRedactionJobId());
        if (requiresRedaction(attachment) && state.status() == null) {
            appendUnavailableAttachmentNote(zipOutputStream, attachment, state);
            return;
        }
        if (state.status() != null && state.status() != ImageRedactionStatus.READY) {
            appendUnavailableAttachmentNote(zipOutputStream, attachment, state);
            return;
        }
        String entryName = state.outputOriginalFileName() == null
                ? attachment.getFileName()
                : state.outputOriginalFileName();
        if (!isSafeEntryName(entryName)) {
            log.warn("Skipping potentially malicious entry name: {} for attachment ID: {} in work order {}",
                    entryName, attachment.getId(), workOrderId);
            return;
        }

        Path filePath = fileStorageService.getFileStorageLocation()
                .resolve(state.outputStoredFileName() == null
                        ? attachment.getStoredFileName()
                        : state.outputStoredFileName())
                .normalize();
        if (Files.exists(filePath) && Files.isReadable(filePath)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            Files.copy(filePath, zipOutputStream);
            zipOutputStream.closeEntry();
            return;
        }

        log.error("Attachment file not found or not readable: {} for attachment ID: {} in work order {}",
                filePath, attachment.getId(), workOrderId);
        zipOutputStream.putNextEntry(new ZipEntry(entryName + "_MISSING.txt"));
        zipOutputStream.write(("服务器上未找到附件：" + entryName)
                .getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private void requireAttachmentReady(Attachment attachment,
                                        ImageRedactionService.ImageRedactionState state) {
        if (requiresRedaction(attachment) && state.status() == null) {
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "图片已进入安全处理队列，请稍后再试。");
        }
        requireAttachmentReady(state);
    }

    private void requireAttachmentReady(ImageRedactionService.ImageRedactionState state) {
        if (state.status() == null || state.status() == ImageRedactionStatus.READY) return;
        HttpStatus status = state.status() == ImageRedactionStatus.FAILED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.TOO_EARLY;
        throw new ResponseStatusException(status, state.message());
    }

    private boolean requiresRedaction(Attachment attachment) {
        if (attachment.getFileType() == null
                || !attachment.getFileType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return false;
        }
        String storedFileName = attachment.getStoredFileName();
        return storedFileName == null
                || !storedFileName.replace('\\', '/').toLowerCase(Locale.ROOT).startsWith("_sanitized/");
    }

    private void appendUnavailableAttachmentNote(ZipOutputStream zipOutputStream,
                                                 Attachment attachment,
                                                 ImageRedactionService.ImageRedactionState state) throws IOException {
        String suffix = state.status() == ImageRedactionStatus.FAILED
                ? "_处理失败.txt"
                : state.status() == ImageRedactionStatus.DEFERRED
                        ? "_待提交.txt"
                        : "_处理中.txt";
        String entryName = attachment.getFileName() + suffix;
        if (!isSafeEntryName(entryName)) return;
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write((state.message() == null ? "附件正在后台进行安全处理。" : state.message())
                .getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private boolean isSafeEntryName(String entryName) {
        return StringUtils.hasText(entryName)
                && !entryName.contains("..")
                && !entryName.startsWith("/")
                && !entryName.startsWith("\\");
    }

    private Path prepareZipCacheDirectory() throws IOException {
        Path zipCacheDirectory = fileStorageService.getFileStorageLocation().resolve("_zip-cache").normalize();
        Files.createDirectories(zipCacheDirectory);
        Instant expiredBefore = Instant.now().minus(Duration.ofHours(6));
        try (var paths = Files.list(zipCacheDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isExpired(path, expiredBefore))
                    .forEach(this::deleteQuietly);
        }
        return zipCacheDirectory;
    }

    private boolean isExpired(Path path, Instant expiredBefore) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(expiredBefore);
        } catch (IOException exception) {
            log.warn("Failed to inspect ZIP cache file timestamp: {}", path, exception);
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete expired ZIP cache file: {}", path, exception);
        }
    }

    public record AttachmentResource(Resource resource, String originalFileName, String contentType) {
    }

    public record ZipArchiveResource(Resource resource, String fileName, long fileSize) {
    }
}

