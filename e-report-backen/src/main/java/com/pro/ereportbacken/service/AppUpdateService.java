package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import java.util.UUID;

import com.pro.ereportbacken.model.dto.AppReleasePublishRequestDTO;
import com.pro.ereportbacken.model.dto.AppReleasePolicyUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.repository.AppReleaseRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@Log4j2
public class AppUpdateService {

    private static final String DEFAULT_CHANNEL = "stable";

    private final AppReleaseRepository appReleaseRepository;
    private final AppReleaseStorageService appReleaseStorageService;
    private final FileTransferService fileTransferService;

    public AppUpdateService(AppReleaseRepository appReleaseRepository,
                            AppReleaseStorageService appReleaseStorageService,
                            FileTransferService fileTransferService) {
        this.appReleaseRepository = appReleaseRepository;
        this.appReleaseStorageService = appReleaseStorageService;
        this.fileTransferService = fileTransferService;
    }

    @Transactional
    public AppRelease publishFromCompletedUpload(AppReleasePublishRequestDTO request) {
        if (request.completedUploadId() == null) {
            throw new IllegalArgumentException("缺少已完成上传的安装包标识。");
        }
        AppPlatform platform = request.platform() == null ? AppPlatform.ANDROID : request.platform();
        AppEdition edition = AppEdition.orNative(request.edition());
        String channel = normalizeChannel(request.channel());
        AppVersionContract.Version version = AppVersionContract.requireValid(
                edition, request.versionName(), request.versionCode(), request.runtimeVersion());
        Integer versionCode = version.versionCode();
        if (appReleaseRepository.existsByEditionAndPlatformAndChannelAndVersionCode(edition, platform, channel, versionCode)) {
            throw new IllegalArgumentException("该安卓应用版本已经发布，请提高构建号或先撤销已发布版本。");
        }
        appReleaseRepository.findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                edition, platform, channel).ifPresent(current -> {
                    if (versionCode <= current.getVersionCode()) {
                        throw new IllegalArgumentException("新版本构建号必须高于当前发布版本 "
                                + current.getVersionName() + "（" + current.getVersionCode() + "）。");
                    }
                });

