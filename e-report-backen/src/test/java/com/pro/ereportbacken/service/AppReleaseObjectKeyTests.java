package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppRelease;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppReleaseObjectKeyTests {
    @Test
    void retainsNativeObjectsAndSeparatesLiteAtTheSameBuildNumber() {
        AppRelease release = new AppRelease();
        release.setPlatform(AppPlatform.ANDROID);
        release.setChannel("stable");
        release.setVersionCode(5008000);
        assertThat(AppReleaseObjectKey.relative(release)).isEqualTo("android/stable/5008000/app-release.apk");
        release.setEdition(AppEdition.LITE);
        String key = AppReleaseObjectKey.relative(release);
        assertThat(key).isEqualTo("android/lite/stable/5008000/app-release.apk");
        assertThat(AppReleaseObjectKey.parse(key)).contains(new AppReleaseObjectKey.Identity(AppEdition.LITE, "stable", 5008000));
        assertThat(AppReleaseObjectKey.parse("android/stable/2147483648/app-release.apk")).isEmpty();
        assertThat(AppReleaseObjectKey.parse("android/../stable/5008000/app-release.apk")).isEmpty();
        assertThat(AppReleaseObjectKey.parse("expo/android/5008000/asset.apk")).isEmpty();
    }
}
