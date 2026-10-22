package com.pro.ereportbacken.model.pojo;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_fault_report_id", columnList = "fault_report_id"),
                @Index(name = "idx_attachments_fault_stored_name", columnList = "fault_report_id,stored_file_name")
        }
)
@Getter
@Setter
public class Attachment extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Column(nullable = false)
    private String fileName; // 原始文件名

    @Column(nullable = false)
    private String storedFileName; // 存储在服务器上的文件名 (可能是UUID或其他唯一名称)

    @Column(nullable = false)
    private String fileType; // 文件类型 (e.g., image/jpeg, video/mp4)

    private long fileSize; // 文件大小 (bytes)

    @Column(nullable = false)
    private String filePath; // 文件存储路径 (相对于某个根目录或完整URL)

    @Column(name = "image_redaction_job_id")
    private java.util.UUID imageRedactionJobId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AttachmentStage attachmentStage = AttachmentStage.REPORT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fault_report_id", nullable = false)
    @JsonBackReference
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    private WorkOrderResponse response;

    // Constructors, getters, setters (Lombok)
}
