package com.pro.ereportbacken.service;

import com.pro.ereportbacken.TestIds;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferUploadSession;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import com.pro.ereportbacken.security.WorkOrderAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileTransferAttachmentLimitTests {

    @TempDir
    Path tempDirectory;

    private FileTransferUploadSessionRepository uploadSessionRepository;
    private WorkOrderAccessPolicy workOrderAccessPolicy;
    private FileTransferService fileTransferService;

    @BeforeEach
    void setUp() {
        uploadSessionRepository = mock(FileTransferUploadSessionRepository.class);
        workOrderAccessPolicy = mock(WorkOrderAccessPolicy.class);
        ImageRedactionService imageRedactionService = mock(ImageRedactionService.class);
        when(imageRedactionService.state(any(FileTransferUploadSession.class)))
                .thenReturn(new ImageRedactionService.ImageRedactionState(
                        null, null, null, null, null, null));
        fileTransferService = new FileTransferService(
                uploadSessionRepository,
                mock(FileStorageService.class),
                mock(AppReleaseStorageService.class),
                workOrderAccessPolicy,
                mock(WorkOrderImageTypeSniffer.class),
                imageRedactionService,
                tempDirectory.toString(),
                "300MB"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("worker", "n/a", List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOneWorkOrderAttachmentWithExactlyOneHundredMegabytes() {
        User user = new User();
        user.setId(TestIds.uuid(300));
        user.setTenantId(TestIds.uuid(301));
        when(workOrderAccessPolicy.currentUserOrThrow()).thenReturn(user);
        when(uploadSessionRepository.save(any(FileTransferUploadSession.class))).thenAnswer(invocation -> {
            FileTransferUploadSession session = invocation.getArgument(0);
            session.setId(TestIds.uuid(302));
            return session;
        });

        var session = fileTransferService.initiate(request(100L * 1024L * 1024L));

        assertThat(session.totalSize()).isEqualTo(100L * 1024L * 1024L);
    }

    @Test
    void rejectsOneWorkOrderAttachmentLargerThanOneHundredMegabytes() {
        assertThatThrownBy(() -> fileTransferService.initiate(request(100L * 1024L * 1024L + 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单个附件不能超过100兆字节，请压缩后重新上传。");
        verifyNoInteractions(uploadSessionRepository, workOrderAccessPolicy);
    }

    private FileTransferInitRequestDTO request(long totalSize) {
        return new FileTransferInitRequestDTO(
                FileTransferPurpose.WORK_ORDER_ATTACHMENT,
                "现场照片.jpg",
                "image/jpeg",
                totalSize,
                null,
                FileTransferService.DEFAULT_CHUNK_BYTES
        );
    }
}
