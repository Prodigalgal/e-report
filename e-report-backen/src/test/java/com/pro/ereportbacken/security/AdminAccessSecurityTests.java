package com.pro.ereportbacken.security;

import jakarta.servlet.DispatcherType;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.service.DefaultTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminAccessSecurityTests {

    private static final String EXPORT_TEST_USERNAME = "work-order-security-exporter";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultTenantService defaultTenantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void authorizesOnlyServerSideAsyncRedispatchWithoutCredentials() throws Exception {
        mockMvc.perform(get("/security-async-dispatch-probe"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/security-async-dispatch-probe")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ASYNC);
                            return request;
                        }))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowsDedicatedWorkOrderExporterWithoutViewPermission() throws Exception {
        String exportPermission = PermissionCatalog.exportPermissions().iterator().next();

        mockMvc.perform(get("/work-orders/export")
                        .with(authentication(exporterAuthentication(exportPermission))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void rejectsWorkOrderViewerWithoutDedicatedExportPermission() throws Exception {
        String viewPermission = PermissionCatalog.viewPermissions().iterator().next();

        mockMvc.perform(get("/work-orders/export")
                        .with(user("work-order-viewer")
                                .authorities(new SimpleGrantedAuthority(viewPermission))))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiTokenEndpointsRequireTheirDedicatedAtomicPermissions() throws Exception {
        mockMvc.perform(get("/api-tokens").with(user("ordinary-user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api-tokens/options").with(user("ordinary-user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api-tokens")
                        .with(user("ordinary-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"未授权 Token\",\"permissionNames\":[\"work-order:network-fault:view\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api-tokens/00000000-0000-0000-0000-000000000001")
                        .with(user("ordinary-user"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiTokenViewAndRevokePermissionsReachTheirOwnEndpoints() throws Exception {
        mockMvc.perform(get("/api-tokens")
                        .with(authentication(exporterAuthentication(PermissionCatalog.API_TOKEN_VIEW))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api-tokens/00000000-0000-0000-0000-000000000001")
                        .with(authentication(exporterAuthentication(PermissionCatalog.API_TOKEN_REVOKE)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private UsernamePasswordAuthenticationToken exporterAuthentication(String exportPermission) {
        User exporter = userRepository.findByUsername(EXPORT_TEST_USERNAME)
                .orElseGet(() -> {
                    var tenant = defaultTenantService.requireDefaultTenant();
                    User created = new User(EXPORT_TEST_USERNAME, "not-used", "工单导出权限测试用户");
                    created.setTenantId(tenant.getId());
                    created.setOrgUnitId(tenant.getRootOrgUnitId());
                    created.setPhone("19900000001");
                    created.setEmail("work-order-security-exporter@example.invalid");
                    created.setStatus(UserStatus.ACTIVE);
                    return userRepository.saveAndFlush(created);
                });
        UserAuthorizationSnapshot snapshot = new UserAuthorizationSnapshot(
                exporter.getId(),
                exporter.getTenantId(),
                exporter.getOrgUnitId(),
                exporter.getAuthorizationVersion(),
                exporter.getUsername(),
                exporter.getSystemKey(),
                exporter.getStatus(),
                List.of(new UserAuthorizationSnapshot.EffectivePermissionGrant(
                        UUID.randomUUID(),
                        exportPermission,
                        true,
                        null,
                        null)));
        List<SimpleGrantedAuthority> authorities = snapshot.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        TenantUserDetails principal = new TenantUserDetails(
                snapshot.membershipId(),
                snapshot.tenantId(),
                snapshot.orgUnitId(),
                snapshot.authorizationVersion(),
                snapshot.username(),
                "not-used",
                true,
                authorities,
                snapshot);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Test
    void allowsSpecializedManagerWithItsAtomicEndpointPermission() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("user-manager")
                        .authorities(new SimpleGrantedAuthority(PermissionCatalog.USER_MANAGE_VIEW))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAuthenticatedUserWithoutEndpointPermission() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("restricted-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsManagerWithEndpointPermission() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("administrator")
                        .authorities(new SimpleGrantedAuthority(PermissionCatalog.USER_MANAGE_VIEW))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsBulkUserExportWithoutDedicatedPermission() throws Exception {
        mockMvc.perform(post("/admin/user-transfers/exports").with(user("user-viewer")
                        .authorities(
                                new SimpleGrantedAuthority(PermissionCatalog.USER_MANAGE_VIEW)
                        ))
                        .header("Idempotency-Key", "security-test-export")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotExposeE2eIsolationCapabilityInDefaultProfile() throws Exception {
        mockMvc.perform(get("/admin/e2e-isolation").with(user("administrator")
                        .authorities(new SimpleGrantedAuthority(PermissionCatalog.OBSERVABILITY_VIEW))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsReleasePolicyUpdateWithoutDedicatedPermission() throws Exception {
        mockMvc.perform(patch("/admin/app-updates/00000000-0000-0000-0000-000000000040/policy")
                        .with(user("release-viewer").authorities(
                                new SimpleGrantedAuthority(PermissionCatalog.APP_UPDATE_VIEW)
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceUpdate\":true,\"minSupportedVersionCode\":37,\"releaseNotes\":\"说明\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsReleasePolicyManagerToReachEndpoint() throws Exception {
        mockMvc.perform(patch("/admin/app-updates/00000000-0000-0000-0000-000000000040/policy")
                        .with(user("release-policy-manager").authorities(
                                new SimpleGrantedAuthority(PermissionCatalog.APP_UPDATE_POLICY_UPDATE)
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceUpdate\":true,\"minSupportedVersionCode\":37,\"releaseNotes\":\"说明\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otaProtocolIsPublicWhilePublishingAndHistoryKeepTheirDedicatedPermissions() throws Exception {
        mockMvc.perform(get("/app-updates/ota")
                        .header("expo-protocol-version", "1").header("expo-platform", "android")
                        .header("expo-runtime-version", "android-6.8.0-native5008000"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/admin/app-updates/ota")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/app-updates/ota").with(user("ordinary-user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/app-updates/ota").with(user("release-viewer")
                        .authorities(new SimpleGrantedAuthority(PermissionCatalog.APP_UPDATE_VIEW))))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/admin/app-updates/ota")
                        .file(new MockMultipartFile("archive", "ota.zip", "application/zip", new byte[]{1}))
                        .with(user("release-viewer").authorities(new SimpleGrantedAuthority(PermissionCatalog.APP_UPDATE_VIEW)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/app-updates/ota")
                        .file(new MockMultipartFile("archive", "ota.zip", "application/zip", new byte[]{1}))
                        .with(user("release-publisher").authorities(new SimpleGrantedAuthority(PermissionCatalog.APP_UPDATE_PUBLISH)))
                        .cookie(new jakarta.servlet.http.Cookie(com.pro.ereportbacken.service.AuthSessionService.WEB_COOKIE_NAME, "test-cookie"))
                        .header("X-Client-Platform", "WEB").header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }
}
