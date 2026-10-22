package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.time.UtcTime;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Component
public class ExpoUpdateArchive {
    private static final long MAX_EXPANDED_BYTES = 150L * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private final ExpoUpdateConfiguration configuration;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExpoUpdateArchive(ExpoUpdateConfiguration configuration) { this.configuration = configuration; }

    public Extracted extract(MultipartFile archive) throws IOException {
        configuration.requireEnabled();
        if (archive.isEmpty() || archive.getSize() > 50L * 1024 * 1024) throw new IllegalArgumentException("OTA 归档为空或超过 50MB。");
        Path directory = Files.createTempDirectory("e-report-ota-");
        try {
            Set<String> files = unpack(archive.getInputStream(), directory);
            JsonNode descriptor = mapper.readTree(readSmall(directory.resolve("release.json"), MAX_JSON_BYTES));
            String payload = readSmall(directory.resolve("manifest.json"), MAX_JSON_BYTES);
            String signature = readSmall(directory.resolve("signature.txt"), 1024).trim();
            verifySignature(payload, signature);
            JsonNode manifest = mapper.readTree(payload);
            if (descriptor == null || !descriptor.isObject() || manifest == null || !manifest.isObject()) {
                throw new IllegalArgumentException("OTA 元数据必须为 JSON 对象。");
            }
            ExpoUpdate update = validateDescriptor(descriptor, manifest, payload, signature);
            List<Asset> assets = new ArrayList<>();
            Set<String> required = new HashSet<>(Set.of("release.json", "manifest.json", "signature.txt"));
            if ("UPDATE".equals(update.getKind())) {
                if (!manifest.path("assets").isArray() || manifest.path("assets").size() > 1900) throw new IllegalArgumentException("OTA 资源清单无效。");
                assets.add(validateAsset(manifest.path("launchAsset"), update.getRuntimeVersion(), directory, required));
                for (JsonNode asset : manifest.path("assets")) assets.add(validateAsset(asset, update.getRuntimeVersion(), directory, required));
            }
            if (!files.equals(required)) throw new IllegalArgumentException("OTA 归档与已签名资源清单不一致。");
            return new Extracted(directory, update, List.copyOf(assets));
        } catch (IOException | RuntimeException exception) {
            deleteDirectory(directory);
            throw exception;
        }
    }

