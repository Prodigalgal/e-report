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
    @Test void servesOriginalSignedBytesAndNoContentForCurrentOrMissingUpdate() {
        ExpoUpdateService service = mock(ExpoUpdateService.class);
        ExpoUpdateConfiguration config = mock(ExpoUpdateConfiguration.class); when(config.environment()).thenReturn("test");
        var controller = new ExpoUpdateController(service, config, mock(CloudflareR2AppReleaseService.class));
        ExpoUpdate update = new ExpoUpdate(); update.setId(UUID.randomUUID()); update.setPayload("{\"signed\":true}"); update.setSignature("YWJj"); update.setKind("UPDATE");
        when(service.latest("runtime")).thenReturn(Optional.of(update));
        var response = controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("expo-manifest-filters")).isEqualTo("environment=\"test\"");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("name=\"manifest\"", "expo-signature: sig=\"YWJj\"", "\r\n\r\n" + update.getPayload() + "\r\n");
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", update.getId().toString(), null, "test").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        when(service.latest("runtime")).thenReturn(Optional.empty());
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "test").getBody()).isNull();
        assertThatThrownBy(() -> controller.manifest("1", "android", "runtime", "multipart/mixed", null, null, "production"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    @Test void rollbackRequiresMultipartAndDoesNotRunAgainOnEmbeddedClient() {
        ExpoUpdateService service = mock(ExpoUpdateService.class); ExpoUpdateConfiguration config = mock(ExpoUpdateConfiguration.class);
        when(config.environment()).thenReturn("production");
        var controller = new ExpoUpdateController(service, config, mock(CloudflareR2AppReleaseService.class));
        ExpoUpdate rollback = new ExpoUpdate(); rollback.setId(UUID.randomUUID()); rollback.setKind("ROLLBACK"); rollback.setPayload("{\"type\":\"rollBackToEmbedded\"}"); rollback.setSignature("YWJj");
        when(service.latest("runtime")).thenReturn(Optional.of(rollback));
        assertThatThrownBy(() -> controller.manifest("1", "android", "runtime", "application/json", null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.manifest("1", "android", "runtime", "multipart/mixed", "embedded", "embedded", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
