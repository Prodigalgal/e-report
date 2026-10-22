package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.pojo.ClientObservationEvent;
import com.pro.ereportbacken.model.pojo.ObservationEventType;
import com.pro.ereportbacken.model.pojo.ObservationPlatform;
import com.pro.ereportbacken.model.pojo.ObservationSeverity;
import com.pro.ereportbacken.model.pojo.ObservationViewMode;
import com.pro.ereportbacken.repository.ClientObservationEventRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.OrganizationScopeAuthorizationService;
import com.pro.ereportbacken.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservationQueryServiceTests {

    @Test
    void listsAnonymousEventsWithoutLookingUpNullOrganizationOrUserIds() {
        UUID tenantId = UUID.randomUUID();
        ClientObservationEvent event = new ClientObservationEvent();
        event.setTenantId(tenantId);
        event.setClientEventId("anonymous-event");
        event.setPlatform(ObservationPlatform.ANDROID);
        event.setEventType(ObservationEventType.ERROR);
        event.setSeverity(ObservationSeverity.ERROR);
        event.setCategory("LOCATION");
        event.setEventName("LOCATION_FAILED");
        event.setReceivedAt(LocalDateTime.now());

        ClientObservationEventRepository eventRepository = mock(ClientObservationEventRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationScopeAuthorizationService authorizationService = mock(OrganizationScopeAuthorizationService.class);
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(authorizationService.accessFor("observability:view"))
                .thenReturn(new OrganizationScopeAuthorizationService.OrganizationAccess(true, Set.of()));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(orgUnitRepository.findAllById(Set.of())).thenReturn(List.of());
        when(userRepository.findAllById(Set.of())).thenReturn(List.of());

        ObservationQueryService service = new ObservationQueryService(
                eventRepository,
                orgUnitRepository,
                userRepository,
                tenantContext,
                authorizationService,
                new ObjectMapper()
        );

        var result = service.search(
                null, null, null, ObservationViewMode.DIAGNOSTIC, null, null, null, null,
                null, null, PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.orgUnitId()).isNull();
            assertThat(summary.orgUnitName()).isNull();
            assertThat(summary.userId()).isNull();
            assertThat(summary.userName()).isNull();
        });
    }

    @Test
    void returnsPlainJsonStructuresForDetailContract() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ClientObservationEvent event = new ClientObservationEvent();
        event.setId(eventId);
        event.setTenantId(tenantId);
        event.setClientEventId("detail-event");
        event.setPlatform(ObservationPlatform.ANDROID);
        event.setEventType(ObservationEventType.DIAGNOSTIC);
        event.setSeverity(ObservationSeverity.INFO);
        event.setCategory("LOCATION");
        event.setEventName("LOCATION_DIAGNOSTIC");
        event.setContextJson("{\"permission\":\"granted\",\"attempts\":2}");
        event.setBreadcrumbsJson("[{\"name\":\"OPEN_FORM\"}]");
        event.setReceivedAt(LocalDateTime.now());

        ClientObservationEventRepository eventRepository = mock(ClientObservationEventRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationScopeAuthorizationService authorizationService = mock(OrganizationScopeAuthorizationService.class);
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(authorizationService.accessFor("observability:view"))
                .thenReturn(new OrganizationScopeAuthorizationService.OrganizationAccess(true, Set.of()));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(orgUnitRepository.findAllById(Set.of())).thenReturn(List.of());
        when(userRepository.findAllById(Set.of())).thenReturn(List.of());

        ObservationQueryService service = new ObservationQueryService(
                eventRepository,
                orgUnitRepository,
                userRepository,
                tenantContext,
                authorizationService,
                new ObjectMapper()
        );

        var detail = service.findById(eventId);

        assertThat(detail.context()).isEqualTo(Map.of("permission", "granted", "attempts", 2));
        assertThat(detail.breadcrumbs()).isEqualTo(List.of(Map.of("name", "OPEN_FORM")));
    }

    @Test
    void exposesSuccessfulOperationDetailForBehaviorInvestigation() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ClientObservationEvent event = new ClientObservationEvent();
        event.setId(eventId);
        event.setTenantId(tenantId);
        event.setClientEventId("operation-success");
        event.setPlatform(ObservationPlatform.WEB);
        event.setEventType(ObservationEventType.OPERATION);
        event.setSeverity(ObservationSeverity.INFO);
        event.setCategory("USER_ACTION");
        event.setEventName("PROFILE_VIEW");
        event.setReceivedAt(LocalDateTime.now());

        ClientObservationEventRepository eventRepository = mock(ClientObservationEventRepository.class);
        OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationScopeAuthorizationService authorizationService = mock(OrganizationScopeAuthorizationService.class);
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(authorizationService.accessFor("observability:view"))
                .thenReturn(new OrganizationScopeAuthorizationService.OrganizationAccess(true, Set.of()));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(orgUnitRepository.findAllById(Set.of())).thenReturn(List.of());
        when(userRepository.findAllById(Set.of())).thenReturn(List.of());

        ObservationQueryService service = new ObservationQueryService(
                eventRepository,
                orgUnitRepository,
                userRepository,
                tenantContext,
                authorizationService,
                new ObjectMapper()
        );

        assertThat(service.findById(eventId).eventName()).isEqualTo("PROFILE_VIEW");
    }
}