    private Set<String> unpack(InputStream input, Path directory) throws IOException {
        Set<String> names = new HashSet<>();
        long totalBytes = 0;
        try (var zip = new ZipInputStream(input)) {
            while (true) {
                var entry = zip.getNextEntry();
                if (entry == null) break;
                String name = entry.getName();
                if (entry.isDirectory() && "assets/".equals(name)) continue;
                if (!(Set.of("release.json", "manifest.json", "signature.txt").contains(name)
                        || name.matches("assets/[a-f0-9]{64}")) || !names.add(name) || names.size() > 2003) {
                    throw new IllegalArgumentException("OTA 归档路径、文件数量或重复条目无效。");
                }
                Path target = directory.resolve(name);
                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    long fileBytes = 0;
                    while ((count = zip.read(buffer)) != -1) {
                        totalBytes += count;
                        fileBytes += count;
                        if (totalBytes > MAX_EXPANDED_BYTES || fileBytes > 64L * 1024 * 1024
                                || (!name.startsWith("assets/") && fileBytes > MAX_JSON_BYTES)) {
                            throw new IllegalArgumentException("OTA 归档解压体积超出限制。");
                        }
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        return names;
    }

    private ExpoUpdate validateDescriptor(JsonNode descriptor, JsonNode manifest, String payload, String signature) {
        if (descriptor.path("schemaVersion").asInt() != 1) throw new IllegalArgumentException("OTA 归档版本无效。");
        String runtime = descriptor.path("runtimeVersion").asText();
        requireRuntime(runtime);
        String kind = descriptor.path("kind").asText();
        if (!Set.of("UPDATE", "ROLLBACK").contains(kind)) throw new IllegalArgumentException("OTA 更新类型无效。");
        JsonNode metadata = "ROLLBACK".equals(kind) ? manifest.path("parameters").path("metadata") : manifest.path("metadata");
        for (String property : List.of("environment", "runtimeVersion", "version", "sourceSha", "id")) {
            if (!descriptor.path(property).isTextual() || !descriptor.path(property).asText().equals(metadata.path(property).asText())) {
                throw new IllegalArgumentException("OTA 签名元数据不一致：" + property);
            }
        }
        if (!configuration.environment().equals(metadata.path("environment").asText())) throw new IllegalArgumentException("不能将 OTA 发布到其他环境。");
        String version = metadata.path("version").asText();
        if (!version.matches("[1-9][0-9]*\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})")) throw new IllegalArgumentException("OTA 业务版本无效。");
        String source = metadata.path("sourceSha").asText();
        if (!source.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("OTA 源码 SHA 无效。");
        String createdAt;
        if ("ROLLBACK".equals(kind)) {
            if (!"rollBackToEmbedded".equals(manifest.path("type").asText())) throw new IllegalArgumentException("OTA 回退指令无效。");
            createdAt = manifest.path("parameters").path("commitTime").asText();
        } else {
            if (!runtime.equals(manifest.path("runtimeVersion").asText()) || !metadata.path("id").asText().equals(manifest.path("id").asText())) {
                throw new IllegalArgumentException("OTA 运行时或更新标识不一致。");
            }
            createdAt = manifest.path("createdAt").asText();
        }
        Instant created;
        try { created = Instant.parse(createdAt); }
        catch (java.time.format.DateTimeParseException exception) { throw new IllegalArgumentException("OTA 创建时间格式无效。", exception); }
        if (created.isAfter(Instant.now().plusSeconds(300))) throw new IllegalArgumentException("OTA 创建时间不能在未来。");
        ExpoUpdate update = new ExpoUpdate();
        update.setId(UUID.fromString(metadata.path("id").asText()));
        update.setEnvironment(configuration.environment());
        update.setRuntimeVersion(runtime); update.setVersionName(version); update.setSourceSha(source); update.setKind(kind);
        update.setPayload(payload); update.setSignature(signature); update.setSha256(sha256(payload.getBytes(StandardCharsets.UTF_8)));
        update.setCreatedAt(LocalDateTime.ofInstant(created, ZoneOffset.UTC)); update.setPublishedAt(UtcTime.now());
        return update;
    }

    public static void requireRuntime(String runtime) {
        if (!runtime.matches("android-[1-9][0-9]*\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})-native[1-9][0-9]{0,9}") || runtime.length() > 100) {
            throw new IllegalArgumentException("OTA 原生运行时无效。");
        }
        String[] parts = runtime.substring("android-".length()).split("-native", 2);
        try { AppVersionContract.requireValid(parts[0], Integer.valueOf(parts[1]), runtime); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("OTA 原生运行时超出范围。", exception); }
    }

    private Asset validateAsset(JsonNode asset, String runtime, Path directory, Set<String> required) throws IOException {
        String hash = asset.path("hash").asText();
        byte[] decoded;
        try { decoded = Base64.getUrlDecoder().decode(hash); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("OTA 资源哈希无效。", exception); }
        if (decoded.length != 32) throw new IllegalArgumentException("OTA 资源哈希必须是 SHA-256。");
        String hex = HexFormat.of().formatHex(decoded);
        Path file = directory.resolve("assets/" + hex);
        if (!Files.isRegularFile(file) || !hex.equals(sha256(Files.newInputStream(file)))) throw new IllegalArgumentException("OTA 资源缺失或校验失败。");
        if (!configuration.assetUrl(runtime, hex).equals(asset.path("url").asText())) throw new IllegalArgumentException("OTA 资源 URL 与目标环境不一致。");
        String type = asset.path("contentType").asText();
        if (!type.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")) throw new IllegalArgumentException("OTA 资源类型无效。");
        if (!asset.path("key").asText().matches("[a-zA-Z0-9_-]{1,128}")
                || !asset.path("fileExtension").asText().matches("\\.[a-zA-Z0-9]{1,16}")) throw new IllegalArgumentException("OTA 资源标识无效。");
        required.add("assets/" + hex);
        return new Asset(file, hex, type);
    }

    private void verifySignature(String payload, String encodedSignature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(configuration.publicKey()); verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(encodedSignature))) throw new IllegalArgumentException("OTA 签名校验失败。");
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("OTA 签名无效，发布已停止。", exception);
        }
    }

    private static String readSmall(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > limit) throw new IllegalArgumentException("OTA 归档元数据缺失或过大。");
        return Files.readString(file, StandardCharsets.UTF_8);
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("SHA-256 不可用。", exception); }
    }
    private static String sha256(InputStream stream) throws IOException {
        try (stream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("SHA-256 不可用。", exception); }
    }
    private static void deleteDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
    public record Asset(Path file, String sha256, String contentType) {}
    public record Extracted(Path directory, ExpoUpdate update, List<Asset> assets) implements AutoCloseable {
        @Override public void close() throws IOException { deleteDirectory(directory); }
    }
}
