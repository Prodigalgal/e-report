package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.FileTransferStatus;
import com.pro.ereportbacken.repository.FileTransferUploadSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileTransferRetentionSchedulerTests {

    @Test
    void includesImportErrorWorkbookInTemporaryFileRetention() {
        FileTransferUploadSessionRepository repository = mock(FileTransferUploadSessionRepository.class);
        FileTransferService fileTransferService = mock(FileTransferService.class);
        when(repository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                eq(FileTransferStatus.INITIATED), any(LocalDateTime.class))).thenReturn(List.of());
        when(repository.findTop100ByStatusAndPurposeInAndExpiresAtBeforeOrderByExpiresAtAsc(
                any(FileTransferStatus.class), any(), any(LocalDateTime.class))).thenReturn(List.of());

        new FileTransferRetentionScheduler(repository, fileTransferService).cleanupExpiredFiles();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FileTransferPurpose>> purposes = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).findTop100ByStatusAndPurposeInAndExpiresAtBeforeOrderByExpiresAtAsc(
                any(FileTransferStatus.class), purposes.capture(), any(LocalDateTime.class));
        assertThat(purposes.getAllValues()).allSatisfy(value ->
                assertThat(value).contains(FileTransferPurpose.USER_IMPORT_ERROR));
    }
}
