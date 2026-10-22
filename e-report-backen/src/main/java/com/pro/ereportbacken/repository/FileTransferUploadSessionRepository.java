package com.pro.ereportbacken.repository;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileTransferUploadSessionRepository extends JpaRepository<FileTransferUploadSession, UUID> {

    List<FileTransferUploadSession> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            FileTransferStatus status,
            LocalDateTime now
    );

    List<FileTransferUploadSession> findTop100ByStatusAndPurposeInAndExpiresAtBeforeOrderByExpiresAtAsc(
            FileTransferStatus status,
            Collection<FileTransferPurpose> purposes,
            LocalDateTime now
    );

    Optional<FileTransferUploadSession> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<FileTransferUploadSession> findFirstByTenantIdAndPurposeAndStoredFileNameOrderByCreatedAtDesc(
            UUID tenantId,
            FileTransferPurpose purpose,
            String storedFileName
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from FileTransferUploadSession session "
            + "where session.tenantId = :tenantId and session.id = :id")
    Optional<FileTransferUploadSession> findByTenantIdAndIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );
}


