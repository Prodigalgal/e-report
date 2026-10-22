package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.OrgUnitDTO;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.TenantStatus;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.WorkOrderType;
import com.pro.ereportbacken.repository.UserNotificationContact;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.repository.PermissionGrantRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailNotificationRecipientResolverTests {

    @Test
    void resolvesOnlyCountyAdministratorsAndPrefersExplicitEmail() {
        UUID tenantId = uuid(1);
        UUID countyId = uuid(2);
        UUID reportingOrgId = uuid(3);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PermissionGrantRepository grantRepository = mock(PermissionGrantRepository.class);
        Set<UUID> recipientMembershipIds = Set.of(uuid(10), uuid(11), uuid(12));
        OrgUnitDTO county = county(tenantId, countyId);
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(hierarchyService.nearestAncestorOfType(tenantId, reportingOrgId, OrgUnitType.COUNTY))
                .thenReturn(Optional.of(county));
        when(hierarchyService.descendantIds(tenantId, countyId)).thenReturn(Set.of(countyId, reportingOrgId));
        when(grantRepository.findActiveMembershipIdsByPermission(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(PermissionCatalog.NOTIFICATION_RECIPIENT_COUNTY),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.copyOf(recipientMembershipIds));
        when(userRepository.findNotificationContactsByMembershipsAndOrganizations(
                tenantId, UserStatus.ACTIVE, recipientMembershipIds,
                Set.of(countyId, reportingOrgId)))
                .thenReturn(List.of(
                        contact(uuid(10), "13900000001", "county-admin@example.com"),
                        contact(uuid(11), "13800000002", null),
                        contact(uuid(12), null, "invalid-address")
                ));
        EmailNotificationRecipientResolver resolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, "");

        var recipients = resolver.resolve(notification(reportingOrgId)).recipients();

        assertThat(recipients).extracting(EmailNotificationRecipientResolver.EmailRecipient::address)
                .containsExactly("county-admin@example.com", "13800000002@139.com");
        assertThat(recipients.getFirst().phoneFallback()).isFalse();
        assertThat(recipients.getLast().phoneFallback()).isTrue();
        verify(userRepository).findNotificationContactsByMembershipsAndOrganizations(
                tenantId, UserStatus.ACTIVE, recipientMembershipIds,
                Set.of(countyId, reportingOrgId));
    }

    @Test
    void filtersCountyEmailRecipientsToConfiguredUserIds() {
        UUID tenantId = uuid(1);
        UUID countyId = uuid(2);
        UUID reportingOrgId = uuid(3);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PermissionGrantRepository grantRepository = mock(PermissionGrantRepository.class);
        Set<UUID> recipientMembershipIds = Set.of(uuid(10), uuid(11));
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(hierarchyService.nearestAncestorOfType(tenantId, reportingOrgId, OrgUnitType.COUNTY))
                .thenReturn(Optional.of(county(tenantId, countyId)));
        when(hierarchyService.descendantIds(tenantId, countyId)).thenReturn(Set.of(countyId, reportingOrgId));
        when(grantRepository.findActiveMembershipIdsByPermission(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(PermissionCatalog.NOTIFICATION_RECIPIENT_COUNTY),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.copyOf(recipientMembershipIds));
        when(userRepository.findNotificationContactsByMembershipsAndOrganizations(
                tenantId, UserStatus.ACTIVE, recipientMembershipIds, Set.of(countyId, reportingOrgId)))
                .thenReturn(List.of(
                        contact(uuid(10), "13900000001", "superadmin@example.com"),
                        contact(uuid(11), "13800000002", "other-admin@example.com")
                ));
        EmailNotificationRecipientResolver resolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, uuid(10).toString());

        var recipients = resolver.resolve(notification(reportingOrgId)).recipients();

        assertThat(recipients).containsExactly(new EmailNotificationRecipientResolver.EmailRecipient(
                uuid(10), "superadmin@example.com", false));
    }

    @Test
    void filtersAssignmentEmailRecipientsToConfiguredUserIds() {
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PermissionGrantRepository grantRepository = mock(PermissionGrantRepository.class);
        EmailNotificationRecipientResolver allowedResolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, uuid(35).toString());
        EmailNotificationRecipientResolver blockedResolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, uuid(36).toString());
        WorkOrderNotification created = notification(uuid(33));
        WorkOrderNotification reassigned = new WorkOrderNotification(
                created.id(), created.reportingOrgUnitId(), created.responsibleOrgUnitId(),
                WorkOrderNotificationKind.ASSIGNMENT_RECEIVED, created.reportType(), created.reportTypeName(),
                created.priorityName(), created.slaStageName(), created.slaDueAt(), created.slaBreachedAt(),
                created.location(), created.gpsCoordinates(), created.reason(), created.phone(),
                created.contactPerson(), created.reportTime(), created.attachments(), uuid(34),
                new WorkOrderNotification.AssigneeContact(uuid(35), "处理人", "assignee@example.com", "13800000003", true),
                false);

        assertThat(allowedResolver.resolve(reassigned).recipients()).containsExactly(
                new EmailNotificationRecipientResolver.EmailRecipient(uuid(35), "assignee@example.com", false));
        assertThat(blockedResolver.resolve(reassigned).recipients()).isEmpty();
        verifyNoInteractions(hierarchyService, userRepository, grantRepository);
    }

    @Test
    void rejectsInvalidRecipientAllowlistAtStartup() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new EmailNotificationRecipientResolver(
                mock(TenantContext.class), mock(OrganizationHierarchyService.class),
                mock(UserRepository.class), mock(PermissionGrantRepository.class), "not-a-user-id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new EmailNotificationRecipientResolver(
                mock(TenantContext.class), mock(OrganizationHierarchyService.class),
                mock(UserRepository.class), mock(PermissionGrantRepository.class), " , ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
    }

    @Test
    void fallsBackToCityAdministratorsForCityDirectOrders() {
        UUID tenantId = uuid(1);
        UUID reportingOrgId = uuid(4);
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PermissionGrantRepository grantRepository = mock(PermissionGrantRepository.class);
        Set<UUID> recipientMembershipIds = Set.of(uuid(20));
        when(tenantContext.requireTenantId()).thenReturn(tenantId);
        when(hierarchyService.nearestAncestorOfType(tenantId, reportingOrgId, OrgUnitType.COUNTY))
                .thenReturn(Optional.empty());
        when(grantRepository.findActiveMembershipIdsByPermission(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(PermissionCatalog.NOTIFICATION_RECIPIENT_TENANT),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.copyOf(recipientMembershipIds));
        when(userRepository.findNotificationContactsByMemberships(
                tenantId, UserStatus.ACTIVE, recipientMembershipIds))
                .thenReturn(List.of(contact(uuid(20), "13900000003", null)));
        EmailNotificationRecipientResolver resolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, "");

        assertThat(resolver.resolve(notification(reportingOrgId)).recipients())
                .extracting(EmailNotificationRecipientResolver.EmailRecipient::address)
                .containsExactly("13900000003@139.com");
        verify(userRepository).findNotificationContactsByMemberships(
                tenantId, UserStatus.ACTIVE, recipientMembershipIds);
    }

    @Test
    void reassignmentNoticeTargetsOnlyTheCurrentAssignee() {
        TenantContext tenantContext = mock(TenantContext.class);
        OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PermissionGrantRepository grantRepository = mock(PermissionGrantRepository.class);
        EmailNotificationRecipientResolver resolver = new EmailNotificationRecipientResolver(
                tenantContext, hierarchyService, userRepository, grantRepository, "");
        WorkOrderNotification created = notification(uuid(33));
        WorkOrderNotification reassigned = new WorkOrderNotification(
                created.id(), created.reportingOrgUnitId(), created.responsibleOrgUnitId(),
                WorkOrderNotificationKind.ASSIGNMENT_RECEIVED, created.reportType(), created.reportTypeName(),
                created.priorityName(), created.slaStageName(), created.slaDueAt(), created.slaBreachedAt(),
                created.location(), created.gpsCoordinates(), created.reason(), created.phone(),
                created.contactPerson(), created.reportTime(), created.attachments(), uuid(34),
                new WorkOrderNotification.AssigneeContact(uuid(35), "处理人", "assignee@example.com", "13800000003", true),
                false);

        var recipients = resolver.resolve(reassigned).recipients();

        assertThat(recipients).containsExactly(new EmailNotificationRecipientResolver.EmailRecipient(
                uuid(35), "assignee@example.com", false));
        verifyNoInteractions(hierarchyService, userRepository, grantRepository);
    }

    @Test
    void eachOrganizationSubscriptionIsAppliedIndependently() {
        var resolver = resolverWithContacts(contact(uuid(10), "13900000001", "admin@example.com", false, true, true, false));

        assertThat(resolver.resolve(uuid(1), uuid(4), EmailNotificationCategory.CREATED).allUnsubscribed()).isTrue();
        assertThat(resolver.resolve(uuid(1), uuid(4), EmailNotificationCategory.SLA_BREACHED).recipients()).hasSize(1);
        assertThat(resolver.resolve(uuid(1), uuid(4), EmailNotificationCategory.DAILY_OUTSTANDING_REPORT).allUnsubscribed()).isTrue();
    }

    @Test
    void filtersSubscriptionsBeforeDeduplicatingSharedEmailAddresses() {
        var resolver = resolverWithContacts(
                contact(uuid(10), null, "shared@example.com", false, true, true, true),
                contact(uuid(11), null, "SHARED@example.com", true, true, true, true));

        var selection = resolver.resolve(uuid(1), uuid(4), EmailNotificationCategory.CREATED);

        assertThat(selection.recipients()).extracting(EmailNotificationRecipientResolver.EmailRecipient::userId)
                .containsExactly(uuid(11));
        assertThat(selection.unsubscribedCount()).isEqualTo(1);
        assertThat(selection.allUnsubscribed()).isFalse();
    }

    @Test
    void subscribedAccountWithoutContactDetailsIsNotTreatedAsAnOptOut() {
        var resolver = resolverWithContacts(contact(uuid(10), null, null));
        var selection = resolver.resolve(uuid(1), uuid(4), EmailNotificationCategory.CREATED);

        assertThat(selection.recipients()).isEmpty();
        assertThat(selection.subscribedCount()).isEqualTo(1);
        assertThat(selection.allUnsubscribed()).isFalse();
    }

    @Test
    void assigneeCanDisablePersonalAssignmentEmails() {
        var resolver = new EmailNotificationRecipientResolver(mock(TenantContext.class),
                mock(OrganizationHierarchyService.class), mock(UserRepository.class), mock(PermissionGrantRepository.class), "");
        var base = notification(uuid(33));
        var assigned = new WorkOrderNotification(base.id(), base.reportingOrgUnitId(), base.responsibleOrgUnitId(),
                WorkOrderNotificationKind.ASSIGNMENT_RECEIVED, base.reportType(), base.reportTypeName(),
                base.priorityName(), base.slaStageName(), base.slaDueAt(), base.slaBreachedAt(), base.location(),
                base.gpsCoordinates(), base.reason(), base.phone(), base.contactPerson(), base.reportTime(),
                base.attachments(), uuid(34), new WorkOrderNotification.AssigneeContact(uuid(35), "处理人", "assignee@example.com", null, false), true);

        assertThat(resolver.resolve(assigned).allUnsubscribed()).isTrue();
        assertThat(resolver.resolve(assigned).recipients()).isEmpty();
    }

    private EmailNotificationRecipientResolver resolverWithContacts(UserNotificationContact... contacts) {
        var tenantContext = mock(TenantContext.class);
        var hierarchy = mock(OrganizationHierarchyService.class);
        var users = mock(UserRepository.class);
        var grants = mock(PermissionGrantRepository.class);
        var ids = java.util.Arrays.stream(contacts).map(UserNotificationContact::getUserId).collect(java.util.stream.Collectors.toSet());
        when(hierarchy.nearestAncestorOfType(uuid(1), uuid(4), OrgUnitType.COUNTY)).thenReturn(Optional.empty());
        when(grants.findActiveMembershipIdsByPermission(org.mockito.ArgumentMatchers.eq(uuid(1)),
                org.mockito.ArgumentMatchers.eq(PermissionCatalog.NOTIFICATION_RECIPIENT_TENANT), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.copyOf(ids));
        when(users.findNotificationContactsByMemberships(uuid(1), UserStatus.ACTIVE, ids)).thenReturn(List.of(contacts));
        return new EmailNotificationRecipientResolver(tenantContext, hierarchy, users, grants, "");
    }

    private WorkOrderNotification notification(UUID reportingOrgId) {
        return new WorkOrderNotification(
                uuid(100), reportingOrgId, reportingOrgId, WorkOrderNotificationKind.CREATED,
                WorkOrderType.NETWORK_FAULT, "网络故障", "普通", "等待接单",
                null, null, "测试位置", "", "测试原因", "13900000000", "测试联系人",
                LocalDateTime.now(), List.of(), null, null, false);
    }

    private OrgUnitDTO county(UUID tenantId, UUID id) {
        return new OrgUnitDTO(
                id,
                tenantId,
                null,
                "TEST_COUNTY",
                "测试县",
                OrgUnitType.COUNTY,
                "/" + id + "/",
              TenantStatus.ACTIVE,
              false
        );
    }

    private UserNotificationContact contact(UUID userId, String phone, String email) {
        return contact(userId, phone, email, true, true, true, true);
    }

    private UserNotificationContact contact(UUID userId, String phone, String email,
                                             boolean created, boolean sla, boolean assigned, boolean daily) {
        return new UserNotificationContact() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public String getEmail() { return email; }

            @Override
            public boolean getEmailWorkOrderCreated() { return created; }

            @Override
            public boolean getEmailWorkOrderSlaBreached() { return sla; }

            @Override
            public boolean getEmailWorkOrderAssigned() { return assigned; }

            @Override
            public boolean getEmailDailyOutstandingReport() { return daily; }
        };
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