        FileTransferService.ClaimedFile claimedFile = fileTransferService.claimCompletedFile(
                request.completedUploadId(),
                FileTransferPurpose.APP_RELEASE
        );
        AppRelease release = newBaseRelease(request, platform, channel, version);
        release.setOriginalFileName(claimedFile.originalFileName());
        release.setStoredFileName(claimedFile.storedFileName());
        release.setFileSize(claimedFile.fileSize());
        release.setSha256(claimedFile.sha256());
        release.setContentType(claimedFile.contentType());
        return appReleaseRepository.save(release);
    }

    public Optional<AppRelease> findLatestPublished(AppEdition edition, AppPlatform platform, String channel) {
        return appReleaseRepository.findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
                AppEdition.orNative(edition),
                platform == null ? AppPlatform.ANDROID : platform,
                normalizeChannel(channel)
        );
    }

    public AppRelease requirePublished(UUID releaseId) {
        return appReleaseRepository.findById(releaseId)
                .filter(release -> Boolean.TRUE.equals(release.getPublished()))
                .orElseThrow(() -> new EntityNotFoundException("安装包版本不存在或尚未完成发布。"));
    }

    public Page<AppRelease> list(AppEdition edition, AppPlatform platform, String channel, String searchTerm, Pageable pageable) {
        Specification<AppRelease> specification = buildListSpecification(AppEdition.orNative(edition), platform, channel, searchTerm);
        return appReleaseRepository.findAll(specification, pageable);
    }

    @Transactional
    public AppRelease updatePolicy(UUID releaseId, AppReleasePolicyUpdateRequestDTO request) {
        AppRelease release = appReleaseRepository.findById(releaseId)
                .orElseThrow(() -> new EntityNotFoundException("安装包版本不存在。"));
        Integer minSupportedVersionCode = normalizePolicyMinSupportedVersionCode(
                request.minSupportedVersionCode(),
                release.getVersionCode()
        );
        boolean previousForceUpdate = Boolean.TRUE.equals(release.getForceUpdate());
        int previousMinSupportedVersionCode = release.getMinSupportedVersionCode();
        release.setForceUpdate(Boolean.TRUE.equals(request.forceUpdate()));
        release.setMinSupportedVersionCode(minSupportedVersionCode);
        release.setReleaseNotes(normalizeText(request.releaseNotes(), 2000));
        AppRelease saved = appReleaseRepository.save(release);
        log.info("App release policy updated. releaseId={}, versionCode={}, forceUpdate={}->{}, minSupportedVersionCode={}->{}",
                saved.getId(), saved.getVersionCode(), previousForceUpdate, saved.getForceUpdate(),
                previousMinSupportedVersionCode, saved.getMinSupportedVersionCode());
        return saved;
    }

    public void delete(UUID releaseId) {
        AppRelease release = appReleaseRepository.findById(releaseId)
                .orElseThrow(() -> new EntityNotFoundException("安装包版本不存在。"));
        appReleaseRepository.delete(release);
        appReleaseRepository.flush();
        appReleaseStorageService.deleteFile(release.getStoredFileName());
    }

    public String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return DEFAULT_CHANNEL;
        }
        String normalized = channel.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,49}")) {
            throw new IllegalArgumentException("发布通道只能包含小写字母、数字、点、下划线和短横线，且长度不超过50字符。");
        }
        return normalized;
    }

    private Integer normalizeMinSupportedVersionCode(Integer minSupportedVersionCode) {
        if (minSupportedVersionCode == null || minSupportedVersionCode < 0) {
            return 0;
        }
        return minSupportedVersionCode;
    }

    private Integer normalizePolicyMinSupportedVersionCode(Integer minSupportedVersionCode, Integer versionCode) {
        if (minSupportedVersionCode == null || minSupportedVersionCode < 0) {
            throw new IllegalArgumentException("最低支持构建号不能小于0。");
        }
        if (minSupportedVersionCode > versionCode) {
            throw new IllegalArgumentException("最低支持构建号不能高于当前发布版本的构建号。");
        }
        return minSupportedVersionCode;
    }

    private AppRelease newBaseRelease(AppReleasePublishRequestDTO request,
                                      AppPlatform platform,
                                      String channel,
                                      AppVersionContract.Version version) {
        AppRelease release = new AppRelease();
        release.setEdition(AppEdition.orNative(request.edition()));
        release.setPlatform(platform);
        release.setChannel(channel);
        release.setVersionCode(version.versionCode());
        release.setVersionName(version.versionName());
        release.setRuntimeVersion(version.runtimeVersion());
        release.setMinSupportedVersionCode(normalizePolicyMinSupportedVersionCode(
                normalizeMinSupportedVersionCode(request.minSupportedVersionCode()), version.versionCode()));
        release.setForceUpdate(Boolean.TRUE.equals(request.forceUpdate()));
        release.setReleaseNotes(normalizeText(request.releaseNotes(), 2000));
        release.setPublished(false);
        release.setPublishedAt(UtcTime.now());
        return release;
    }

    private String normalizeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "字符。");
        }
        return normalized;
    }

    private Specification<AppRelease> buildListSpecification(AppEdition edition, AppPlatform platform, String channel, String searchTerm) {
        return (root, query, criteriaBuilder) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("edition"), edition));
            if (platform != null) {
                predicates.add(criteriaBuilder.equal(root.get("platform"), platform));
            }
            if (StringUtils.hasText(channel)) {
                predicates.add(criteriaBuilder.equal(root.get("channel"), normalizeChannel(channel)));
            }
            if (StringUtils.hasText(searchTerm)) {
                String normalizedSearchTerm = searchTerm.trim().toLowerCase(Locale.ROOT);
                String pattern = "%" + normalizedSearchTerm
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                        + "%";
                java.util.List<Predicate> keywordPredicates = new java.util.ArrayList<>();
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "channel", pattern));
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "versionName", pattern));
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "runtimeVersion", pattern));
                keywordPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.coalesce(
                                root.<String>get("releaseNotes").as(String.class),
                                criteriaBuilder.literal("")),
                        "%" + searchTerm.trim()
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_") + "%",
                        '\\'));
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "originalFileName", pattern));
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "storedFileName", pattern));
                keywordPredicates.add(containsIgnoreCase(root, criteriaBuilder, "sha256", pattern));
                try {
                    keywordPredicates.add(criteriaBuilder.equal(root.get("versionCode"), Integer.parseInt(normalizedSearchTerm)));
                } catch (NumberFormatException ignored) {
                    // 非数字关键词不匹配 versionCode。
                }
                predicates.add(criteriaBuilder.or(keywordPredicates.toArray(new Predicate[0])));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate containsIgnoreCase(
            Root<AppRelease> root,
            CriteriaBuilder criteriaBuilder,
            String attributeName,
            String pattern) {
        return criteriaBuilder.like(
                criteriaBuilder.lower(root.<String>get(attributeName)), pattern, '\\');
    }

}
