package com.pro.ereportbacken.repository;
import java.util.UUID;

import com.pro.ereportbacken.model.pojo.Attachment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    @EntityGraph(attributePaths = {"workOrder", "workOrder.reportedBy"})
    Optional<Attachment> findByTenantIdAndWorkOrder_IdAndStoredFileName(UUID tenantId, UUID workOrderId, String storedFileName);

    @EntityGraph(attributePaths = {"workOrder", "workOrder.reportedBy"})
    Optional<Attachment> findByTenantIdAndWorkOrder_IdAndId(UUID tenantId, UUID workOrderId, UUID attachmentId);

    @Query("""
            select attachment
            from Attachment attachment
            where attachment.tenantId = :tenantId
              and attachment.imageRedactionJobId is null
              and lower(attachment.fileType) like :imageTypePattern
              and lower(attachment.storedFileName) not like :sanitizedPathPattern escape '!'
              and (:afterAttachmentId is null or attachment.id > :afterAttachmentId)
            order by attachment.id asc
            """)
    List<Attachment> findUnprocessedImageAttachments(
            @Param("tenantId") UUID tenantId,
            @Param("imageTypePattern") String imageTypePattern,
            @Param("sanitizedPathPattern") String sanitizedPathPattern,
            @Param("afterAttachmentId") UUID afterAttachmentId,
            Pageable pageable
    );
}

