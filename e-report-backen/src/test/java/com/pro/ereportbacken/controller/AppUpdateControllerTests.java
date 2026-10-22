package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.service.AppReleaseUrlBuilder;
import com.pro.ereportbacken.service.AppUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppUpdateControllerTests {

    @Test
    void updateCheckReturnsCanonicalStableDownloadUrlInsteadOfDistributionUrl() {
        AppUpdateService appUpdateService = mock(AppUpdateService.class);
        AppReleaseUrlBuilder appReleaseUrlBuilder = mock(AppReleaseUrlBuilder.class);
        AppRelease release = release();
        String stableDownloadUrl = "https://e-report.fantong.eu.org:60443/download/android";
        when(appUpdateService.normalizeChannel("stable")).thenReturn("stable");
        when(appUpdateService.findLatestPublished(AppEdition.NATIVE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.of(release));
        when(appReleaseUrlBuilder.latestApkUrl(AppEdition.NATIVE, "stable")).thenReturn(stableDownloadUrl);

        AppUpdateController controller = new AppUpdateController(
                appUpdateService,
                appReleaseUrlBuilder
        );

        var response = controller.latest(AppEdition.NATIVE, AppPlatform.ANDROID, "stable", 17, "android-1.0.17-native18");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().apkUrl()).isEqualTo(stableDownloadUrl);
    }

    @Test
    void latestApkAliasRejectsDownloadWhenR2DistributionIsUnavailable() {
        AppUpdateService appUpdateService = mock(AppUpdateService.class);
        AppReleaseUrlBuilder appReleaseUrlBuilder = mock(AppReleaseUrlBuilder.class);
        AppRelease release = release();
        when(appUpdateService.findLatestPublished(AppEdition.NATIVE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.of(release));
        when(appReleaseUrlBuilder.distributionApkUrl(release))
                .thenThrow(new IllegalStateException("R2 公开下载地址未配置。"));

        AppUpdateController controller = new AppUpdateController(
                appUpdateService,
                appReleaseUrlBuilder
        );

        assertThatThrownBy(() -> controller.downloadLatestApk(AppEdition.NATIVE, AppPlatform.ANDROID, "stable"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void latestApkAliasRedirectsToImmutableR2Object() {
        AppUpdateService appUpdateService = mock(AppUpdateService.class);
        AppReleaseUrlBuilder appReleaseUrlBuilder = mock(AppReleaseUrlBuilder.class);
        AppRelease release = release();
        String cdnUrl = "https://download.example/android/stable/18/app-release.apk";
        when(appUpdateService.findLatestPublished(AppEdition.NATIVE, AppPlatform.ANDROID, "stable")).thenReturn(Optional.of(release));
        when(appReleaseUrlBuilder.distributionApkUrl(release)).thenReturn(cdnUrl);

        AppUpdateController controller = new AppUpdateController(
                appUpdateService,
                appReleaseUrlBuilder
        );

        var response = controller.downloadLatestApk(AppEdition.NATIVE, AppPlatform.ANDROID, "stable");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString(cdnUrl);
        assertThat(response.getHeaders().getFirst("X-App-Version-Code")).isEqualTo("18");
        assertThat(response.getHeaders().getFirst("X-App-Sha256")).isEqualTo(release.getSha256());
    }

    @Test
    void selectedPublishedVersionRedirectsToItsImmutableR2Object() {
        AppUpdateService appUpdateService = mock(AppUpdateService.class);
        AppReleaseUrlBuilder appReleaseUrlBuilder = mock(AppReleaseUrlBuilder.class);
        AppRelease release = release();
        String cdnUrl = "https://download.example/android/stable/18/app-release.apk";
        when(appUpdateService.requirePublished(release.getId())).thenReturn(release);
        when(appReleaseUrlBuilder.distributionApkUrl(release)).thenReturn(cdnUrl);

        var response = new AppUpdateController(appUpdateService, appReleaseUrlBuilder)
                .downloadPublishedRelease(release.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString(cdnUrl);
        assertThat(response.getHeaders().getFirst("X-App-Version-Code")).isEqualTo("18");
    }

    private AppRelease release() {
        AppRelease release = new AppRelease();
        release.setId(com.pro.ereportbacken.TestIds.uuid(20));
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel("stable");
        release.setVersionCode(18);
        release.setVersionName("1.0.17");
        release.setRuntimeVersion("android-1.0.17-native18");
        release.setOriginalFileName("e-report-1.0.17.apk");
        release.setStoredFileName("release.apk");
        release.setFileSize(11L);
        release.setSha256("b".repeat(64));
        release.setContentType("application/vnd.android.package-archive");
        release.setPublished(true);
        return release;
    }
}
