package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.dto.EmailNotificationPreferencesDTO;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.model.pojo.OrgUnitType;
import com.pro.ereportbacken.model.pojo.Tenant;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.model.pojo.UserSystemKey;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminEmailNotificationPreferencesIntegrationTests {
    private static final String VIEW = PermissionCatalog.USER_MANAGE_VIEW;
    private static final String MODIFY = PermissionCatalog.USER_MANAGE_CONTACT_UPDATE;
    private static final AtomicLong NEXT_PHONE = new AtomicLong(18080000000L);
    private static final String SELECTED = """
            {"workOrderCreated":false,"workOrderSlaBreached":true,
             "workOrderAssigned":false,"dailyOutstandingReport":true}
            """;
    private static final EmailNotificationPreferencesDTO DEFAULTS = new EmailNotificationPreferencesDTO(true, true, true, true);
    private static final EmailNotificationPreferencesDTO PREFERENCES = new EmailNotificationPreferencesDTO(false, true, false, true);

    private final WebApplicationContext context;
    private final UserRepository users;
    private final OrgUnitRepository organizations;
    private final TenantRepository tenants;
    private final EmailNotificationPreferencesService preferences;
    private MockMvc mvc;
    private Tenant tenant;
    private OrgUnit root;
    private OrgUnit county;
    private OrgUnit subordinate;
    private OrgUnit sibling;
    private User administrator;
    private User peer;

    AdminEmailNotificationPreferencesIntegrationTests(WebApplicationContext context, UserRepository users,
                                                       OrgUnitRepository organizations, TenantRepository tenants,
                                                       EmailNotificationPreferencesService preferences) {
        this.context = context;
        this.users = users;
        this.organizations = organizations;
        this.tenants = tenants;
        this.preferences = preferences;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = tenant();
        root = organization(tenant, null);
        tenant.setRootOrgUnitId(root.getId());
        tenants.saveAndFlush(tenant);
        county = organization(tenant, root);
        subordinate = organization(tenant, county);
        sibling = organization(tenant, root);
        administrator = account(county);
        peer = account(county);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorAndAccountOwnerReadTheSamePreferencesWithoutChangingProfileOrAuthorization() throws Exception {
        String name = peer.getName();
        String phone = peer.getPhone();
        String email = peer.getEmail();
        String password = peer.getPassword();
        UUID organizationId = peer.getOrgUnitId();
        Long authorizationVersion = peer.getAuthorizationVersion();

        mvc.perform(put(path(peer)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workOrderCreated").value(false));
        mvc.perform(get("/auth/me/email-notifications").with(authentication(as(peer))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workOrderAssigned").value(false));
        var reloaded = users.findById(peer.getId()).orElseThrow();
        assertThat(preferences.get(peer.getTenantId(), peer.getId())).isEqualTo(PREFERENCES);
        assertThat(reloaded.getName()).isEqualTo(name);
        assertThat(reloaded.getPhone()).isEqualTo(phone);
        assertThat(reloaded.getEmail()).isEqualTo(email);
        assertThat(reloaded.getPassword()).isEqualTo(password);
        assertThat(reloaded.getOrgUnitId()).isEqualTo(organizationId);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getAuthorizationVersion()).isEqualTo(authorizationVersion);
    }

    @Test
    void ownerCanChangeAnAdministratorsSelectionAndAdministratorReadsTheResult() throws Exception {
        preferences.update(peer.getTenantId(), peer.getId(), PREFERENCES);
        mvc.perform(put("/auth/me/email-notifications").with(authentication(as(peer))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"workOrderCreated":true,"workOrderSlaBreached":false,
                                 "workOrderAssigned":true,"dailyOutstandingReport":false}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get(path(peer)).with(authentication(as(administrator, VIEW))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workOrderSlaBreached").value(false))
                .andExpect(jsonPath("$.dailyOutstandingReport").value(false));
    }

    @Test
    void administratorCanModifyASubordinateOrganization() throws Exception {
        User target = account(subordinate);
        mvc.perform(put(path(target)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isOk());
        assertThat(preferences.get(target.getTenantId(), target.getId())).isEqualTo(PREFERENCES);
    }

    @Test
    void viewingUsersDoesNotAllowChangingTheirPreferences() throws Exception {
        mvc.perform(get(path(peer)).with(authentication(as(administrator, VIEW))))
                .andExpect(status().isOk());
        mvc.perform(put(path(peer)).with(authentication(as(administrator, VIEW))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isForbidden());
        assertDefaults(peer);
    }

    @Test
    void contactPermissionAloneDoesNotBypassTheUserViewPermission() throws Exception {
        mvc.perform(get(path(peer)).with(authentication(as(administrator, MODIFY))))
                .andExpect(status().isForbidden());
        mvc.perform(put(path(peer)).with(authentication(as(administrator, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isForbidden());
        assertDefaults(peer);
    }

    @Test
    void siblingAndSuperiorOrganizationsAreOutsideTheManagementScope() throws Exception {
        for (OrgUnit outsideScope : new OrgUnit[]{sibling, root}) {
            User target = account(outsideScope);
            mvc.perform(get(path(target)).with(authentication(as(administrator, VIEW, MODIFY))))
                    .andExpect(status().isForbidden());
            mvc.perform(put(path(target)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                    .andExpect(status().isForbidden());
            assertDefaults(target);
        }
    }

    @Test
    void crossTenantAccountIsNotExposedOrUpdated() throws Exception {
        Tenant otherTenant = tenant();
        User target = account(organization(otherTenant, null));
        mvc.perform(get(path(target)).with(authentication(as(administrator, VIEW, MODIFY))))
                .andExpect(status().isNotFound());
        mvc.perform(put(path(target)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isNotFound());
        assertDefaults(target);
    }

    @Test
    void ordinaryAdministratorCannotModifySystemRootEvenInTheSameOrganization() throws Exception {
        peer.setSystemKey(UserSystemKey.SUPER_ADMINISTRATOR);
        users.saveAndFlush(peer);
        mvc.perform(put(path(peer)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isForbidden());
        assertDefaults(peer);
    }

    @Test
    void superAdministratorCanModifyAcrossOrganizationsWithinTheTenant() throws Exception {
        administrator.setSystemKey(UserSystemKey.SUPER_ADMINISTRATOR);
        users.saveAndFlush(administrator);
        User target = account(sibling);
        mvc.perform(put(path(target)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isOk());
        assertThat(preferences.get(target.getTenantId(), target.getId())).isEqualTo(PREFERENCES);
    }

    @Test
    void deletedAccountsCannotBeModified() throws Exception {
        peer.setStatus(UserStatus.DELETED);
        users.saveAndFlush(peer);
        mvc.perform(put(path(peer)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isBadRequest());
        assertDefaults(peer);
    }

    @Test
    void unknownAccountsReturnNotFound() throws Exception {
        String path = "/admin/users/" + UUID.randomUUID() + "/email-notifications";
        mvc.perform(get(path).with(authentication(as(administrator, VIEW))))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"workOrderCreated\":false}",
            "{\"workOrderCreated\":\"invalid\"}",
            "{\"workOrderCreated\":null,\"workOrderSlaBreached\":true,\"workOrderAssigned\":true,\"dailyOutstandingReport\":true}"})
    void incompleteOrMalformedRequestsDoNotChangeSubscriptions(String request) throws Exception {
        mvc.perform(put(path(peer)).with(authentication(as(administrator, VIEW, MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertDefaults(peer);
    }

    @Test
    void unauthenticatedOrNonTenantPrincipalsCannotUseManagementEndpoints() throws Exception {
        mvc.perform(get(path(peer))).andExpect(status().isUnauthorized());
        mvc.perform(put(path(peer)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path(peer)).with(user("non-tenant").authorities(
                                new SimpleGrantedAuthority(VIEW), new SimpleGrantedAuthority(MODIFY))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SELECTED))
                .andExpect(status().isForbidden());
        assertDefaults(peer);
    }

    private void assertDefaults(User target) {
        assertThat(preferences.get(target.getTenantId(), target.getId())).isEqualTo(DEFAULTS);
    }

    private String path(User target) {
        return "/admin/users/" + target.getId() + "/email-notifications";
    }

    private Tenant tenant() {
        Tenant created = new Tenant();
        created.setCode("mail-" + UUID.randomUUID());
        created.setName("邮件通知偏好测试租户");
        return tenants.saveAndFlush(created);
    }

    private OrgUnit organization(Tenant owner, OrgUnit parent) {
        OrgUnit created = new OrgUnit();
        created.setTenantId(owner.getId());
        created.setParentId(parent == null ? null : parent.getId());
        created.setCode("mail-" + UUID.randomUUID());
        created.setName("邮件通知偏好测试组织");
        created.setUnitType(parent == null ? OrgUnitType.CITY : OrgUnitType.COUNTY);
        created.setPath((parent == null ? "/" : parent.getPath()) + created.getCode() + "/");
        return organizations.saveAndFlush(created);
    }

    private User account(OrgUnit organization) {
        User created = new User();
        String identifier = UUID.randomUUID().toString();
        created.setTenantId(organization.getTenantId());
        created.setOrgUnitId(organization.getId());
        created.setUsername("mail-" + identifier);
        created.setName("订阅测试用户");
        created.setPhone(Long.toString(NEXT_PHONE.getAndIncrement()));
        created.setEmail(identifier + "@example.test");
        created.setPassword("encoded-test-password");
        created.setStatus(UserStatus.ACTIVE);
        created.setAuthorizationVersion(1L);
        return users.saveAndFlush(created);
    }

    private UsernamePasswordAuthenticationToken as(User operator, String... permissions) {
        var grants = Arrays.stream(permissions).map(permission -> new UserAuthorizationSnapshot.EffectivePermissionGrant(
                UUID.randomUUID(), permission, false, null, null)).toList();
        var snapshot = new UserAuthorizationSnapshot(operator.getId(), operator.getTenantId(), operator.getOrgUnitId(),
                operator.getAuthorizationVersion(), operator.getUsername(), operator.getSystemKey(), operator.getStatus(), grants);
        var authorities = snapshot.authorities().stream().map(SimpleGrantedAuthority::new).toList();
        var principal = new TenantUserDetails(operator.getId(), operator.getTenantId(), operator.getOrgUnitId(),
                operator.getAuthorizationVersion(), operator.getUsername(), "not-used", true, authorities, snapshot);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
