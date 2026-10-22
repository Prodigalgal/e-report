package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.FileTransferDownloadDTO;
import com.pro.ereportbacken.model.dto.UserTransferExecuteRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferImportRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferJobDTO;
import com.pro.ereportbacken.model.dto.UserTransferJobRowDTO;
import com.pro.ereportbacken.model.dto.PageResponseDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportRequestDTO;
import com.pro.ereportbacken.model.dto.UserTransferExportPreviewDTO;
import com.pro.ereportbacken.model.pojo.FileTransferPurpose;
import com.pro.ereportbacken.model.pojo.UserTransferValidationStatus;
import com.pro.ereportbacken.model.pojo.UserTransferExecutionStatus;
import com.pro.ereportbacken.model.pojo.UserTransferJobType;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.service.FileDownloadResponseFactory;
import com.pro.ereportbacken.service.FileTransferService;
import com.pro.ereportbacken.service.UserTransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/user-transfers")
@PreAuthorize("hasAuthority('" + PermissionCatalog.USER_MANAGE_VIEW + "')")
public class AdminUserTransferController {

    private final UserTransferService userTransferService;
    private final FileTransferService fileTransferService;
    private final FileDownloadResponseFactory fileDownloadResponseFactory;

    public AdminUserTransferController(UserTransferService userTransferService,
                                       FileTransferService fileTransferService,
                                       FileDownloadResponseFactory fileDownloadResponseFactory) {
        this.userTransferService = userTransferService;
        this.fileTransferService = fileTransferService;
        this.fileDownloadResponseFactory = fileDownloadResponseFactory;
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD + "')")
    public ResponseEntity<FileTransferDownloadDTO> createTemplate() {
        FileTransferService.GeneratedFile template = userTransferService.createTemplate();
        return ResponseEntity.status(HttpStatus.CREATED).body(toDownloadDTO(
                template, "/admin/user-transfers/templates/" + template.fileId()));
    }

    @RequestMapping(value = "/templates/{fileId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_IMPORT_TEMPLATE_DOWNLOAD + "')")
    public ResponseEntity<?> downloadTemplate(
            @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        return download(fileId, FileTransferPurpose.USER_IMPORT_TEMPLATE, rangeHeader, request);
    }

    @PostMapping("/imports")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_IMPORT_PREVIEW + "')")
    public ResponseEntity<UserTransferJobDTO> createImport(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UserTransferImportRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(userTransferService.createImport(request.uploadId(), idempotencyKey));
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EXPORT_CREATE + "')")
    public ResponseEntity<UserTransferJobDTO> createExport(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody UserTransferExportRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(userTransferService.createExport(request, idempotencyKey));
    }

    @PostMapping("/exports/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EXPORT_CREATE + "')")
    public UserTransferExportPreviewDTO previewExport(
            @RequestBody UserTransferExportRequestDTO request) {
        return userTransferService.previewExport(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_IMPORT_TASK_VIEW + "','"
            + PermissionCatalog.USER_IMPORT_PREVIEW + "','"
            + PermissionCatalog.USER_EXPORT_TASK_VIEW + "','"
            + PermissionCatalog.USER_EXPORT_CREATE + "')")
    public PageResponseDTO<UserTransferJobDTO> findAll(
            @RequestParam(required = false) UserTransferJobType jobType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponseDTO.from(userTransferService.findAll(jobType, pageable));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_IMPORT_TASK_VIEW + "','"
            + PermissionCatalog.USER_IMPORT_PREVIEW + "','"
            + PermissionCatalog.USER_EXPORT_TASK_VIEW + "','"
            + PermissionCatalog.USER_EXPORT_CREATE + "')")
    public UserTransferJobDTO find(@PathVariable UUID jobId) {
        return userTransferService.find(jobId);
    }

    @GetMapping("/{jobId}/rows")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_IMPORT_TASK_VIEW + "','"
            + PermissionCatalog.USER_IMPORT_PREVIEW + "')")
    public PageResponseDTO<UserTransferJobRowDTO> rows(
            @PathVariable UUID jobId,
            @RequestParam(required = false) UserTransferValidationStatus validationStatus,
            @RequestParam(required = false) UserTransferExecutionStatus executionStatus,
        @PageableDefault(size = 50, sort = {"subjectKeyHash", "sheetName", "rowNumber"}) Pageable pageable) {
        return PageResponseDTO.from(
                userTransferService.rows(jobId, validationStatus, executionStatus, pageable));
    }

    @PostMapping("/{jobId}/execute")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_IMPORT_EXECUTE + "')")
    public ResponseEntity<UserTransferJobDTO> execute(
            @PathVariable UUID jobId,
            @Valid @RequestBody UserTransferExecuteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(userTransferService.execute(jobId, request.previewToken()));
    }

