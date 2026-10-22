package com.pro.ereportbacken.controller;

import java.util.UUID;

import com.pro.ereportbacken.model.dto.FileTransferChunkResponseDTO;
import com.pro.ereportbacken.model.dto.FileTransferInitRequestDTO;
import com.pro.ereportbacken.model.dto.FileTransferSessionDTO;
import com.pro.ereportbacken.service.FileTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file-transfers/uploads")
public class FileTransferController {

    private final FileTransferService fileTransferService;

    public FileTransferController(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    @PostMapping
    public ResponseEntity<FileTransferSessionDTO> initiate(@Valid @RequestBody FileTransferInitRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileTransferService.initiate(request));
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<FileTransferSessionDTO> getSession(@PathVariable UUID uploadId) {
        return ResponseEntity.ok(fileTransferService.getSession(uploadId));
    }

    @PutMapping(value = "/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<FileTransferChunkResponseDTO> uploadChunk(
            @PathVariable UUID uploadId,
            @PathVariable int chunkIndex,
            @RequestHeader(HttpHeaders.CONTENT_RANGE) String contentRange,
            @RequestBody byte[] chunkBytes) {
        return ResponseEntity.ok(fileTransferService.uploadChunk(uploadId, chunkIndex, contentRange, chunkBytes));
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<FileTransferSessionDTO> complete(@PathVariable UUID uploadId) {
        return ResponseEntity.ok(fileTransferService.complete(uploadId));
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> abort(@PathVariable UUID uploadId) {
        fileTransferService.abort(uploadId);
        return ResponseEntity.noContent().build();
    }
}
