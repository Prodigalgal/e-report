package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.ImageRedactionJob;
import com.pro.ereportbacken.model.pojo.ImageRedactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageRedactionJobRepository extends JpaRepository<ImageRedactionJob, UUID> {

    Optional<ImageRedactionJob> findByTenantIdAndUploadSessionId(UUID tenantId, UUID uploadSessionId);

    boolean existsByTenantIdAndUploadSessionIdAndStatus(
            UUID tenantId,
            UUID uploadSessionId,
            ImageRedactionStatus status
    );

    List<ImageRedactionJob> findByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId,
            ImageRedactionStatus status,
            Pageable pageable
    );
}
