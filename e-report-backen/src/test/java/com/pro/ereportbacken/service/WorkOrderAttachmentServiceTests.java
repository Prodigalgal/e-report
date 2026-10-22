package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.Attachment;
import com.pro.ereportbacken.model.pojo.AttachmentStage;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.WorkOrder;
import com.pro.ereportbacken.repository.AttachmentRepository;
import com.pro.ereportbacken.repository.WorkOrderRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkOrderAttachmentServiceTests {

    private FileTransferService fileTransferService;
    private AttachmentRepository attachmentRepository;
    private FileStorageService fileStorageService;
    private WorkOrderAccessPolicy workOrderAccessPolicy;
    private ImageRedactionService imageRedactionService;
    private WorkOrderAttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        fileTransferService = mock(FileTransferService.class);
        attachmentRepository = mock(AttachmentRepository.class);
        fileStorageService = mock(FileStorageService.class);
        workOrderAccessPolicy = mock(WorkOrderAccessPolicy.class);
        imageRedactionService = mock(ImageRedactionService.class);
        attachmentService = new WorkOrderAttachmentService(
                mock(WorkOrderRepository.class),
                attachmentRepository,
                fileStorageService,
                fileTransferService,
                workOrderAccessPolicy,
                imageRedactionService
        );
    }

    @Test
    void loadsAttachmentByUuidWithoutUsingTheStoragePathAsAUrlIdentifier() throws Exception {
        UUID tenantId = com.pro.ereportbacken.TestIds.uuid(301);
        UUID workOrderId = com.pro.ereportbacken.TestIds.uuid(302);
        UUID attachmentId = com.pro.ereportbacken.TestIds.uuid(303);
        WorkOrder workOrder = new WorkOrder();
        Attachment attachment = new Attachment();
        attachment.setFileName("现场照片-已脱敏.jpg");
        attachment.setStoredFileName("_sanitized/" + attachmentId + ".jpg");
        attachment.setFileType("image/jpeg");
        attachment.setWorkOrder(workOrder);
        Resource resource = mock(Resource.class);

        when(workOrderAccessPolicy.currentTenantId()).thenReturn(tenantId);
        when(attachmentRepository.findByTenantIdAndWorkOrder_IdAndId(tenantId, workOrderId, attachmentId))
                .thenReturn(Optional.of(attachment));
        when(imageRedactionService.state((UUID) null)).thenReturn(new ImageRedactionService.ImageRedactionState(
                null, null, null, null, null, null));
        when(fileStorageService.loadFileAsResource(attachment.getStoredFileName())).thenReturn(resource);

        WorkOrderAttachmentService.AttachmentResource loaded =
                attachmentService.loadAttachmentResource(workOrderId, attachmentId);

        assertThat(loaded.resource()).isSameAs(resource);
        assertThat(loaded.originalFileName()).isEqualTo("现场照片-已脱敏.jpg");
        verify(attachmentRepository).findByTenantIdAndWorkOrder_IdAndId(tenantId, workOrderId, attachmentId);
        verify(fileStorageService).loadFileAsResource(attachment.getStoredFileName());
    }

    @Test
    void doesNotExposeAnUnlinkedHistoricalImageWhileReconciliationIsPending() throws Exception {
        UUID tenantId = com.pro.ereportbacken.TestIds.uuid(304);
        UUID workOrderId = com.pro.ereportbacken.TestIds.uuid(305);
        UUID attachmentId = com.pro.ereportbacken.TestIds.uuid(306);
        WorkOrder workOrder = new WorkOrder();
        Attachment attachment = new Attachment();
        attachment.setId(attachmentId);
        attachment.setTenantId(tenantId);
        attachment.setStoredFileName("sample.jpg");
        attachment.setFileType("image/jpeg");
        attachment.setWorkOrder(workOrder);

        when(workOrderAccessPolicy.currentTenantId()).thenReturn(tenantId);
        when(attachmentRepository.findByTenantIdAndWorkOrder_IdAndId(tenantId, workOrderId, attachmentId))
                .thenReturn(Optional.of(attachment));
        when(imageRedactionService.state((UUID) null)).thenReturn(new ImageRedactionService.ImageRedactionState(
                null, null, null, null, null, null));

        assertThatThrownBy(() -> attachmentService.loadAttachmentResource(workOrderId, attachmentId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_EARLY);
                            assertThat(exception.getReason()).contains("安全处理队列");
                        });
        verify(fileStorageService, org.mockito.Mockito.never()).loadFileAsResource("sample.jpg");
    }

    @Test
    void attachesDistinctTransferredFilesWithTheRequestedStage() {
        UUID uploadId = com.pro.ereportbacken.TestIds.uuid(1);
        FileTransferService.ClaimedFile claimedFile = claimedFile(uploadId, 1024L);
        when(fileTransferService.claimCompletedFile(uploadId, FileTransferPurpose.WORK_ORDER_ATTACHMENT))
                .thenReturn(claimedFile);
        WorkOrder workOrder = new WorkOrder();

        attachmentService.attachTransferredFiles(
                workOrder,
                List.of(uploadId, uploadId),
                AttachmentStage.RESPONSE,
                true
        );

        assertThat(workOrder.getAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getFileName()).isEqualTo("photo.jpg");
            assertThat(attachment.getStoredFileName()).isEqualTo("stored-photo.jpg");
            assertThat(attachment.getAttachmentStage()).isEqualTo(AttachmentStage.RESPONSE);
            assertThat(attachment.getWorkOrder()).isSameAs(workOrder);
        });
        verify(fileTransferService).claimCompletedFile(uploadId, FileTransferPurpose.WORK_ORDER_ATTACHMENT);
    }

    @Test
    void responseAttachmentsAreRequired() {
        WorkOrder workOrder = new WorkOrder();

        assertThatThrownBy(() -> attachmentService.attachTransferredFiles(
                workOrder,
                List.of(),
                AttachmentStage.RESPONSE,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请至少上传一张回单照片。");
    }

    @Test
    void acceptsTenAttachmentsWithExactlyOneHundredMegabytes() {
        List<UUID> uploadIds = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> com.pro.ereportbacken.TestIds.uuid(100 + index))
                .toList();
        uploadIds.forEach(uploadId -> when(fileTransferService.claimCompletedFile(
                uploadId, FileTransferPurpose.WORK_ORDER_ATTACHMENT))
                .thenReturn(claimedFile(uploadId, 10L * 1024L * 1024L)));
        WorkOrder workOrder = new WorkOrder();

        attachmentService.attachTransferredFiles(
                workOrder,
                uploadIds,
                AttachmentStage.REPORT,
                true
        );

        assertThat(workOrder.getAttachments()).hasSize(10);
    }

    @Test
    void rejectsAttachmentSetsLargerThanOneHundredMegabytesBeforeMutatingAggregate() {
        UUID uploadId = com.pro.ereportbacken.TestIds.uuid(2);
        FileTransferService.ClaimedFile claimedFile = claimedFile(uploadId, 100L * 1024L * 1024L + 1L);
        when(fileTransferService.claimCompletedFile(uploadId, FileTransferPurpose.WORK_ORDER_ATTACHMENT))
                .thenReturn(claimedFile);
        WorkOrder workOrder = new WorkOrder();

        assertThatThrownBy(() -> attachmentService.attachTransferredFiles(
                workOrder,
                List.of(uploadId),
                AttachmentStage.REPORT,
                false
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                            assertThat(exception.getReason())
                                    .contains("不能超过100兆字节")
                                    .contains("当前合计100.1兆字节");
                        });
        assertThat(workOrder.getAttachments()).isEmpty();
    }

    @Test
    void rejectsTheEleventhDistinctAttachmentBeforeClaimingFiles() {
        List<UUID> uploadIds = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> com.pro.ereportbacken.TestIds.uuid(200 + index))
                .toList();

        assertThatThrownBy(() -> attachmentService.attachTransferredFiles(
                new WorkOrder(),
                uploadIds,
                AttachmentStage.RESPONSE,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多上传10个")
                .hasMessageContaining("当前共11个");
        verifyNoInteractions(fileTransferService);
    }

    private FileTransferService.ClaimedFile claimedFile(UUID uploadId, long fileSize) {
        return new FileTransferService.ClaimedFile(
                uploadId,
                FileTransferPurpose.WORK_ORDER_ATTACHMENT,
                "photo.jpg",
                "stored-photo.jpg",
                "image/jpeg",
                fileSize,
                "sha256",
                null,
                null,
                null
        );
    }
}
