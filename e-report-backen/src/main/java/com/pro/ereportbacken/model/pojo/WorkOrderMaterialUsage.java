package com.pro.ereportbacken.model.pojo;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "work_order_material_usages",
        indexes = {
                @Index(name = "idx_material_usage_fault_report_id", columnList = "fault_report_id"),
                @Index(name = "idx_material_usage_category_spec", columnList = "category,specification")
        }
)
@Getter
@Setter
public class WorkOrderMaterialUsage extends UuidV7Entity {

    @Column(name = "tenant_id", nullable = false)
    private java.util.UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MaterialCategory category;

    @Column(nullable = false, length = 50)
    private String specification;

    @Column(nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fault_report_id", nullable = false)
    @JsonBackReference
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    private WorkOrderResponse response;
}
