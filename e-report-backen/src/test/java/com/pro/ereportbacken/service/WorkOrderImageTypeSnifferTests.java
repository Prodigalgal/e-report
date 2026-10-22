package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderImageTypeSnifferTests {

    @TempDir
    Path temporaryDirectory;

    private final WorkOrderImageTypeSniffer sniffer = new WorkOrderImageTypeSniffer();

    @Test
    void detectsSupportedImageTypesFromMagicBytes() throws Exception {
        assertDetected(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00}, "image/jpeg");
        assertDetected(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}, "image/png");
        assertDetected("RIFF1234WEBPpayload".getBytes(java.nio.charset.StandardCharsets.US_ASCII), "image/webp");
        assertDetected(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}, "image/heic");
    }

    @Test
    void rejectsDeclaredImageWithUnknownOrForgedContent() throws Exception {
        Path file = temporaryDirectory.resolve("forged.jpg");
        Files.writeString(file, "not an image");

        assertThatThrownBy(() -> sniffer.inspect(file, "forged.jpg", "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("图片内容无法识别");
    }

    @Test
    void rejectsImageExtensionEvenWhenClientUsesGenericContentType() throws Exception {
        Path file = temporaryDirectory.resolve("forged.png");
        Files.writeString(file, "not an image");

        assertThatThrownBy(() -> sniffer.inspect(file, "forged.png", "application/octet-stream"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("图片内容无法识别");
    }

    @Test
    void leavesNonImageAttachmentOutsideImagePipeline() throws Exception {
        Path file = temporaryDirectory.resolve("video.mp4");
        Files.write(file, new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        WorkOrderImageTypeSniffer.Detection detection = sniffer.inspect(file, "video.mp4", "video/mp4");

        assertThat(detection.image()).isFalse();
        assertThat(detection.contentType()).isNull();
    }

    private void assertDetected(byte[] bytes, String expectedContentType) throws Exception {
        Path file = Files.createTempFile(temporaryDirectory, "image-", ".bin");
        Files.write(file, bytes);

        WorkOrderImageTypeSniffer.Detection detection = sniffer.inspect(file, "upload.bin", "application/octet-stream");

        assertThat(detection.image()).isTrue();
        assertThat(detection.contentType()).isEqualTo(expectedContentType);
    }
}
