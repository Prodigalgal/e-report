package com.pro.ereportbacken.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

@Component
public class FileDownloadResponseFactory {

    public ResponseEntity<?> build(Resource resource,
                                   DownloadDescriptor descriptor,
                                   String rangeHeader,
                                   String method) {
        HttpHeaders headers = headers(descriptor);
        if (HttpMethod.HEAD.matches(method)) {
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(descriptor.fileSize())
                    .contentType(MediaType.parseMediaType(descriptor.contentType()))
                    .build();
        }
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            return partialResponse(resource, descriptor, headers, rangeHeader);
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(descriptor.contentType()))
                .contentLength(descriptor.fileSize())
                .body(resource);
    }

    private ResponseEntity<?> partialResponse(Resource resource,
                                              DownloadDescriptor descriptor,
                                              HttpHeaders headers,
                                              String rangeHeader) {
        try {
            var ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.size() != 1) {
                return unsatisfiableRange(descriptor.fileSize());
            }
            HttpRange range = ranges.getFirst();
            long start = range.getRangeStart(descriptor.fileSize());
            long end = range.getRangeEnd(descriptor.fileSize());
            if (start >= descriptor.fileSize()) {
                return unsatisfiableRange(descriptor.fileSize());
            }
            end = Math.min(end, descriptor.fileSize() - 1);
            if (end < start) {
                return unsatisfiableRange(descriptor.fileSize());
            }
            long requestedCount = end - start + 1;
            long count = Math.min(requestedCount, FileTransferService.DEFAULT_CHUNK_BYTES);
            byte[] bytes = readRange(resource, start, count);
            if (bytes.length == 0) {
                return unsatisfiableRange(descriptor.fileSize());
            }
            long actualEnd = start + bytes.length - 1;
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + actualEnd + "/" + descriptor.fileSize());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(descriptor.contentType()))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IllegalArgumentException | IOException ex) {
            return unsatisfiableRange(descriptor.fileSize());
        }
    }

    private void skipFully(InputStream inputStream, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                if (inputStream.read() == -1) {
                    throw new IOException("下载文件在跳转到分片起点前已结束。");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private byte[] readRange(Resource resource, long start, long bytesToRead) throws IOException {
        byte[] bytes = new byte[Math.toIntExact(bytesToRead)];
        int offset = 0;
        try (InputStream inputStream = resource.getInputStream()) {
            skipFully(inputStream, start);
            while (offset < bytes.length) {
                int read = inputStream.read(bytes, offset, bytes.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
        }
        if (offset == bytes.length) {
            return bytes;
        }
        return Arrays.copyOf(bytes, offset);
    }

    private ResponseEntity<Void> unsatisfiableRange(long fileSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .build();
    }

    private HttpHeaders headers(DownloadDescriptor descriptor) {
        ContentDisposition contentDisposition = ContentDisposition.builder(descriptor.disposition())
                .filename(descriptor.fileName(), StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set("X-File-Range-Download", "supported");
        headers.set("X-File-Download-Chunk-Size", String.valueOf(FileTransferService.DEFAULT_CHUNK_BYTES));
        descriptor.extraHeaders().forEach(headers::set);
        return headers;
    }

    public record DownloadDescriptor(
            String fileName,
            String contentType,
            long fileSize,
            String disposition,
            Map<String, String> extraHeaders
    ) {
    }
}
