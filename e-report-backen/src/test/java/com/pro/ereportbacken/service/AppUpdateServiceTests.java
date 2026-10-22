package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.dto.AppReleasePolicyUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.AppReleasePublishRequestDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.repository.AppReleaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppUpdateServiceTests {

    private AppReleaseRepository repository;
    private FileTransferService fileTransferService;
    private AppUpdateService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppReleaseRepository.class);
        fileTransferService = mock(FileTransferService.class);
        service = new AppUpdateService(
                repository,
                mock(AppReleaseStorageService.class),
                fileTransferService
        );
        when(repository.save(any(AppRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                AppEdition.NATIVE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.empty());
    }

    @Test
    void updatesOnlyMutableReleasePolicyFields() {
        UUID releaseId = UUID.randomUUID();
        AppRelease release = release(releaseId, 40);
        when(repository.findById(releaseId)).thenReturn(Optional.of(release));

        AppRelease updated = service.updatePolicy(
                releaseId,
                new AppReleasePolicyUpdateRequestDTO(true, 37, "  修复用户管理交互  ")
        );

        assertThat(updated.getForceUpdate()).isTrue();
        assertThat(updated.getMinSupportedVersionCode()).isEqualTo(37);
        assertThat(updated.getReleaseNotes()).isEqualTo("修复用户管理交互");
        assertThat(updated.getVersionCode()).isEqualTo(40);
        assertThat(updated.getSha256()).isEqualTo("a".repeat(64));
        verify(repository).save(release);
    }

    @Test
    void rejectsMinimumSupportedVersionAboveReleaseVersion() {
        UUID releaseId = UUID.randomUUID();
        when(repository.findById(releaseId)).thenReturn(Optional.of(release(releaseId, 40)));

        assertThatThrownBy(() -> service.updatePolicy(
                releaseId,
                new AppReleasePolicyUpdateRequestDTO(false, 41, "说明")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最低支持构建号不能高于当前发布版本的构建号。");
    }

    @Test
    void rejectsMissingRelease() {
        UUID releaseId = UUID.randomUUID();
        when(repository.findById(releaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePolicy(
                releaseId,
                new AppReleasePolicyUpdateRequestDTO(false, 0, "")
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void keepsNewReleaseUnpublishedUntilObjectStorageSynchronizationCompletes() {
        UUID uploadId = UUID.randomUUID();
        when(fileTransferService.claimCompletedFile(uploadId, FileTransferPurpose.APP_RELEASE))
                .thenReturn(new FileTransferService.ClaimedFile(
                        uploadId,
                        FileTransferPurpose.APP_RELEASE,
                        "e-report-2.4.0.apk",
                        "stored.apk",
                        "application/vnd.android.package-archive",
                        1024L,
                        "b".repeat(64),
                        null,
                        null,
                        null
                ));

        AppRelease created = service.publishFromCompletedUpload(new AppReleasePublishRequestDTO(
                AppPlatform.ANDROID,
                "stable",
                1_004_000,
                "2.4.0",
                "android-2.4.0-native1004000",
                47,
                false,
                "系统级发布中心",
                uploadId,
                null
        ));

        assertThat(created.getPublished()).isFalse();
        assertThat(created.getVersionName()).isEqualTo("2.4.0");
        assertThat(created.getVersionCode()).isEqualTo(1_004_000);
        assertThat(created.getRuntimeVersion()).isEqualTo("android-2.4.0-native1004000");
        assertThat(created.getSha256()).isEqualTo("b".repeat(64));
    }

    @Test
    void rejectsVersionThatDoesNotAdvanceCurrentPublishedBuild() {
        AppRelease current = release(UUID.randomUUID(), 1_004_000);
        current.setVersionName("2.4.0");
        when(repository.findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                AppEdition.NATIVE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.publishFromCompletedUpload(new AppReleasePublishRequestDTO(
                AppPlatform.ANDROID,
                "stable",
                1_003_001,
                "2.3.1",
                "android-2.3.1-native1003001",
                47,
                false,
                "回退版本",
                UUID.randomUUID(),
                AppEdition.NATIVE
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须高于当前发布版本");
    }

    private AppRelease release(UUID id, int versionCode) {
        AppRelease release = new AppRelease();
        release.setId(id);
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel("stable");
        release.setVersionCode(versionCode);
        release.setVersionName("1.0." + versionCode);
        release.setRuntimeVersion("android-1.0." + versionCode + "-native" + versionCode);
        release.setMinSupportedVersionCode(37);
        release.setForceUpdate(false);
        release.setReleaseNotes("旧说明");
        release.setSha256("a".repeat(64));
        return release;
    }

    @Test
    void latestReleaseIsScopedToEditionAndMissingEditionDefaultsToNative() {
        AppRelease lite = release(UUID.randomUUID(), 5008000);
        lite.setEdition(AppEdition.LITE);
        when(repository.findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                AppEdition.LITE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.of(lite));

        assertThat(service.findLatestPublished(AppEdition.LITE, AppPlatform.ANDROID, "stable")).contains(lite);
        assertThat(service.findLatestPublished(null, null, "stable")).isEmpty();
        verify(repository).findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                AppEdition.NATIVE, AppPlatform.ANDROID, "stable");
    }
}
