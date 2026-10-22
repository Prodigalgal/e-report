package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.AppUpdateCheckResponseDTO;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.service.AppReleaseUrlBuilder;
import com.pro.ereportbacken.service.AppUpdateService;
import com.pro.ereportbacken.service.FileTransferService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/app-updates")
@Log4j2
public class AppUpdateController {

    private final AppUpdateService appUpdateService;
    private final AppReleaseUrlBuilder appReleaseUrlBuilder;

    public AppUpdateController(AppUpdateService appUpdateService,
                               AppReleaseUrlBuilder appReleaseUrlBuilder) {
        this.appUpdateService = appUpdateService;
        this.appReleaseUrlBuilder = appReleaseUrlBuilder;
    }

    @GetMapping("/latest")
    public ResponseEntity<AppUpdateCheckResponseDTO> latest(
            @RequestParam(defaultValue = "NATIVE") AppEdition edition,
            @RequestParam(defaultValue = "ANDROID") AppPlatform platform,
            @RequestParam(defaultValue = "stable") String channel,
            @RequestParam(required = false) Integer currentVersionCode,
            @RequestParam(required = false) String runtimeVersion) {
        String normalizedChannel = appUpdateService.normalizeChannel(channel);
        return appUpdateService.findLatestPublished(edition, platform, normalizedChannel)
                .map(release -> ResponseEntity.ok(toUpdateResponse(release, currentVersionCode, runtimeVersion, normalizedChannel)))
                .orElseGet(() -> ResponseEntity.ok(new AppUpdateCheckResponseDTO(
                        false,
                        false,
                        false,
                        platform,
                        normalizedChannel,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        FileTransferService.DEFAULT_CHUNK_BYTES,
                        null,
                        "当前没有发布版本。",
                        edition
                )));
    }

    @RequestMapping(value = "/latest/apk", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> downloadLatestApk(
            @RequestParam(defaultValue = "NATIVE") AppEdition edition,
            @RequestParam(defaultValue = "ANDROID") AppPlatform platform,
            @RequestParam(defaultValue = "stable") String channel) {
        try {
            AppRelease release = appUpdateService.findLatestPublished(edition, platform, channel)
                    .orElseThrow(() -> new EntityNotFoundException("当前没有可下载的安装包。"));
            String distributionUrl = appReleaseUrlBuilder.distributionApkUrl(release);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(distributionUrl))
                    .header("X-App-Version-Code", String.valueOf(release.getVersionCode()))
                    .header("X-App-Sha256", release.getSha256())
                    .build();
        } catch (EntityNotFoundException ex) {
            log.warn("Latest app release APK not found. platform={}, channel={}, message={}", platform, channel, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前没有可下载的安装包。", ex);
        } catch (IllegalStateException ex) {
            log.error("R2 app release distribution is unavailable. platform={}, channel={}, message={}", platform, channel, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "安装包下载服务暂时不可用。", ex);
        }
    }

    @RequestMapping(value = "/releases/{releaseId}/apk", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> downloadPublishedRelease(@PathVariable UUID releaseId) {
        try {
            AppRelease release = appUpdateService.requirePublished(releaseId);
            return redirectToDistribution(release);
        } catch (EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "安装包版本不存在或尚未发布。", ex);
        } catch (IllegalStateException ex) {
            log.error("R2 app release distribution is unavailable. releaseId={}, message={}", releaseId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "安装包下载服务暂时不可用。", ex);
        }
    }

    private ResponseEntity<Void> redirectToDistribution(AppRelease release) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(appReleaseUrlBuilder.distributionApkUrl(release)))
                .header("X-App-Version-Code", String.valueOf(release.getVersionCode()))
                .header("X-App-Sha256", release.getSha256())
                .build();
    }

    private AppUpdateCheckResponseDTO toUpdateResponse(AppRelease release,
                                                       Integer currentVersionCode,
                                                       String runtimeVersion,
                                                       String normalizedChannel) {
        int current = currentVersionCode == null ? 0 : Math.max(0, currentVersionCode);
        boolean updateAvailable = current == 0 || release.getVersionCode() > current;
        boolean belowMinVersion = current > 0 && release.getMinSupportedVersionCode() != null && current < release.getMinSupportedVersionCode();
        boolean forceUpdate = updateAvailable && (Boolean.TRUE.equals(release.getForceUpdate()) || belowMinVersion);
        boolean nativeUpgradeRequired = updateAvailable && runtimeDiffers(release.getRuntimeVersion(), runtimeVersion);
        String message = updateAvailable ? "发现新版本，请下载并安装。" : "当前已是最新版本。";
        return new AppUpdateCheckResponseDTO(
                updateAvailable,
                forceUpdate,
                nativeUpgradeRequired,
                release.getPlatform(),
                normalizedChannel,
                release.getVersionCode(),
                release.getVersionName(),
                release.getRuntimeVersion(),
                release.getMinSupportedVersionCode(),
                release.getReleaseNotes(),
                appReleaseUrlBuilder.latestApkUrl(release.getEdition(), release.getChannel()),
                release.getFileSize(),
                release.getSha256(),
                true,
                FileTransferService.DEFAULT_CHUNK_BYTES,
                release.getPublishedAt(),
                message,
                release.getEdition()
        );
    }

    private boolean runtimeDiffers(String latestRuntimeVersion, String currentRuntimeVersion) {
        if (currentRuntimeVersion == null || currentRuntimeVersion.isBlank()) {
            return true;
        }
        return !currentRuntimeVersion.trim().equals(latestRuntimeVersion);
    }

}

