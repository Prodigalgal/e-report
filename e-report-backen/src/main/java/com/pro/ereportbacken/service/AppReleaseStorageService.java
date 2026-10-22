package com.pro.ereportbacken.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@Getter
@Log4j2
public class AppReleaseStorageService {

    private static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

    private final Path releaseStorageLocation;
    private final long maxApkSizeBytes;

    public AppReleaseStorageService(@Value("${app.update.release-dir:./app_releases}") String releaseDir,
                                    @Value("${app.update.max-apk-size:300MB}") String maxApkSize) {
        this.releaseStorageLocation = Path.of(releaseDir).toAbsolutePath().normalize();
        this.maxApkSizeBytes = DataSize.parse(maxApkSize).toBytes();
        try {
            Files.createDirectories(this.releaseStorageLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建安卓应用安装包存储目录，请检查服务磁盘权限。", ex);
        }
    }

    public StoredAppReleaseFile storeCompletedApk(String originalFileName,
                                                  String contentType,
                                                  Path sourceFile,
                                                  String sha256) {
        String cleanedFileName = StringUtils.cleanPath(originalFileName == null ? "app-release.apk" : originalFileName);
        if (!cleanedFileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new IllegalArgumentException("只允许上传安卓应用安装包。");
        }
        String storedFileName = UUID.randomUUID() + ".apk";
        Path target = releaseStorageLocation.resolve(storedFileName).normalize();
        if (!target.startsWith(releaseStorageLocation)) {
            throw new IllegalArgumentException("安装包文件名不安全。");
        }

        try {
            Files.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            long fileSize = Files.size(target);
            if (fileSize > maxApkSizeBytes) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException("安装包大小超过系统限制，请压缩或重新构建后上传。");
            }
            String normalizedContentType = normalizeContentType(contentType);
            return new StoredAppReleaseFile(cleanedFileName, storedFileName, fileSize, sha256, normalizedContentType);
        } catch (IOException ex) {
            throw new IllegalStateException("安装包保存失败，请稍后重试。", ex);
        }
    }

    public Path storedFilePath(String storedFileName) throws FileNotFoundException {
        Path filePath = resolveStoredFile(storedFileName);
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("安装包不存在。");
        }
        return filePath;
    }

    public void deleteFile(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return;
        }
        try {
            Path target = releaseStorageLocation.resolve(storedFileName).normalize();
            if (target.startsWith(releaseStorageLocation)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ex) {
            log.warn("Failed to delete app release file {}.", storedFileName, ex);
        }
    }

    private Path resolveStoredFile(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            throw new IllegalArgumentException("安装包存储文件名不能为空。");
        }
        Path source = releaseStorageLocation.resolve(storedFileName).normalize();
        if (!source.startsWith(releaseStorageLocation)) {
            throw new IllegalArgumentException("安装包存储路径不安全。");
        }
        return source;
    }

    private String normalizeContentType(String contentType) {
        if ("application/octet-stream".equalsIgnoreCase(contentType) || !StringUtils.hasText(contentType)) {
            return APK_CONTENT_TYPE;
        }
        return contentType;
    }

    public record StoredAppReleaseFile(
            String originalFileName,
            String storedFileName,
            long fileSize,
            String sha256,
            String contentType
    ) {
    }
}
