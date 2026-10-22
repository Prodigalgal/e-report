package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.notification.EmailNotificationPreferencesService;
import com.pro.ereportbacken.security.TenantUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CurrentEmailNotificationPreferencesControllerTests {
    private final EmailNotificationPreferencesService service = mock(EmailNotificationPreferencesService.class);
    private final CurrentEmailNotificationPreferencesController controller = new CurrentEmailNotificationPreferencesController(service);
    private final TenantUserDetails principal = mock(TenantUserDetails.class);

    @Test
    void readsAndUpdatesTheAuthenticatedAccountWithoutAClientSuppliedUserId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(principal.tenantId()).thenReturn(tenantId);
        when(principal.membershipId()).thenReturn(membershipId);
        var preferences = new EmailNotificationPreferencesDTO(true, false, true, false);
        when(service.get(tenantId, membershipId)).thenReturn(preferences);
        when(service.update(eq(tenantId), eq(membershipId), any())).thenReturn(preferences);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/auth/me/email-notifications").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workOrderSlaBreached").value(false));
        mvc.perform(put("/auth/me/email-notifications").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workOrderCreated":true,"workOrderSlaBreached":false,
                                 "workOrderAssigned":true,"dailyOutstandingReport":false}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyOutstandingReport").value(false));
        verify(service).update(tenantId, membershipId, preferences);
    }

    @Test
    void unauthenticatedAndNonTenantPrincipalsAreRejected() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/auth/me/email-notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me/email-notifications").principal(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void incompleteOrMalformedPreferencesAreRejected() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(put("/auth/me/email-notifications").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workOrderCreated\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/auth/me/email-notifications").principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workOrderCreated\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
