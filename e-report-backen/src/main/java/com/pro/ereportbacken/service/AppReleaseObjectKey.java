package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AppReleaseObjectKey {
    private static final Pattern APK = Pattern.compile(
            "android/(lite/)?([a-z0-9][a-z0-9._-]{0,49})/([1-9][0-9]*)/app-release\\.apk");

    private AppReleaseObjectKey() {
    }

    public static String relative(AppRelease release) {
        String editionPrefix = release.getEdition() == AppEdition.LITE ? "lite/" : "";
        return release.getPlatform().name().toLowerCase(Locale.ROOT) + "/" + editionPrefix
                + release.getChannel() + "/" + release.getVersionCode() + "/app-release.apk";
    }

    public static Optional<Identity> parse(String relativeKey) {
        if (relativeKey == null) return Optional.empty();
        var matcher = APK.matcher(relativeKey);
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(new Identity(matcher.group(1) == null ? AppEdition.NATIVE : AppEdition.LITE,
                    matcher.group(2), Integer.parseInt(matcher.group(3))));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public record Identity(AppEdition edition, String channel, int versionCode) {
    }
}