    @PostMapping("/{jobId}/cancel")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.USER_IMPORT_CANCEL + "','"
            + PermissionCatalog.USER_EXPORT_CANCEL + "')")
    public UserTransferJobDTO cancel(@PathVariable UUID jobId) {
        return userTransferService.cancel(jobId);
    }

    @RequestMapping(value = "/{jobId}/result", method = {RequestMethod.GET, RequestMethod.HEAD})
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_EXPORT_DOWNLOAD + "')")
    public ResponseEntity<?> downloadResult(
            @PathVariable UUID jobId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        FileTransferService.CompletedFile file = userTransferService.loadResult(jobId);
        return fileDownloadResponseFactory.build(
                file.resource(),
                new FileDownloadResponseFactory.DownloadDescriptor(
                        file.fileName(), file.contentType(), file.fileSize(), "attachment",
                        Map.of(
                                HttpHeaders.CACHE_CONTROL, "no-store, max-age=0",
                                HttpHeaders.PRAGMA, "no-cache",
                                "X-File-Sha256", file.sha256()
                        )),
                rangeHeader,
                request.getMethod());
    }

    @RequestMapping(value = "/{jobId}/error-result", method = {RequestMethod.GET, RequestMethod.HEAD})
    @PreAuthorize("hasAuthority('" + PermissionCatalog.USER_IMPORT_ERROR_REPORT_DOWNLOAD + "')")
    public ResponseEntity<?> downloadErrorResult(
            @PathVariable UUID jobId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request) {
        FileTransferService.CompletedFile file = userTransferService.loadErrorResult(jobId);
        return fileDownloadResponseFactory.build(
                file.resource(),
                new FileDownloadResponseFactory.DownloadDescriptor(
                        file.fileName(), file.contentType(), file.fileSize(), "attachment",
                        Map.of(
                                HttpHeaders.CACHE_CONTROL, "no-store, max-age=0",
                                HttpHeaders.PRAGMA, "no-cache",
                                "X-File-Sha256", file.sha256()
                        )),
                rangeHeader,
                request.getMethod());
    }

    private FileTransferDownloadDTO toDownloadDTO(FileTransferService.GeneratedFile file, String downloadUrl) {
        return new FileTransferDownloadDTO(
                file.fileId(), file.fileName(), file.contentType(), file.fileSize(),
                file.chunkSize(), file.sha256(), downloadUrl, file.expiresAt());
    }

    private ResponseEntity<?> download(UUID fileId,
                                       FileTransferPurpose expectedPurpose,
                                       String rangeHeader,
                                       HttpServletRequest request) {
        FileTransferService.CompletedFile file = fileTransferService.loadCompletedFile(fileId, expectedPurpose);
        return fileDownloadResponseFactory.build(
                file.resource(),
                new FileDownloadResponseFactory.DownloadDescriptor(
                        file.fileName(), file.contentType(), file.fileSize(), "attachment",
                        Map.of(
                                HttpHeaders.CACHE_CONTROL, "no-store, max-age=0",
                                HttpHeaders.PRAGMA, "no-cache",
                                "X-File-Sha256", file.sha256()
                        )),
                rangeHeader,
                request.getMethod());
    }
}
