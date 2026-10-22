package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppVersionContractTests {

    @Test
    void validatesUnifiedSemanticAndroidVersionContract() {
        AppVersionContract.Version version = AppVersionContract.requireValid(
                "2.4.0", 1_004_000, "android-2.4.0-native1004000");

        assertThat(version.versionName()).isEqualTo("2.4.0");
        assertThat(version.versionCode()).isEqualTo(1_004_000);
        assertThat(version.runtimeVersion()).isEqualTo("android-2.4.0-native1004000");
    }

    @Test
    void rejectsInconsistentOrNonCanonicalVersions() {
        assertThatThrownBy(() -> AppVersionContract.requireValid(
                "2.4.0", 2_004_000, "android-2.4.0-native2004000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("构建号与版本号不一致");
        assertThatThrownBy(() -> AppVersionContract.requireValid(
                "2.04.0", 1_004_000, "android-2.04.0-native1004000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x.y.z");
    }
}
