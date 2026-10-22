package com.pro.ereportbacken.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class WorkOrderImageTypeSniffer {

    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "heic", "heif");

    public Detection inspect(Path file, String originalFileName, String declaredContentType) {
        byte[] header = readHeader(file);
        String detected = detect(header);
        boolean declaredImage = StringUtils.hasText(declaredContentType)
                && declaredContentType.trim().toLowerCase(Locale.ROOT).startsWith("image/");
        boolean imageFileName = hasSupportedImageExtension(originalFileName);
        if ((declaredImage || imageFileName) && detected == null) {
            throw new IllegalArgumentException("图片内容无法识别或与文件类型不一致，请转换为 JPG、PNG、WebP 或 HEIC 后重新上传。");
        }
        return new Detection(detected != null, detected);
    }

    private boolean hasSupportedImageExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return false;
        }
        String normalized = originalFileName.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('.');
        return separator >= 0
                && separator < normalized.length() - 1
                && SUPPORTED_IMAGE_EXTENSIONS.contains(normalized.substring(separator + 1));
    }

    private byte[] readHeader(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return inputStream.readNBytes(64);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取上传文件的真实类型，请重新上传。", exception);
        }
    }

    private String detect(byte[] header) {
        if (startsWith(header, new int[]{0xFF, 0xD8, 0xFF})) return "image/jpeg";
        if (startsWith(header, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (header.length >= 12 && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP")) {
            return "image/webp";
        }
        if (header.length >= 12 && ascii(header, 4, 4).equals("ftyp")) {
            String brand = ascii(header, 8, 4).toLowerCase(Locale.ROOT);
            if (HEIF_BRANDS.contains(brand)) return "image/heic";
        }
        return null;
    }

    private boolean startsWith(byte[] source, int[] signature) {
        if (source.length < signature.length) return false;
        for (int index = 0; index < signature.length; index += 1) {
            if ((source[index] & 0xFF) != signature[index]) return false;
        }
        return true;
    }

    private String ascii(byte[] source, int offset, int length) {
        if (source.length < offset + length) return "";
        return StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(source, offset, length)).toString();
    }

    public record Detection(boolean image, String contentType) {
    }
}
