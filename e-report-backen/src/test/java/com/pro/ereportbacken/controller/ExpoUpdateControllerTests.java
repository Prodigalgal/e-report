package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.pojo.ExpoUpdate;
import com.pro.ereportbacken.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpoUpdateControllerTests {
    @Test void forwardsEmbeddedVersionAndVariesNoContentResponsesByClientIdentity() {
        ExpoUpdateService service = mock(ExpoUpdateService.class);
        ExpoUpdateConfiguration config = mock(ExpoUpdateConfiguration.class);
        when(config.environment()).thenReturn("test");
        when(service.latest("runtime", "6.11.0")).thenReturn(Optional.empty());
        var controller = new ExpoUpdateController(service, config, mock(CloudflareR2AppReleaseService.class));
        var response = controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "test", "6.11.0");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getVary()).contains("e-report-embedded-version", "expo-embedded-update-id");
        verify(service).latest("runtime", "6.11.0");
    }
    @Test void servesOriginalSignedBytesAndNoContentForCurrentOrMissingUpdate() {
        ExpoUpdateService service = mock(ExpoUpdateService.class);
        ExpoUpdateConfiguration config = mock(ExpoUpdateConfiguration.class); when(config.environment()).thenReturn("test");
        var controller = new ExpoUpdateController(service, config, mock(CloudflareR2AppReleaseService.class));
        ExpoUpdate update = new ExpoUpdate(); update.setId(UUID.randomUUID()); update.setPayload("{\"signed\":true}"); update.setSignature("YWJj"); update.setKind("UPDATE");
        when(service.latest("runtime", null)).thenReturn(Optional.of(update));
        var response = controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "test", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("expo-manifest-filters")).isEqualTo("environment=\"test\"");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("name=\"manifest\"", "expo-signature: sig=\"YWJj\"", "\r\n\r\n" + update.getPayload() + "\r\n");
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", update.getId().toString(), null, "test", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        when(service.latest("runtime", null)).thenReturn(Optional.empty());
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "test", null).getBody()).isNull();
        assertThatThrownBy(() -> controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "production", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    @Test void rollbackRequiresMultipartAndDoesNotRunAgainOnEmbeddedClient() {
        ExpoUpdateService service = mock(ExpoUpdateService.class); ExpoUpdateConfiguration config = mock(ExpoUpdateConfiguration.class);
        when(config.environment()).thenReturn("production");
        var controller = new ExpoUpdateController(service, config, mock(CloudflareR2AppReleaseService.class));
        ExpoUpdate rollback = new ExpoUpdate(); rollback.setId(UUID.randomUUID()); rollback.setKind("ROLLBACK"); rollback.setPayload("{\"type\":\"rollBackToEmbedded\"}"); rollback.setSignature("YWJj");
        when(service.latest("runtime", null)).thenReturn(Optional.of(rollback));
        assertThatThrownBy(() -> controller.manifest("1", "android", "runtime", "application/json", null, null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", "embedded", "embedded", null, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
