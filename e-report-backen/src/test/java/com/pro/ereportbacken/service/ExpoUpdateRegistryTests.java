package com.pro.ereportbacken.service;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.repository.ExpoUpdateRepository;
import com.pro.ereportbacken.time.UtcTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:expo_registry;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ExpoUpdateRegistryTests {
    @Autowired private ExpoUpdateRegistry registry;
    @Autowired private ExpoUpdateRepository repository;

    @Test void doesNotActivateUntilDistributionFinishesAndRetryPreservesIdentity() {
        ExpoUpdate candidate = candidate();
        var first = registry.stage(candidate);
        assertThat(first.claimed()).isTrue();
        assertThat(repository.findFirstByEnvironmentAndRuntimeVersionAndStatusOrderByCreatedAtDescPublishedAtDesc("test", candidate.getRuntimeVersion(), "PUBLISHED")).isEmpty();
        assertThat(registry.stage(candidate).claimed()).isFalse();
        registry.finish(candidate.getId(), first.update().getAttemptId(), false);
        assertThat(repository.findById(candidate.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
        var second = registry.stage(candidate());
        // A different update may run independently, while retrying this update keeps its immutable ID.
        var retry = registry.stage(candidate);
        assertThat(second.claimed()).isTrue(); assertThat(retry.claimed()).isTrue();
        registry.finish(candidate.getId(), first.update().getAttemptId(), true);
        assertThat(repository.findById(candidate.getId()).orElseThrow().getStatus()).isEqualTo("PUBLISHING");
        registry.finish(candidate.getId(), retry.update().getAttemptId(), true);
        assertThat(registry.stage(candidate).claimed()).isFalse();
        assertThat(repository.findFirstByEnvironmentAndRuntimeVersionAndStatusOrderByCreatedAtDescPublishedAtDesc("test", candidate.getRuntimeVersion(), "PUBLISHED")).isPresent();
        candidate.setSha256("b".repeat(64));
        assertThatThrownBy(() -> registry.stage(candidate)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("禁止覆盖");
    }
    private ExpoUpdate candidate() {
        ExpoUpdate update = new ExpoUpdate(); update.setId(UUID.randomUUID()); update.setEnvironment("test");
        update.setRuntimeVersion("android-6.8.0-native5008000"); update.setVersionName("6.8.1"); update.setSourceSha("a".repeat(40));
        update.setKind("UPDATE"); update.setPayload("{}"); update.setSignature("signature"); update.setSha256("a".repeat(64)); update.setCreatedAt(UtcTime.now());
        return update;
    }
}
