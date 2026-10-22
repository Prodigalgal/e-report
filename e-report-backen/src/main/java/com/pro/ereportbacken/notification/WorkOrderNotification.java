package com.pro.ereportbacken.notification;

import java.util.UUID;

import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.model.pojo.WorkOrderAssignment;
import com.pro.ereportbacken.model.pojo.WorkOrderType;

import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderNotification(
        UUID id,
        UUID reportingOrgUnitId,
        UUID responsibleOrgUnitId,
        WorkOrderNotificationKind kind,
        WorkOrderType reportType,
        String reportTypeName,
        String priorityName,
        String slaStageName,
        LocalDateTime slaDueAt,
        LocalDateTime slaBreachedAt,
        String location,
        String gpsCoordinates,
        String reason,
        String phone,
        String contactPerson,
        LocalDateTime reportTime,
        List<AttachmentInfo> attachments,
        UUID assignmentId,
        AssigneeContact assignee,
        boolean assignmentPending
) {
    public static WorkOrderNotification created(WorkOrder workOrder) {
        return from(workOrder, WorkOrderNotificationKind.CREATED, null);
    }

    public static WorkOrderNotification slaBreached(WorkOrder workOrder) {
        return from(workOrder, WorkOrderNotificationKind.SLA_BREACHED, null);
    }

    public static WorkOrderNotification assignmentReceived(WorkOrder workOrder, WorkOrderAssignment assignment) {
        if (assignment == null || (!assignment.isCurrent() && !assignment.isPending())) {
            throw new IllegalArgumentException("派单通知必须关联有效的接收人。");
        }
        return from(workOrder, WorkOrderNotificationKind.ASSIGNMENT_RECEIVED, assignment);
    }

    private static WorkOrderNotification from(WorkOrder workOrder,
                                              WorkOrderNotificationKind kind,
                                              WorkOrderAssignment assignment) {
        WorkOrderType type = workOrder.getReportType() != null
                ? workOrder.getReportType()
                : WorkOrderType.NETWORK_FAULT;
        List<AttachmentInfo> attachmentInfos = workOrder.getAttachments() == null
                ? List.of()
                : workOrder.getAttachments().stream()
                .map(AttachmentInfo::from)
                .toList();

        return new WorkOrderNotification(
                workOrder.getId(),
                workOrder.getReportingOrgId(),
                workOrder.getResponsibleOrgId(),
                kind,
                type,
                type.getDisplayName(),
                workOrder.getPriority() == null ? "普通" : workOrder.getPriority().getDisplayName(),
                workOrder.getSlaStage() == null ? "等待接单" : workOrder.getSlaStage().getDisplayName(),
                workOrder.getSlaDueAt(),
                workOrder.getSlaBreachedAt(),
                text(workOrder.getLocation()),
                text(workOrder.getGpsCoordinates()),
                text(workOrder.getReason()),
                text(workOrder.getPhone()),
                text(workOrder.getContactPerson()),
                workOrder.getReportTime(),
                List.copyOf(attachmentInfos),
                assignment == null ? null : assignment.getId(),
                assignment == null ? null : new AssigneeContact(
                        assignment.getAssignee().getId(),
                        text(assignment.getAssignee().getName()),
                        text(assignment.getAssignee().getEmail()),
                        text(assignment.getAssignee().getPhone()),
                        assignment.getAssignee().isEmailWorkOrderAssigned()),
                assignment != null && assignment.isPending()
        );
    }

    public boolean isSlaBreach() {
        return kind == WorkOrderNotificationKind.SLA_BREACHED;
    }

    public boolean isAssignmentReceived() {
        return kind == WorkOrderNotificationKind.ASSIGNMENT_RECEIVED;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record AssigneeContact(UUID userId, String name, String email, String phone, boolean emailSubscribed) {
    }

    public record AttachmentInfo(
            String fileName,
            String storedFileName,
            String fileType,
            long fileSize
    ) {
        private static AttachmentInfo from(Attachment attachment) {
            return new AttachmentInfo(
                    text(attachment.getFileName()),
                    text(attachment.getStoredFileName()),
                    text(attachment.getFileType()),
                    attachment.getFileSize()
            );
        }
    }
}
