package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.repository.AppReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppReleaseR2ManagementServiceTests {

    @Test
    void reportsSynchronizedMissingAndOrphanObjects() {
        AppReleaseRepository repository = mock(AppReleaseRepository.class);
        AppReleaseStorageService storageService = mock(AppReleaseStorageService.class);
        CloudflareR2AppReleaseService r2Service = mock(CloudflareR2AppReleaseService.class);
        AppReleaseSyncOperationStore syncOperationStore = mock(AppReleaseSyncOperationStore.class);
        AppRelease code25 = release(25, com.pro.ereportbacken.TestIds.uuid(25), 100L);
        AppRelease code24 = release(24, com.pro.ereportbacken.TestIds.uuid(24), 90L);
        when(repository.findByEdition(org.mockito.ArgumentMatchers.eq(AppEdition.NATIVE), any(Sort.class))).thenReturn(List.of(code25, code24));
        when(r2Service.isEnabled()).thenReturn(true);
        when(r2Service.bucketName()).thenReturn("bucket");
        when(r2Service.publicBaseUrl()).thenReturn("https://download.example");
        when(r2Service.objectKey(code25)).thenReturn("android/stable/25/app-release.apk");
        when(r2Service.objectKey(code24)).thenReturn("android/stable/24/app-release.apk");
        when(r2Service.publicUrl(any())).thenAnswer(invocation -> "https://download.example/" + invocation.getArgument(0));
        when(r2Service.relativeObjectKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(r2Service.listAndroidObjects()).thenReturn(List.of(
                new CloudflareR2AppReleaseService.R2Object("android/stable/25/app-release.apk", 100L, "etag25", Instant.now()),
                new CloudflareR2AppReleaseService.R2Object("android/stable/23/app-release.apk", 80L, "etag23", Instant.now())
        ));
        when(syncOperationStore.findAll(any())).thenReturn(java.util.Map.of());

        AppReleaseR2ManagementService managementService = new AppReleaseR2ManagementService(
                repository,
                storageService,
                r2Service,
                Runnable::run,
                syncOperationStore
        );
        var overview = managementService.overview(AppEdition.NATIVE);

        assertThat(overview.synchronizedCount()).isEqualTo(1);
        assertThat(overview.missingCount()).isEqualTo(1);
        assertThat(overview.orphanCount()).isEqualTo(1);
        assertThat(overview.items()).extracting(item -> item.status())
                .containsExactly("SYNCED", "MISSING", "ORPHAN");
        assertThat(overview.items().getFirst().current()).isTrue();
        assertThat(overview.items().getFirst().forceUpdate()).isFalse();
        assertThat(overview.items().getFirst().minSupportedVersionCode()).isZero();
    }

    private AppRelease release(int versionCode, UUID id, long fileSize) {
        AppRelease release = new AppRelease();
        release.setId(id);
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel("stable");
        release.setVersionCode(versionCode);
        release.setVersionName("1.0." + versionCode);
        release.setRuntimeVersion("android-1.0." + versionCode + "-native" + versionCode);
        release.setMinSupportedVersionCode(0);
        release.setForceUpdate(false);
        release.setFileSize(fileSize);
        release.setSha256("a".repeat(64));
        release.setPublished(true);
        return release;
    }
}
