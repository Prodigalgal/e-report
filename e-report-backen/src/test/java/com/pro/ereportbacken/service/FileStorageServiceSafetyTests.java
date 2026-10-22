package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceSafetyTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesNestedSanitizedPathInsideStorageRoot() {
        FileStorageService storageService = new FileStorageService(temporaryDirectory.toString());

        assertThat(storageService.safeResolve("_sanitized/image.jpg"))
                .isEqualTo(temporaryDirectory.resolve("_sanitized/image.jpg").toAbsolutePath().normalize());
    }

    @Test
    void rejectsPathTraversal() {
        FileStorageService storageService = new FileStorageService(temporaryDirectory.toString());

        assertThatThrownBy(() -> storageService.safeResolve("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路径不安全");
    }

    @Test
    void rejectsTraversalEvenWhenNormalizedPathWouldStayInsideStorageRoot() {
        FileStorageService storageService = new FileStorageService(temporaryDirectory.toString());

        assertThatThrownBy(() -> storageService.safeResolve("_quarantine/../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路径不安全");
    }
}
