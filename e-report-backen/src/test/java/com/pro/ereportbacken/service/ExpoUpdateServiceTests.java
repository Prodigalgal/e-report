package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.repository.ExpoUpdateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExpoUpdateServiceTests {
    @TempDir Path directory;

    @Test
    void failedAssetDistributionDoesNotActivateTheManifestAndRemovesStagingFiles() throws Exception {
        ExpoUpdateConfiguration configuration = mock(ExpoUpdateConfiguration.class);
        ExpoUpdateArchive archive = mock(ExpoUpdateArchive.class);
        ExpoUpdateRepository repository = mock(ExpoUpdateRepository.class);
        ExpoUpdateRegistry registry = mock(ExpoUpdateRegistry.class);
        CloudflareR2AppReleaseService storage = mock(CloudflareR2AppReleaseService.class);
        ExpoUpdate update = new ExpoUpdate();
        update.setId(UUID.randomUUID()); update.setAttemptId(UUID.randomUUID());
        update.setRuntimeVersion("android-6.8.0-native5008000"); update.setStatus("PUBLISHING");
        Path staging = Files.createDirectory(directory.resolve("staging"));
        Path asset = Files.writeString(staging.resolve("bundle"), "signed bundle fixture");
        when(storage.isEnabled()).thenReturn(true);
        when(archive.extract(any())).thenReturn(new ExpoUpdateArchive.Extracted(staging, update,
                List.of(new ExpoUpdateArchive.Asset(asset, "a".repeat(64), "application/javascript"))));
        when(registry.stage(update)).thenReturn(new ExpoUpdateRegistry.Publication(update, true));
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).uploadExpoAsset(any(), any(), any(), any());

        ExpoUpdateService service = new ExpoUpdateService(configuration, archive, repository, registry, storage, new SyncTaskExecutor());
        service.publish(new MockMultipartFile("archive", new byte[]{1}));

        verify(registry).finish(update.getId(), update.getAttemptId(), false);
        verify(registry, never()).finish(update.getId(), update.getAttemptId(), true);
        assertThat(staging).doesNotExist();
    }
}
