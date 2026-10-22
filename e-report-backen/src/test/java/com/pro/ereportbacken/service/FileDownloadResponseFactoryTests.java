package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileDownloadResponseFactoryTests {

    private final FileDownloadResponseFactory factory = new FileDownloadResponseFactory();

    @TempDir
    Path tempDir;

    @Test
    void headResponseAdvertisesRangeDownload() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "0123456789", StandardCharsets.UTF_8);

        ResponseEntity<?> response = factory.build(
                new UrlResource(file.toUri()),
                descriptor(10),
                null,
                "HEAD"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst("X-File-Range-Download")).isEqualTo("supported");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(10);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void rangeResponseReturnsRequestedBytes() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "0123456789", StandardCharsets.UTF_8);

        ResponseEntity<?> response = factory.build(
                new UrlResource(file.toUri()),
                descriptor(10),
                "bytes=2-5",
                "GET"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(response.getBody()).isInstanceOf(ByteArrayResource.class);
        ByteArrayResource body = (ByteArrayResource) response.getBody();
        assertThat(new String(body.getByteArray(), StandardCharsets.UTF_8)).isEqualTo("2345");
    }

    @Test
    void rangePastEndReturns416() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "0123456789", StandardCharsets.UTF_8);

        ResponseEntity<?> response = factory.build(
                new UrlResource(file.toUri()),
                descriptor(10),
                "bytes=100-120",
                "GET"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
    }

    @Test
    void chineseFilenameUsesUtf8ContentDisposition() throws Exception {
        Path file = tempDir.resolve("template.xlsx");
        Files.write(file, new byte[]{1, 2, 3});
        FileDownloadResponseFactory.DownloadDescriptor descriptor = new FileDownloadResponseFactory.DownloadDescriptor(
                "用户导入模板.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                3, "attachment", Map.of()
        );

        ResponseEntity<?> response = factory.build(new UrlResource(file.toUri()), descriptor, null, "HEAD");
        String header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(header).contains("filename*=UTF-8''");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("用户导入模板.xlsx");
    }

    private FileDownloadResponseFactory.DownloadDescriptor descriptor(long fileSize) {
        return new FileDownloadResponseFactory.DownloadDescriptor(
                "sample.txt",
                "text/plain",
                fileSize,
                "attachment",
                Map.of()
        );
    }
}
