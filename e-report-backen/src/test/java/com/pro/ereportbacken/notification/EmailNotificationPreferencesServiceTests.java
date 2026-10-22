package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailNotificationPreferencesServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final EmailNotificationPreferencesService service = new EmailNotificationPreferencesService(users);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();

    @Test
    void readsOnlyTheAuthenticatedTenantsMembership() {
        var preferences = new EmailNotificationPreferencesDTO(true, false, true, false);
        when(users.findEmailNotificationPreferences(tenantId, membershipId)).thenReturn(Optional.of(preferences));

        assertThat(service.get(tenantId, membershipId)).isEqualTo(preferences);
        verify(users).findEmailNotificationPreferences(tenantId, membershipId);
    }

    @Test
    void allFourSubscriptionsCanBeDisabledAtomically() {
        var preferences = new EmailNotificationPreferencesDTO(false, false, false, false);
        when(users.updateEmailNotificationPreferences(eq(tenantId), eq(membershipId),
                eq(false), eq(false), eq(false), eq(false), any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.update(tenantId, membershipId, preferences)).isEqualTo(preferences);
        verify(users).updateEmailNotificationPreferences(eq(tenantId), eq(membershipId),
                eq(false), eq(false), eq(false), eq(false), any(LocalDateTime.class));
    }

    @Test
    void missingFieldsAreRejectedBeforeAnyDatabaseWrite() {
        assertThatThrownBy(() -> service.update(tenantId, membershipId,
                new EmailNotificationPreferencesDTO(true, true, null, true))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users);
    }

    @Test
    void wrongTenantOrMissingMembershipCannotCreateSettings() {
        assertThatThrownBy(() -> service.update(tenantId, membershipId,
                new EmailNotificationPreferencesDTO(true, true, true, true))).isInstanceOf(EntityNotFoundException.class);
    }
}
