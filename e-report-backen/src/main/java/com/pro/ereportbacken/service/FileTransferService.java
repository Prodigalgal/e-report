package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.FileTransferChunkResponseDTO;
import com.pro.ereportbacken.config.WorkOrderAttachmentLimits;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.dto.FileTransferSessionDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import com.pro.ereportbacken.security.SystemAuthority;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class FileTransferService {

    public static final long DEFAULT_CHUNK_BYTES = 2L * 1024L * 1024L;
    private static final long MIN_CHUNK_BYTES = 256L * 1024L;
    private static final long MAX_CHUNK_BYTES = 8L * 1024L * 1024L;
    private static final long GENERATED_FILE_MAX_BYTES = 50L * 1024L * 1024L;
    private static final long USER_IMPORT_MAX_BYTES = 20L * 1024L * 1024L;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String PENDING_TEMP_FILE_NAME = "pending";
    private static final String GENERATED_TEMP_FILE_NAME = "generated";
    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("^bytes\\s+(\\d+)-(\\d+)/(\\d+)$");

    private final FileTransferUploadSessionRepository uploadSessionRepository;
    private final FileStorageService fileStorageService;
    private final AppReleaseStorageService appReleaseStorageService;
    private final Path tempStorageLocation;
    private final long maxApkSizeBytes;
    private final WorkOrderAccessPolicy workOrderAccessPolicy;
    private final WorkOrderImageTypeSniffer imageTypeSniffer;
    private final ImageRedactionService imageRedactionService;

    public FileTransferService(FileTransferUploadSessionRepository uploadSessionRepository,
                               FileStorageService fileStorageService,
                               AppReleaseStorageService appReleaseStorageService,
                               WorkOrderAccessPolicy workOrderAccessPolicy,
                               WorkOrderImageTypeSniffer imageTypeSniffer,
                               ImageRedactionService imageRedactionService,
                               @Value("${file.transfer-temp-dir:./file_transfer_tmp}") String tempDir,
                               @Value("${app.update.max-apk-size:300MB}") String maxApkSize) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.fileStorageService = fileStorageService;
        this.appReleaseStorageService = appReleaseStorageService;
        this.workOrderAccessPolicy = workOrderAccessPolicy;
        this.imageTypeSniffer = imageTypeSniffer;
        this.imageRedactionService = imageRedactionService;
        this.tempStorageLocation = Path.of(tempDir).toAbsolutePath().normalize();
        this.maxApkSizeBytes = DataSize.parse(maxApkSize).toBytes();
        try {
            Files.createDirectories(this.tempStorageLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建文件分片临时目录，请检查服务磁盘权限。", ex);
        }
    }

    @Transactional
    public FileTransferSessionDTO initiate(FileTransferInitRequestDTO request) {
        Authentication authentication = requireAuthentication();
        requirePurposePermission(request.purpose(), authentication);
        validateInitRequest(request);

        FileTransferUploadSession session = new FileTransferUploadSession();
        com.pro.ereportbacken.model.pojo.User currentUser = workOrderAccessPolicy.currentUserOrThrow();
        session.setTenantId(currentUser.getTenantId());
        session.setCreatedByMembershipId(currentUser.getId());
        session.setPurpose(request.purpose());
        session.setOriginalFileName(cleanFileName(request.fileName()));
        session.setContentType(normalizeContentType(request.contentType()));
        session.setTotalSize(request.totalSize());
        session.setExpectedSha256(normalizeSha256(request.sha256()));
        session.setChunkSize(normalizeChunkSize(request.chunkSize()));
        session.setTempFileName(PENDING_TEMP_FILE_NAME);
        session.setCreatedByUsername(authentication.getName());
        session.setExpiresAt(UtcTime.now().plusHours(6));
        FileTransferUploadSession persistedSession = uploadSessionRepository.save(session);
        String tempFileName = persistedSession.getId() + ".part";
        persistedSession.setTempFileName(tempFileName);
        try {
            Files.createFile(tempPath(tempFileName));
        } catch (IOException ex) {
            throw new IllegalStateException("创建上传临时文件失败，请稍后重试。", ex);
        }
        return toDTO(persistedSession);
    }

    @Transactional(readOnly = true)
    public FileTransferSessionDTO getSession(UUID uploadId) {
        FileTransferUploadSession session = findSession(uploadId);
        requireSessionOwner(session);
        return toDTO(session);
    }

    @Transactional
    public FileTransferChunkResponseDTO uploadChunk(UUID uploadId,
                                                    int chunkIndex,
                                                    String contentRange,
                                                    byte[] chunkBytes) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionWritable(session);
        ParsedContentRange range = parseContentRange(contentRange, session.getTotalSize());
        validateChunk(chunkIndex, range, session, chunkBytes);
        if (range.end() + 1 <= session.getReceivedBytes()) {
            return new FileTransferChunkResponseDTO(
                    session.getId(),
                    chunkIndex,
                    session.getReceivedBytes(),
                    session.getTotalSize(),
                    session.getReceivedBytes() >= session.getTotalSize()
            );
        }

        Path tempFile = tempPath(session.getTempFileName());
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(tempFile.toFile(), "rw")) {
            randomAccessFile.seek(range.start());
            randomAccessFile.write(chunkBytes);
        } catch (IOException ex) {
            throw new IllegalStateException("分片写入失败，请稍后自动重试。", ex);
        }

        long receivedBytes = Math.max(session.getReceivedBytes(), range.end() + 1);
        session.setReceivedBytes(receivedBytes);
        uploadSessionRepository.save(session);
        return new FileTransferChunkResponseDTO(
                session.getId(),
                chunkIndex,
                receivedBytes,
                session.getTotalSize(),
                receivedBytes >= session.getTotalSize()
        );
    }

    @Transactional
    public FileTransferSessionDTO complete(UUID uploadId) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        if (FileTransferStatus.COMPLETED.equals(session.getStatus())) {
            return toDTO(session);
        }
        requireSessionWritable(session);
        Path tempFile = tempPath(session.getTempFileName());
        try {
            long actualSize = Files.size(tempFile);
            if (actualSize != session.getTotalSize()) {
                throw new IllegalStateException("文件还没有上传完整，请等待自动重试完成后再提交。");
            }
            String actualSha256 = sha256(tempFile);
            String expectedSha256 = session.getExpectedSha256();
            if (StringUtils.hasText(expectedSha256) && !expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new IllegalStateException("文件校验失败，系统会重新上传该文件。");
            }
            WorkOrderImageTypeSniffer.Detection mediaDetection = inspectWorkOrderMedia(session, tempFile);
            storeCompletedFile(session, tempFile, actualSha256, mediaDetection);
            session.setActualSha256(actualSha256);
            session.setReceivedBytes(session.getTotalSize());
            session.setStatus(FileTransferStatus.COMPLETED);
            FileTransferUploadSession saved = uploadSessionRepository.save(session);
            if (mediaDetection.image()) {
                try {
                    imageRedactionService.enqueue(saved);
                } catch (RuntimeException exception) {
                    fileStorageService.deleteFile(saved.getStoredFileName());
                    throw exception;
                }
            }
            return toDTO(saved);
        } catch (IOException ex) {
            throw new IllegalStateException("文件校验失败，请稍后重试。", ex);
        }
    }

    @Transactional
    public ClaimedFile claimCompletedFile(UUID uploadId, FileTransferPurpose expectedPurpose) {
        return claimCompletedFile(uploadId, expectedPurpose, null);
    }

    @Transactional
    public ClaimedFile claimCompletedFile(UUID uploadId,
                                          FileTransferPurpose expectedPurpose,
                                          Duration retention) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        if (!expectedPurpose.equals(session.getPurpose())) {
            throw new IllegalArgumentException("上传文件用途不匹配，请重新选择文件。");
        }
        if (!FileTransferStatus.COMPLETED.equals(session.getStatus())) {
            throw new IllegalStateException("文件还没有完成上传，请等待自动重试完成。");
        }
        ImageRedactionService.SanitizedFile sanitizedFile = imageRedactionService.claimableFile(session);
        session.setStatus(FileTransferStatus.CLAIMED);
        if (retention != null) {
            if (retention.isNegative() || retention.isZero() || retention.compareTo(Duration.ofDays(30)) > 0) {
                throw new IllegalArgumentException("任务输入文件保留时间必须在1秒到30天之间。");
            }
            session.setExpiresAt(UtcTime.now().plus(retention));
        }
        uploadSessionRepository.save(session);
        return new ClaimedFile(
                session.getId(),
                session.getPurpose(),
                sanitizedFile.originalFileName(),
                sanitizedFile.storedFileName(),
                sanitizedFile.contentType(),
                sanitizedFile.fileSize(),
                sanitizedFile.sha256(),
                sanitizedFile.redactionJobId(),
                sanitizedFile.redactionStatus(),
                sanitizedFile.redactionMessage()
        );
    }

    public GeneratedFile registerGeneratedFile(FileTransferPurpose purpose,
                                               String fileName,
                                               String contentType,
                                               byte[] content,
                                               Duration retention) {
        Authentication authentication = requireAuthentication();
        validateGeneratedFileRequest(purpose, retention);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("生成文件内容不能为空。");
        }
        if (content.length > GENERATED_FILE_MAX_BYTES) {
            throw new IllegalArgumentException("生成文件超过50兆字节，请缩小导出范围后重试。");
        }
        String cleanedFileName = cleanFileName(fileName);
        String normalizedContentType = normalizeContentType(contentType);
        String actualSha256 = sha256(content);
        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(
                cleanedFileName,
                new ByteArrayInputStream(content)
        );
        return registerGeneratedFileSession(
                purpose, cleanedFileName, normalizedContentType, actualSha256,
                retention, authentication, storedFile);
    }

    public GeneratedFile registerGeneratedFile(FileTransferPurpose purpose,
                                               String fileName,
                                               String contentType,
                                               Path content,
                                               Duration retention) {
        Authentication authentication = requireAuthentication();
        validateGeneratedFileRequest(purpose, retention);
        if (content == null || !Files.isRegularFile(content)) {
            throw new IllegalArgumentException("生成文件内容不存在。");
        }
        try {
            long fileSize = Files.size(content);
            if (fileSize <= 0) {
                throw new IllegalArgumentException("生成文件内容不能为空。");
            }
            if (fileSize > GENERATED_FILE_MAX_BYTES) {
                throw new IllegalArgumentException("生成文件超过50兆字节，请缩小导出范围后重试。");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取生成文件大小。", exception);
        }
        String cleanedFileName = cleanFileName(fileName);
        String normalizedContentType = normalizeContentType(contentType);
        String actualSha256 = sha256(content);
        FileStorageService.StoredFile storedFile = fileStorageService.storeCompletedFile(cleanedFileName, content);
        return registerGeneratedFileSession(
                purpose, cleanedFileName, normalizedContentType, actualSha256,
                retention, authentication, storedFile);
    }

    private GeneratedFile registerGeneratedFileSession(FileTransferPurpose purpose,
                                                       String cleanedFileName,
                                                       String normalizedContentType,
                                                       String actualSha256,
                                                       Duration retention,
                                                       Authentication authentication,
                                                       FileStorageService.StoredFile storedFile) {
        try {
            com.pro.ereportbacken.model.pojo.User currentUser = workOrderAccessPolicy.currentUserOrThrow();
            FileTransferUploadSession session = new FileTransferUploadSession();
            session.setTenantId(currentUser.getTenantId());
            session.setCreatedByMembershipId(currentUser.getId());
            session.setPurpose(purpose);
            session.setStatus(FileTransferStatus.COMPLETED);
            session.setOriginalFileName(cleanedFileName);
            session.setContentType(normalizedContentType);
            session.setTotalSize(storedFile.size());
            session.setExpectedSha256(actualSha256);
            session.setActualSha256(actualSha256);
            session.setChunkSize(DEFAULT_CHUNK_BYTES);
            session.setReceivedBytes(storedFile.size());
            session.setTempFileName(GENERATED_TEMP_FILE_NAME);
            session.setStoredFileName(storedFile.storedFileName());
            session.setCreatedByUsername(authentication.getName());
            session.setExpiresAt(UtcTime.now().plus(retention));
            return toGeneratedFile(uploadSessionRepository.save(session));
        } catch (RuntimeException exception) {
            fileStorageService.deleteFile(storedFile.storedFileName());
            throw exception;
        }
    }

    private void validateGeneratedFileRequest(FileTransferPurpose purpose, Duration retention) {
        if (!isServerGeneratedPurpose(purpose)) {
            throw new IllegalArgumentException("文件用途不支持服务端生成。");
        }
        if (retention == null || retention.isNegative() || retention.isZero()
                || retention.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("生成文件保留时间必须在1秒到24小时之间。");
        }
    }

    @Transactional(readOnly = true)
    public CompletedFile loadCompletedFile(UUID fileId, FileTransferPurpose expectedPurpose) {
        FileTransferUploadSession session = findSession(fileId);
        requireSessionOwner(session);
        requireExpectedPurpose(session, expectedPurpose);
        requireCompletedAndActive(session);
        return toCompletedFile(session);
    }

    @Transactional
    public CompletedFile beginProcessing(UUID uploadId, FileTransferPurpose expectedPurpose) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        requireExpectedPurpose(session, expectedPurpose);
        requireCompletedAndActive(session);
        session.setStatus(FileTransferStatus.PROCESSING);
        uploadSessionRepository.save(session);
        return toCompletedFile(session);
    }

    @Transactional(readOnly = true)
    public CompletedFile loadClaimedFile(UUID uploadId, FileTransferPurpose expectedPurpose) {
        FileTransferUploadSession session = findSession(uploadId);
        requireSessionOwner(session);
        requireExpectedPurpose(session, expectedPurpose);
        if (!FileTransferStatus.CLAIMED.equals(session.getStatus())) {
            throw new IllegalStateException("文件尚未绑定业务任务或已经失效。");
        }
        if (session.getExpiresAt().isBefore(UtcTime.now())) {
            throw new IllegalStateException("任务输入文件已经过期，请重新发起操作。");
        }
        return toCompletedFile(session);
    }

    @Transactional
    public String finishProcessing(UUID uploadId, FileTransferPurpose expectedPurpose) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        requireExpectedPurpose(session, expectedPurpose);
        if (!FileTransferStatus.PROCESSING.equals(session.getStatus())) {
            throw new IllegalStateException("文件当前不在处理中，不能重复提交。");
        }
        session.setStatus(FileTransferStatus.CLAIMED);
        uploadSessionRepository.save(session);
        return session.getStoredFileName();
    }

    @Transactional
    public void releaseProcessing(UUID uploadId, FileTransferPurpose expectedPurpose) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        requireExpectedPurpose(session, expectedPurpose);
        if (FileTransferStatus.PROCESSING.equals(session.getStatus())) {
            session.setStatus(FileTransferStatus.COMPLETED);
            uploadSessionRepository.save(session);
        }
    }

    public void deleteStoredTransferFile(String storedFileName) {
        if (StringUtils.hasText(storedFileName)) {
            fileStorageService.deleteFile(storedFileName);
        }
    }

    @Transactional
    public ExpiredFile expireSession(UUID tenantId, UUID fileId, LocalDateTime cutoff) {
        FileTransferUploadSession session = uploadSessionRepository.findByTenantIdAndIdForUpdate(tenantId, fileId)
                .orElse(null);
        if (session == null || !session.getExpiresAt().isBefore(cutoff)) {
            return null;
        }
        boolean initiated = FileTransferStatus.INITIATED.equals(session.getStatus());
        boolean temporaryCompleted = isTemporaryBusinessFile(session.getPurpose())
                && (FileTransferStatus.COMPLETED.equals(session.getStatus())
                || FileTransferStatus.PROCESSING.equals(session.getStatus()));
        if (!initiated && !temporaryCompleted) {
            return null;
        }
        session.setStatus(FileTransferStatus.ABORTED);
        uploadSessionRepository.save(session);
        ImageRedactionService.CleanupFiles cleanupFiles = imageRedactionService.cancelIfPossible(session);
        return new ExpiredFile(
                initiated && !GENERATED_TEMP_FILE_NAME.equals(session.getTempFileName())
                        ? session.getTempFileName()
                        : null,
                temporaryCompleted ? session.getStoredFileName() : null,
                cleanupFiles.outputStoredFileName()
        );
    }

    @Transactional
    public ExpiredFile expireClaimedUserImport(UUID tenantId, UUID fileId, LocalDateTime cutoff) {
        FileTransferUploadSession session = uploadSessionRepository.findByTenantIdAndIdForUpdate(tenantId, fileId)
                .orElse(null);
        if (session == null
                || session.getPurpose() != FileTransferPurpose.USER_IMPORT
                || session.getStatus() != FileTransferStatus.CLAIMED
                || !session.getExpiresAt().isBefore(cutoff)) {
            return null;
        }
        session.setStatus(FileTransferStatus.ABORTED);
        uploadSessionRepository.save(session);
        return new ExpiredFile(null, session.getStoredFileName(), null);
    }

    public void deleteExpiredFiles(ExpiredFile expiredFile) {
        if (expiredFile == null) {
            return;
        }
        if (StringUtils.hasText(expiredFile.tempFileName())) {
            try {
                Files.deleteIfExists(tempPath(expiredFile.tempFileName()));
            } catch (IOException exception) {
                log.warn("过期上传临时文件清理失败。tempFileName={}", expiredFile.tempFileName(), exception);
            }
        }
        deleteStoredTransferFile(expiredFile.storedFileName());
        deleteStoredTransferFile(expiredFile.additionalStoredFileName());
    }

    @Transactional
    public void abort(UUID uploadId) {
        FileTransferUploadSession session = findSessionForUpdate(uploadId);
        requireSessionOwner(session);
        if (FileTransferStatus.CLAIMED.equals(session.getStatus())
                || FileTransferStatus.PROCESSING.equals(session.getStatus())) {
            throw new IllegalStateException("文件正在处理或已被业务使用，不能终止。");
        }
        ImageRedactionService.CleanupFiles cleanupFiles = imageRedactionService.cancelIfPossible(session);
        session.setStatus(FileTransferStatus.ABORTED);
        uploadSessionRepository.save(session);
        deleteTempFile(session);
        deleteStoredTransferFile(cleanupFiles.sourceStoredFileName());
        deleteStoredTransferFile(cleanupFiles.outputStoredFileName());
        if (isTemporaryBusinessFile(session.getPurpose())) {
            deleteStoredTransferFile(session.getStoredFileName());
        }
    }

    private void validateInitRequest(FileTransferInitRequestDTO request) {
        if (request.purpose() == null) {
            throw new IllegalArgumentException("文件用途不能为空。");
        }
        long maxBytes = maxUploadBytes(request.purpose());
        if (request.totalSize() == null || request.totalSize() <= 0) {
            throw new IllegalArgumentException("文件大小必须大于0。");
        }
        if (request.totalSize() > maxBytes) {
            throw new IllegalArgumentException(uploadSizeLimitMessage(request.purpose()));
        }
        if (FileTransferPurpose.APP_RELEASE.equals(request.purpose())
                && !cleanFileName(request.fileName()).toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new IllegalArgumentException("只允许上传安卓应用安装包。");
        }
        if (FileTransferPurpose.USER_IMPORT.equals(request.purpose())
                && !cleanFileName(request.fileName()).toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("用户导入仅支持 .xlsx 文件。");
        }
    }

    private long maxUploadBytes(FileTransferPurpose purpose) {
        if (FileTransferPurpose.APP_RELEASE.equals(purpose)) {
            return maxApkSizeBytes;
        }
        if (FileTransferPurpose.USER_IMPORT.equals(purpose)) {
            return USER_IMPORT_MAX_BYTES;
        }
        return WorkOrderAttachmentLimits.MAX_TOTAL_BYTES;
    }

    private String uploadSizeLimitMessage(FileTransferPurpose purpose) {
        if (FileTransferPurpose.APP_RELEASE.equals(purpose)) {
            return "安装包大小超过系统限制，请压缩或重新构建后上传。";
        }
        if (FileTransferPurpose.USER_IMPORT.equals(purpose)) {
            return "用户导入文件不能超过 20 MiB。";
        }
        return WorkOrderAttachmentLimits.SINGLE_FILE_LIMIT_MESSAGE;
    }

    private void requirePurposePermission(FileTransferPurpose purpose, Authentication authentication) {
        if (FileTransferPurpose.USER_IMPORT_TEMPLATE.equals(purpose)
                || FileTransferPurpose.USER_EXPORT.equals(purpose)) {
            throw new AccessDeniedException("该文件用途只能由服务端生成。");
        }
        if (FileTransferPurpose.USER_IMPORT.equals(purpose)) {
            requireAnyAuthority(authentication, PermissionCatalog.USER_IMPORT_PREVIEW);
            return;
        }
        if (!FileTransferPurpose.APP_RELEASE.equals(purpose)) {
            return;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> SystemAuthority.SUPER_ADMINISTRATOR.equals(authority.getAuthority())
                        || PermissionCatalog.APP_UPDATE_PUBLISH.equals(authority.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("当前账号没有发布安卓应用安装包的权限。");
        }
    }

    private void requireAuthorities(Authentication authentication, String... requiredAuthorities) {
        for (String requiredAuthority : requiredAuthorities) {
            boolean allowed = authentication.getAuthorities().stream()
                    .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
            if (!allowed) {
                throw new AccessDeniedException("当前账号没有批量导入用户的完整权限。");
            }
        }
    }

    private void requireAnyAuthority(Authentication authentication, String... authorities) {
        boolean allowed = java.util.Arrays.stream(authorities).anyMatch(requiredAuthority ->
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority())));
        if (!allowed) {
            throw new AccessDeniedException("当前账号没有用户批量治理权限。");
        }
    }

    private boolean isServerGeneratedPurpose(FileTransferPurpose purpose) {
        return FileTransferPurpose.USER_IMPORT_TEMPLATE.equals(purpose)
                || FileTransferPurpose.USER_IMPORT_ERROR.equals(purpose)
                || FileTransferPurpose.USER_EXPORT.equals(purpose);
    }

    private boolean isTemporaryBusinessFile(FileTransferPurpose purpose) {
        return FileTransferPurpose.WORK_ORDER_ATTACHMENT.equals(purpose)
                || FileTransferPurpose.USER_IMPORT.equals(purpose)
                || isServerGeneratedPurpose(purpose);
    }

    private void requireSessionWritable(FileTransferUploadSession session) {
        requireSessionOwner(session);
        if (!FileTransferStatus.INITIATED.equals(session.getStatus())) {
            throw new IllegalStateException("上传会话已经结束，请重新选择文件上传。");
        }
        if (session.getExpiresAt().isBefore(UtcTime.now())) {
            throw new IllegalStateException("上传会话已过期，请重新选择文件上传。");
        }
    }

    private void requireExpectedPurpose(FileTransferUploadSession session, FileTransferPurpose expectedPurpose) {
        if (!expectedPurpose.equals(session.getPurpose())) {
            throw new IllegalArgumentException("文件用途不匹配，请重新发起操作。");
        }
    }

    private void requireCompletedAndActive(FileTransferUploadSession session) {
        if (!FileTransferStatus.COMPLETED.equals(session.getStatus())) {
            throw new IllegalStateException("文件尚未就绪、正在处理或已经使用，请刷新后重试。");
        }
        if (session.getExpiresAt().isBefore(UtcTime.now())) {
            throw new IllegalStateException("文件已经过期，请重新发起操作。");
        }
    }

    private void requireSessionOwner(FileTransferUploadSession session) {
        Authentication authentication = requireAuthentication();
        com.pro.ereportbacken.model.pojo.User currentUser = workOrderAccessPolicy.currentUserOrThrow();
        boolean isOwner = Objects.equals(session.getCreatedByMembershipId(), currentUser.getId())
                || (session.getCreatedByMembershipId() == null && session.getCreatedByUsername().equals(authentication.getName()));
        boolean canManageReleaseSession = FileTransferPurpose.APP_RELEASE.equals(session.getPurpose())
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> SystemAuthority.SUPER_ADMINISTRATOR.equals(authority.getAuthority())
                        || PermissionCatalog.APP_UPDATE_STORAGE_SYNC.equals(authority.getAuthority()));
        if (!isOwner && !canManageReleaseSession) {
            throw new AccessDeniedException("不能访问其他用户的上传文件。");
        }
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("请先登录后再上传文件。");
        }
        return authentication;
    }

    private FileTransferUploadSession findSession(UUID uploadId) {
        return uploadSessionRepository.findByTenantIdAndId(workOrderAccessPolicy.currentTenantId(), uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在或已过期。"));
    }

    private FileTransferUploadSession findSessionForUpdate(UUID uploadId) {
        return uploadSessionRepository.findByTenantIdAndIdForUpdate(workOrderAccessPolicy.currentTenantId(), uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在或已过期。"));
    }

    private void validateChunk(int chunkIndex,
                               ParsedContentRange range,
                               FileTransferUploadSession session,
                               byte[] chunkBytes) {
        if (chunkBytes == null || chunkBytes.length == 0) {
            throw new IllegalArgumentException("分片内容不能为空。");
        }
        long expectedStart = (long) chunkIndex * session.getChunkSize();
        if (range.start() != expectedStart) {
            throw new IllegalArgumentException("分片序号和字节范围不匹配。");
        }
        if (range.end() >= session.getTotalSize()) {
            throw new IllegalArgumentException("分片字节范围超出文件大小。");
        }
        long expectedLength = range.end() - range.start() + 1;
        if (expectedLength != chunkBytes.length) {
            throw new IllegalArgumentException("分片大小和 Content-Range 不一致。");
        }
        if (chunkBytes.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("单个分片不能超过8兆字节。");
        }
        if (range.start() > session.getReceivedBytes()) {
            throw new IllegalArgumentException("分片上传断点不连续，请从当前断点继续上传。");
        }
        if (range.start() < session.getReceivedBytes() && range.end() + 1 > session.getReceivedBytes()) {
            throw new IllegalArgumentException("分片范围和当前断点重叠，请刷新上传状态后重试。");
        }
    }

    private ParsedContentRange parseContentRange(String contentRange, long expectedTotalSize) {
        if (!StringUtils.hasText(contentRange)) {
            throw new IllegalArgumentException("缺少 Content-Range 请求头。");
        }
        Matcher matcher = CONTENT_RANGE_PATTERN.matcher(contentRange.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Content-Range 格式不正确。");
        }
        long start = Long.parseLong(matcher.group(1));
        long end = Long.parseLong(matcher.group(2));
        long totalSize = Long.parseLong(matcher.group(3));
        if (totalSize != expectedTotalSize || start < 0 || end < start) {
            throw new IllegalArgumentException("Content-Range 字节范围不正确。");
        }
        return new ParsedContentRange(start, end);
    }

    private WorkOrderImageTypeSniffer.Detection inspectWorkOrderMedia(
            FileTransferUploadSession session,
            Path tempFile) {
        if (!FileTransferPurpose.WORK_ORDER_ATTACHMENT.equals(session.getPurpose())) {
            return new WorkOrderImageTypeSniffer.Detection(false, null);
        }
        WorkOrderImageTypeSniffer.Detection detection = imageTypeSniffer.inspect(
                tempFile,
                session.getOriginalFileName(),
                session.getContentType()
        );
        if (detection.image()) {
            session.setContentType(detection.contentType());
        }
        return detection;
    }

    private void storeCompletedFile(
            FileTransferUploadSession session,
            Path tempFile,
            String actualSha256,
            WorkOrderImageTypeSniffer.Detection mediaDetection) {
        if (FileTransferPurpose.APP_RELEASE.equals(session.getPurpose())) {
            AppReleaseStorageService.StoredAppReleaseFile storedFile = appReleaseStorageService.storeCompletedApk(
                    session.getOriginalFileName(),
                    session.getContentType(),
                    tempFile,
                    actualSha256
            );
            session.setStoredFileName(storedFile.storedFileName());
            session.setContentType(storedFile.contentType());
            return;
        }
        FileStorageService.StoredFile storedFile = mediaDetection.image()
                ? fileStorageService.storeCompletedQuarantineFile(session.getOriginalFileName(), tempFile)
                : fileStorageService.storeCompletedFile(session.getOriginalFileName(), tempFile);
        session.setStoredFileName(storedFile.storedFileName());
    }

    private String cleanFileName(String fileName) {
        String cleaned = StringUtils.cleanPath(fileName == null ? "uploaded-file" : fileName.trim());
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw new IllegalArgumentException("文件名不安全，请重新选择文件。");
        }
        return cleaned;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : DEFAULT_CONTENT_TYPE;
    }

    private String normalizeSha256(String sha256) {
        if (!StringUtils.hasText(sha256)) {
            return null;
        }
        String normalized = sha256.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("SHA-256 格式不正确。");
        }
        return normalized;
    }

    private long normalizeChunkSize(Long requestedChunkSize) {
        if (requestedChunkSize == null || requestedChunkSize <= 0) {
            return DEFAULT_CHUNK_BYTES;
        }
        return Math.max(MIN_CHUNK_BYTES, Math.min(MAX_CHUNK_BYTES, requestedChunkSize));
    }

    private Path tempPath(String tempFileName) {
        Path path = tempStorageLocation.resolve(tempFileName).normalize();
        if (!path.startsWith(tempStorageLocation)) {
            throw new IllegalArgumentException("临时文件路径不安全。");
        }
        return path;
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalStateException("文件读取失败，请重新上传。", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 校验。", ex);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 校验。", exception);
        }
    }

    private void deleteTempFile(FileTransferUploadSession session) {
        if (GENERATED_TEMP_FILE_NAME.equals(session.getTempFileName())) {
            return;
        }
        try {
            Files.deleteIfExists(tempPath(session.getTempFileName()));
        } catch (IOException ex) {
            log.warn("Failed to delete upload temp file {}.", session.getTempFileName(), ex);
        }
    }

    private FileTransferSessionDTO toDTO(FileTransferUploadSession session) {
        ImageRedactionService.ImageRedactionState imageRedactionState = imageRedactionService.state(session);
        return new FileTransferSessionDTO(
                session.getId(),
                session.getPurpose(),
                session.getStatus(),
                session.getOriginalFileName(),
                session.getContentType(),
                session.getTotalSize(),
                session.getChunkSize(),
                session.getReceivedBytes(),
                session.getActualSha256() != null ? session.getActualSha256() : session.getExpectedSha256(),
                imageRedactionState.status() == null ? session.getStoredFileName() : null,
                session.getExpiresAt(),
                imageRedactionState.status(),
                imageRedactionState.message()
        );
    }

    private GeneratedFile toGeneratedFile(FileTransferUploadSession session) {
        return new GeneratedFile(
                session.getId(),
                session.getPurpose(),
                session.getOriginalFileName(),
                session.getContentType(),
                session.getTotalSize(),
                session.getChunkSize(),
                session.getActualSha256(),
                session.getExpiresAt()
        );
    }

    private CompletedFile toCompletedFile(FileTransferUploadSession session) {
        try {
            Resource resource = fileStorageService.loadFileAsResource(session.getStoredFileName());
            return new CompletedFile(
                    session.getId(),
                    session.getPurpose(),
                    session.getOriginalFileName(),
                    session.getStoredFileName(),
                    session.getContentType(),
                    session.getTotalSize(),
                    session.getChunkSize(),
                    session.getActualSha256(),
                    session.getExpiresAt(),
                    resource
            );
        } catch (FileNotFoundException exception) {
            throw new IllegalStateException("文件存储内容不存在，请重新发起操作。", exception);
        }
    }

    private record ParsedContentRange(long start, long end) {
    }

    public record ClaimedFile(
            UUID uploadId,
            FileTransferPurpose purpose,
            String originalFileName,
            String storedFileName,
            String contentType,
            long fileSize,
            String sha256,
            UUID imageRedactionJobId,
            ImageRedactionStatus imageRedactionStatus,
            String imageRedactionMessage
    ) {
    }

    public record GeneratedFile(
            UUID fileId,
            FileTransferPurpose purpose,
            String fileName,
            String contentType,
            long fileSize,
            long chunkSize,
            String sha256,
            LocalDateTime expiresAt
    ) {
    }

    public record CompletedFile(
            UUID fileId,
            FileTransferPurpose purpose,
            String fileName,
            String storedFileName,
            String contentType,
            long fileSize,
            long chunkSize,
            String sha256,
            LocalDateTime expiresAt,
            Resource resource
    ) {
    }

    public record ExpiredFile(String tempFileName, String storedFileName, String additionalStoredFileName) {
    }
}
