package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpoUpdateArchiveTests {
    private static final String RUNTIME = "android-6.8.0-native5008000";
    private static final String API = "https://test.example/api";
    private final ObjectMapper mapper = new ObjectMapper();
    private KeyPair keys;
    private ExpoUpdateArchive validator;
    private ObjectNode descriptor;
    private ObjectNode manifest;
    private byte[] asset;
    private String assetHash;
    @BeforeEach void prepare() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair();
        ExpoUpdateConfiguration configuration = mock(ExpoUpdateConfiguration.class);
        when(configuration.environment()).thenReturn("test"); when(configuration.publicKey()).thenReturn(keys.getPublic());
        when(configuration.assetUrl(anyString(), anyString())).thenAnswer(call -> API + "/app-updates/ota/assets/" + call.getArgument(0) + "/" + call.getArgument(1));
        validator = new ExpoUpdateArchive(configuration);
        String id = UUID.randomUUID().toString();
        descriptor = mapper.createObjectNode().put("schemaVersion", 1).put("id", id).put("environment", "test")
                .put("runtimeVersion", RUNTIME).put("version", "6.8.1").put("sourceSha", "a".repeat(40)).put("kind", "UPDATE");
        ObjectNode metadata = descriptor.deepCopy(); metadata.remove(java.util.List.of("schemaVersion", "kind"));
        asset = "hermes-bytecode".getBytes(StandardCharsets.UTF_8); assetHash = ExpoUpdateArchive.sha256(asset);
        ObjectNode launchAsset = mapper.createObjectNode().put("key", "abc123").put("fileExtension", ".bundle")
                .put("hash", Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.HexFormat.of().parseHex(assetHash)))
                .put("contentType", "application/javascript").put("url", API + "/app-updates/ota/assets/" + RUNTIME + "/" + assetHash);
        manifest = mapper.createObjectNode().put("id", id).put("createdAt", Instant.now().minusSeconds(2).toString()).put("runtimeVersion", RUNTIME);
        manifest.set("metadata", metadata); manifest.set("launchAsset", launchAsset); manifest.putArray("assets"); manifest.putObject("extra");
    }
    @Test void acceptsSignedCompleteUpdateAndCleansStagingFiles() throws Exception {
        java.nio.file.Path directory;
        try (var extracted = validator.extract(archive(Map.of(), false))) {
            assertThat(extracted.update().getVersionName()).isEqualTo("6.8.1");
            assertThat(extracted.update().getRuntimeVersion()).isEqualTo(RUNTIME);
            assertThat(extracted.assets()).hasSize(1);
            directory = extracted.directory();
            assertThat(Files.readAllBytes(extracted.assets().getFirst().file())).isEqualTo(asset);
        }
        assertThat(directory).doesNotExist();
    }
    @Test void rejectsModifiedSignatureEnvironmentRuntimeAndResourceUrl() throws Exception {
        assertThatThrownBy(() -> validator.extract(archive(Map.of(), true))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("签名");
        manifest.withObject("metadata").put("environment", "production"); descriptor.put("environment", "production");
        assertThatThrownBy(() -> validator.extract(archive(Map.of(), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("其他环境");
        manifest.withObject("metadata").put("environment", "test"); descriptor.put("environment", "test");
        manifest.put("runtimeVersion", "android-6.9.0-native5009000");
        assertThatThrownBy(() -> validator.extract(archive(Map.of(), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("运行时");
        manifest.put("runtimeVersion", RUNTIME);
        manifest.withObject("launchAsset").put("url", "https://other.example/asset");
        assertThatThrownBy(() -> validator.extract(archive(Map.of(), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("URL");
    }
    @Test void rejectsZipTraversalUnlistedFilesAndCorruptedAssets() throws Exception {
        assertThatThrownBy(() -> validator.extract(archive(Map.of("../escape", new byte[]{1}), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("路径");
        assertThatThrownBy(() -> validator.extract(archive(Map.of("assets/" + "b".repeat(64), new byte[]{1}), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("清单");
        asset = new byte[]{1};
        assertThatThrownBy(() -> validator.extract(archive(Map.of(), false))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("校验失败");
    }
    @Test void acceptsSignedRollbackBoundToItsEnvironmentAndRuntime() throws Exception {
        descriptor.put("kind", "ROLLBACK");
        ObjectNode metadata = (ObjectNode) manifest.get("metadata");
        manifest = mapper.createObjectNode().put("type", "rollBackToEmbedded");
        manifest.putObject("parameters").put("commitTime", Instant.now().minusSeconds(1).toString()).set("metadata", metadata);
        try (var extracted = validator.extract(archive(Map.of(), false))) {
            assertThat(extracted.update().getKind()).isEqualTo("ROLLBACK"); assertThat(extracted.assets()).isEmpty();
        }
    }
    private MockMultipartFile archive(Map<String, byte[]> additional, boolean tamper) throws Exception {
        byte[] payload = mapper.writeValueAsBytes(manifest);
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(keys.getPrivate()); signer.update(payload);
        byte[] signed = signer.sign(); if (tamper) signed[0] ^= 1;
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("release.json", mapper.writeValueAsBytes(descriptor)); files.put("manifest.json", payload);
        files.put("signature.txt", Base64.getEncoder().encode(signed));
        if ("UPDATE".equals(descriptor.path("kind").asText())) files.put("assets/" + assetHash, asset);
        files.putAll(additional);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return new MockMultipartFile("archive", "ota.zip", "application/zip", buffer.toByteArray());
    }
}
