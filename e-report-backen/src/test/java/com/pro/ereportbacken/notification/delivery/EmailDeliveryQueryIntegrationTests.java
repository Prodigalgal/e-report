package com.pro.ereportbacken.notification.delivery;

import com.pro.ereportbacken.model.pojo.*;
import com.pro.ereportbacken.repository.*;
import com.pro.ereportbacken.security.*;
import com.pro.ereportbacken.time.DisplayTime;
import com.pro.ereportbacken.time.UtcTime;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EmailDeliveryQueryIntegrationTests {
    private final EmailDeliveryRepository deliveries;
    private final OrgUnitRepository organizations;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final EmailDeliveryQueryService service;
    private final WebApplicationContext web;
    private OrgUnit root;
    private OrgUnit county;
    private OrgUnit sibling;
    private User actor;
    private MockMvc mvc;

    EmailDeliveryQueryIntegrationTests(EmailDeliveryRepository deliveries, OrgUnitRepository organizations,
                                        TenantRepository tenants, UserRepository users,
                                        EmailDeliveryQueryService service, WebApplicationContext web) {
        this.deliveries = deliveries; this.organizations = organizations; this.tenants = tenants;
        this.users = users; this.service = service; this.web = web;
    }

    @BeforeEach
    void prepare() {
        Tenant tenant = tenant();
        root = organization(tenant.getId(), null);
        tenant.setRootOrgUnitId(root.getId()); tenants.saveAndFlush(tenant);
        county = organization(tenant.getId(), root);
        sibling = organization(tenant.getId(), root);
        actor = new User(); actor.setTenantId(tenant.getId()); actor.setOrgUnitId(county.getId());
        actor.setUsername("delivery-" + UUID.randomUUID()); actor.setName("邮件台账验收"); actor.setPhone("19900000111");
        actor.setPassword("encoded-test-password"); actor.setStatus(UserStatus.ACTIVE); actor.setAuthorizationVersion(1L);
        actor = users.saveAndFlush(actor);
        SecurityContextHolder.getContext().setAuthentication(as(PermissionCatalog.EMAIL_DELIVERY_VIEW));
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void listAndDetailsExcludeSiblingAndOtherTenantRecords() {
        EmailDelivery own = delivery(county, EmailDeliveryStatus.SMTP_ACCEPTED, "范围验证");
        EmailDelivery peer = delivery(sibling, EmailDeliveryStatus.FAILED, "同级组织");
        EmailDelivery other = delivery(organization(tenant().getId(), null), EmailDeliveryStatus.FAILED, "其他租户");
        var page = service.search(null, null, "", null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(record -> record.id()).containsExactly(own.getId());
        assertThat(service.detail(own.getId()).orgUnitId()).isEqualTo(county.getId());
        assertThatThrownBy(() -> service.detail(peer.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.detail(other.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.search(null, null, "", sibling.getId(), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.scopes()).extracting(option -> option.id()).contains(county.getId()).doesNotContain(sibling.getId());
    }

    @Test
    void staleSendingIsUnknownAndLiteralWildcardsDoNotExpandSearch() {
        var stale = delivery(county, EmailDeliveryStatus.SENDING, "literal%_entry");
        stale.setStartedAt(UtcTime.now().minusMinutes(16)); deliveries.saveAndFlush(stale);
        delivery(county, EmailDeliveryStatus.SENDING, "normal entry");
        var unknown = service.search(null, EmailDeliveryStatus.UNKNOWN, "%_", null, null, null, PageRequest.of(0, 20));
        assertThat(unknown.getContent()).hasSize(1);
        assertThat(unknown.getContent().getFirst().status()).isEqualTo(EmailDeliveryStatus.UNKNOWN);
        assertThat(service.search(null, EmailDeliveryStatus.SENDING, "", null, null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void businessDateFilterUsesUtc8MidnightAndRejectsUnboundedRanges() {
        LocalDate date = LocalDate.now(DisplayTime.ZONE_ID);
        var included = delivery(county, EmailDeliveryStatus.SMTP_ACCEPTED, "日期边界");
        included.setStartedAt(DisplayTime.startOfDisplayDateUtc(date)); deliveries.saveAndFlush(included);
        var excluded = delivery(county, EmailDeliveryStatus.SMTP_ACCEPTED, "日期边界前");
        excluded.setStartedAt(DisplayTime.startOfDisplayDateUtc(date).minusNanos(1_000)); deliveries.saveAndFlush(excluded);
        assertThat(service.search(null, null, "", null, date, date, PageRequest.of(0, 20)).getContent())
                .extracting(record -> record.id()).containsExactly(included.getId());
        assertThatThrownBy(() -> service.search(null, null, "", null, date.minusDays(93), date, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void independentPermissionProtectsEveryHttpEndpoint() throws Exception {
        var own = delivery(county, EmailDeliveryStatus.FAILED, "HTTP 验证");
        for (String path : new String[]{"/admin/notifications/emails", "/admin/notifications/emails/scopes", "/admin/notifications/emails/" + own.getId()}) {
            mvc.perform(get(path).with(authentication(as(PermissionCatalog.NOTIFICATION_CONFIG_VIEW, PermissionCatalog.OBSERVABILITY_VIEW))))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).with(authentication(as(PermissionCatalog.EMAIL_DELIVERY_VIEW))))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/admin/notifications/emails").param("status", "NOT_A_STATUS")
                        .with(authentication(as(PermissionCatalog.EMAIL_DELIVERY_VIEW))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionIsNotInAnyDefaultAdministratorOrPositionTemplate() {
        assertThat(BuiltInAuthorizationProfileCatalog.templates(true)).allSatisfy(template ->
                assertThat(template.rules()).extracting(rule -> rule.permissionName()).doesNotContain(PermissionCatalog.EMAIL_DELIVERY_VIEW));
    }

    private EmailDelivery delivery(OrgUnit organization, EmailDeliveryStatus status, String subject) {
        EmailDelivery record = new EmailDelivery(); record.setTenantId(organization.getTenantId());
        record.setOrgUnitId(organization.getId()); record.setCategory(EmailDeliveryCategory.CREATED);
        record.setStatus(status); record.setSubject(subject); record.setRecipientAddress("re***@example.test");
        record.setStartedAt(UtcTime.now()); return deliveries.saveAndFlush(record);
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant(); tenant.setCode("delivery-" + UUID.randomUUID()); tenant.setName("邮件台账隔离测试");
        return tenants.saveAndFlush(tenant);
    }

    private OrgUnit organization(UUID tenantId, OrgUnit parent) {
        OrgUnit org = new OrgUnit(); org.setTenantId(tenantId); org.setParentId(parent == null ? null : parent.getId());
        org.setCode("delivery-" + UUID.randomUUID()); org.setName("邮件台账测试组织");
        org.setUnitType(parent == null ? OrgUnitType.CITY : OrgUnitType.COUNTY);
        org.setPath((parent == null ? "/" : parent.getPath()) + org.getCode() + "/");
        return organizations.saveAndFlush(org);
    }

    private UsernamePasswordAuthenticationToken as(String... permissions) {
        var grants = Arrays.stream(permissions).map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(UUID.randomUUID(), permission, false, null, null)).toList();
        var snapshot = new UserAuthorizationSnapshot(actor.getId(), actor.getTenantId(), actor.getOrgUnitId(), 1L,
                actor.getUsername(), actor.getSystemKey(), actor.getStatus(), grants);
        var authorities = snapshot.authorities().stream().map(SimpleGrantedAuthority::new).toList();
        var principal = new TenantUserDetails(actor.getId(), actor.getTenantId(), actor.getOrgUnitId(), 1L,
                actor.getUsername(), "not-used", true, authorities, snapshot);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
