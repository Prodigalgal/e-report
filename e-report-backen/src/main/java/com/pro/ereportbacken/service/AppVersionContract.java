package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppEdition;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppVersionContract {

    private static final Pattern SEMVER = Pattern.compile(
            "^([1-9][0-9]*)\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})$");

    private AppVersionContract() {
    }

    public static Version requireValid(String versionName,
                                       Integer requestedVersionCode,
                                       String requestedRuntimeVersion) {
        return requireValid(AppEdition.NATIVE, versionName, requestedVersionCode, requestedRuntimeVersion);
    }

    public static Version requireValid(AppEdition edition, String versionName,
                                       Integer requestedVersionCode,
                                       String requestedRuntimeVersion) {
        if (!StringUtils.hasText(versionName)) {
            throw new IllegalArgumentException("版本号不能为空，请填写 x.y.z 格式的版本号。");
        }
        String normalizedVersionName = versionName.trim();
        Matcher matcher = SEMVER.matcher(normalizedVersionName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("版本号必须使用 x.y.z 格式，且主版本号从 1 开始。");
        }
        int major = parsePart(matcher.group(1), "主版本号");
        int minor = parsePart(matcher.group(2), "次版本号");
        int patch = parsePart(matcher.group(3), "修订版本号");
        if (minor >= 1_000 || patch >= 1_000) {
            throw new IllegalArgumentException("次版本号和修订版本号必须小于 1000。");
        }
        long calculated = (long) (major - 1) * 1_000_000L + (long) minor * 1_000L + patch;
        if (calculated <= 0 || calculated > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("版本号超出 Android 构建号可用范围。");
        }
        int versionCode = (int) calculated;
        if (requestedVersionCode == null || requestedVersionCode != versionCode) {
            throw new IllegalArgumentException("构建号与版本号不一致，" + normalizedVersionName
                    + " 对应的构建号应为 " + versionCode + "。");
        }
        String runtimeVersion = (edition == AppEdition.LITE ? "android-lite-" : "android-")
                + normalizedVersionName + "-native" + versionCode;
        if (!StringUtils.hasText(requestedRuntimeVersion)
                || !runtimeVersion.equals(requestedRuntimeVersion.trim())) {
            throw new IllegalArgumentException("运行时版本与版本号不一致，应为 " + runtimeVersion + "。");
        }
        return new Version(normalizedVersionName, versionCode, runtimeVersion);
    }

    private static int parsePart(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "超出可用范围。", exception);
        }
    }

    public record Version(String versionName, int versionCode, String runtimeVersion) {
    }
}
