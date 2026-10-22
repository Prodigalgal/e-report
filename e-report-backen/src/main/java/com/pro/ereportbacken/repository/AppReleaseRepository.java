package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.AppPlatform;
import com.pro.ereportbacken.model.pojo.AppEdition;
import com.pro.ereportbacken.model.pojo.AppRelease;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Optional;

@Repository
public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID>, JpaSpecificationExecutor<AppRelease> {
    java.util.List<AppRelease> findByEdition(AppEdition edition, Sort sort);

    Optional<AppRelease> findFirstByEditionAndPlatformAndChannelAndPublishedTrueOrderByVersionCodeDescPublishedAtDesc(
            AppEdition edition,
            AppPlatform platform,
            String channel
    );

    boolean existsByEditionAndPlatformAndChannelAndVersionCode(AppEdition edition, AppPlatform platform, String channel, Integer versionCode);

    Optional<AppRelease> findByEditionAndPlatformAndChannelAndVersionCode(
            AppEdition edition,
            AppPlatform platform,
            String channel,
            Integer versionCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update AppRelease release set release.published = true, release.publishedAt = :publishedAt "
            + "where release.id = :releaseId and release.published = false")
    int markPublishedAfterStorageSync(@Param("releaseId") UUID releaseId,
                                      @Param("publishedAt") LocalDateTime publishedAt);
}


