package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppReleaseUrlBuilderTests {

    @Test
    void exposesOneStablePublicLinkAndBuildsImmutableR2Url() {
        AppRelease release = release(19, "stable");
        release.setId(com.pro.ereportbacken.TestIds.uuid(21));
        release.setSha256("AABBCCDD");
        AppReleaseUrlBuilder builder = new AppReleaseUrlBuilder(
                "https://e-report.fantong.eu.org:60443/api",
                "https://e-report.fantong.eu.org:60443/download/android",
                "https://download.omnnu.xyz",
                ""
        );

        assertThat(builder.latestApkUrl(AppEdition.NATIVE, "stable"))
                .isEqualTo("https://e-report.fantong.eu.org:60443/download/android");
        assertThat(builder.distributionApkUrl(release))
                .isEqualTo("https://download.omnnu.xyz/android/stable/19/app-release.apk?v=aabbccdd");
        assertThat(builder.releaseApkUrl(release))
                .isEqualTo("https://e-report.fantong.eu.org:60443/api/app-updates/releases/" + release.getId() + "/apk");
    }

    @Test
    void refusesDistributionWhenR2PublicUrlIsNotConfigured() {
        AppRelease release = release(19, "stable");
        release.setId(com.pro.ereportbacken.TestIds.uuid(21));
        AppReleaseUrlBuilder builder = new AppReleaseUrlBuilder(
                "https://e-report.fantong.eu.org:60443/api/",
                "",
                "",
                ""
        );

        assertThat(builder.latestApkUrl(AppEdition.NATIVE, "stable"))
                .isEqualTo("https://e-report.fantong.eu.org:60443/api/app-updates/latest/apk?platform=ANDROID&channel=stable");
        assertThatThrownBy(() -> builder.distributionApkUrl(release))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2");
    }

    @Test
    void appliesEnvironmentObjectPrefixToTestDistributionOnly() {
        AppRelease release = release(1_004_000, "stable");
        release.setId(com.pro.ereportbacken.TestIds.uuid(22));
        release.setSha256("ccdd");
        AppReleaseUrlBuilder builder = new AppReleaseUrlBuilder(
                "https://e-report-test.fantong.eu.org:60443/api",
                "https://e-report-test.fantong.eu.org:60443/download/android",
                "https://download.omnnu.xyz",
                "e-report-test"
        );

        assertThat(builder.distributionApkUrl(release))
                .isEqualTo("https://download.omnnu.xyz/e-report-test/android/stable/1004000/app-release.apk?v=ccdd");
        assertThat(builder.releaseApkUrl(release))
                .isEqualTo("https://e-report-test.fantong.eu.org:60443/api/app-updates/releases/" + release.getId() + "/apk");
    }

    private AppRelease release(int versionCode, String channel) {
        AppRelease release = new AppRelease();
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel(channel);
        release.setVersionCode(versionCode);
        return release;
    }

    @Test
    void liteDownloadsNeverUseNativeStableAliasOrObject() {
        AppRelease lite = release(5008000, "stable");
        lite.setEdition(AppEdition.LITE);
        AppReleaseUrlBuilder builder = new AppReleaseUrlBuilder(
                "https://test.example/api", "https://test.example/download/android",
                "https://cdn.example", "test");

        assertThat(builder.latestApkUrl(AppEdition.LITE, "stable"))
                .contains("edition=LITE").doesNotEndWith("/download/android");
        assertThat(builder.distributionApkUrl(lite))
                .isEqualTo("https://cdn.example/test/android/lite/stable/5008000/app-release.apk");
    }
    @Test
    void betaDownloadKeepsItsChannelInsteadOfUsingStableAlias() {
        AppReleaseUrlBuilder builder = new AppReleaseUrlBuilder(
                "https://test.example/api", "https://test.example/download/android", "", "");
        assertThat(builder.latestApkUrl(AppEdition.NATIVE, "beta"))
                .isEqualTo("https://test.example/api/app-updates/latest/apk?platform=ANDROID&channel=beta");
        assertThat(builder.latestApkUrl(AppEdition.LITE, "beta"))
                .contains("edition=LITE").endsWith("channel=beta");
    }
}
