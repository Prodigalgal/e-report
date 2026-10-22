package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppReleaseStorageServiceTests {

    @TempDir
    Path releaseDirectory;

    @Test
    void resolvesAndRemovesPrivateReleaseArchive() throws Exception {
        AppReleaseStorageService storageService = new AppReleaseStorageService(
                releaseDirectory.toString(),
                "300MB"
        );
        Path source = releaseDirectory.resolve("stored-release.apk");
        byte[] apkBytes = "test-apk-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, apkBytes);

        assertThat(storageService.storedFilePath(source.getFileName().toString())).isEqualTo(source);
        assertThatThrownBy(() -> storageService.storedFilePath("../outside.apk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("安装包存储路径不安全。");

        storageService.deleteFile(source.getFileName().toString());
        assertThat(source).doesNotExist();
    }
}
