package com.pro.ereportbacken.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.MailSenderAccountUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderSettingsUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import org.springframework.core.env.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.pro.ereportbacken.config.EmailConstant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MailSenderConfigurationServiceTests {

    private final UUID tenantId = uuid(1);
    private final UUID countyId = uuid(2);
    private final AppConfigService appConfigService = mock(AppConfigService.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final ConfigurationScopeContext configurationScopeContext = mock(ConfigurationScopeContext.class);
    private final OrganizationHierarchyService hierarchyService = mock(OrganizationHierarchyService.class);
    private final Environment environment = mock(Environment.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @TempDir
    private Path tempDirectory;
    private final MailSenderConfigurationService service = new MailSenderConfigurationService(
            appConfigService, tenantContext, configurationScopeContext, hierarchyService, objectMapper, environment, new MailSenderPoolWriter(appConfigService));

    MailSenderConfigurationServiceTests() {
        when(tenantContext.currentTenantIdOrDefault()).thenReturn(tenantId);
        when(hierarchyService.namesById(eq(tenantId), anyCollection())).thenReturn(Map.of(countyId, "南靖县"));
    }

    @Test
    void countyCompleteLegacyConfigurationLoadsAsSingleMaskedSender() throws Exception {
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId)))
                .thenReturn(senderConfigs("county-secret", "smtp.county.example"));

        var settings = service.settings(countyId);

        assertThat(settings.configured()).isTrue();
        assertThat(settings.localOverride()).isTrue();
        assertThat(settings.inherited()).isFalse();
        assertThat(settings.senders()).hasSize(1);
        assertThat(settings.senders().getFirst().host()).isEqualTo("smtp.county.example");
        assertThat(settings.senders().getFirst().passwordConfigured()).isTrue();
        assertThat(settings.sourceOrgUnitName()).isEqualTo("南靖县");
        assertThat(objectMapper.writeValueAsString(settings)).doesNotContain("county-secret");
    }

    @Test
    void countyInheritsCityPool() throws Exception {
        String cityPool = "[{\"id\":\"" + uuid(30) + "\",\"host\":\"smtp.city.example\","
                + "\"port\":465,\"username\":\"city\",\"password\":\"city-secret\","
                + "\"fromAddress\":\"city@example.com\",\"smtpAuth\":true,"
                + "\"sslEnabled\":true,\"startTlsEnabled\":false}]";
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenReturn(Map.of());
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull()))
                .thenReturn(Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, cityPool)));

        var settings = service.settings(countyId);

        assertThat(settings.configured()).isTrue();
        assertThat(settings.inherited()).isTrue();
        assertThat(settings.localOverride()).isFalse();
        assertThat(settings.sourceScope()).isEqualTo("TENANT_OVERRIDE");
        assertThat(settings.senders()).hasSize(1);
        assertThat(settings.senders().getFirst().host()).isEqualTo("smtp.city.example");
        assertThat(objectMapper.writeValueAsString(settings)).doesNotContain("city-secret");
    }

    @Test
    void oauthClientSecretAndRefreshTokenAreNeverReturned() throws Exception {
        String oauthPool = "[{\"id\":\"" + uuid(31) + "\",\"providerId\":\"GMAIL\","
                + "\"host\":\"smtp.gmail.com\",\"port\":587,\"username\":\"sender@gmail.com\","
                + "\"fromAddress\":\"sender@gmail.com\",\"smtpAuth\":true,\"sslEnabled\":false,"
                + "\"startTlsEnabled\":true,\"authenticationMode\":\"OAUTH2\","
                + "\"authMechanism\":\"XOAUTH2\",\"oauthClientId\":\"client-id\","
                + "\"oauthClientSecret\":\"oauth-secret-value\",\"oauthRefreshToken\":\"refresh-token-value\"}]";
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenReturn(Map.of());
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull()))
                .thenReturn(Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, oauthPool)));

        var settings = service.settings(countyId);
        String response = objectMapper.writeValueAsString(settings);

        assertThat(settings.configured()).isTrue();
        assertThat(settings.senders().getFirst().oauthClientSecretConfigured()).isTrue();
        assertThat(settings.senders().getFirst().oauthRefreshTokenConfigured()).isTrue();
        assertThat(response).contains("client-id").doesNotContain("oauth-secret-value", "refresh-token-value");
    }

    @Test
    void microsoftOAuthConfigurationResolvesCanonicalScopeForStoredAndNewSenders() {
        UUID senderId = uuid(33);
        String pool = "[{\"id\":\"" + senderId + "\",\"providerId\":\"MICROSOFT_365\","
                + "\"host\":\"smtp.office365.com\",\"port\":587,\"username\":\"sender@example.com\","
                + "\"fromAddress\":\"sender@example.com\",\"smtpAuth\":true,\"sslEnabled\":false,"
                + "\"startTlsEnabled\":true,\"authenticationMode\":\"OAUTH2\","
                + "\"authMechanism\":\"XOAUTH2\",\"oauthClientId\":\"client\","
                + "\"oauthClientSecret\":\"secret\",\"oauthRefreshToken\":\"refresh\","
                + "\"oauthScope\":\"https://outlook.office365.com/SMTP.Send offline_access\"}]";
        AtomicReference<String> currentPool = new AtomicReference<>(pool);
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenAnswer(invocation ->
                Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, currentPool.get())));
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenReturn(Map.of());
        when(appConfigService.saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), anyString(), anyString(), eq(countyId)))
                .thenAnswer(invocation -> {
                    currentPool.set(invocation.getArgument(1));
                    return null;
                });

        var settings = service.settings(countyId);
        assertThat(settings.senders().getFirst().oauthScope())
                .isEqualTo("https://outlook.office.com/SMTP.Send offline_access");

        var request = new MailSenderAccountUpdateRequestDTO(
                uuid(34), "smtp.office365.com", 587, "new@example.com", null, "new@example.com",
                true, false, true, "MICROSOFT_365", "OAUTH2", "XOAUTH2", true,
                null, null, null, "TLSv1.2 TLSv1.3", 10_000, 30_000, 30_000,
                "new-client", "new-secret", "new-refresh", "common", null);
        service.update(updateRequest(List.of(request)), countyId);

        assertThat(currentPool.get())
                .contains("\"oauthScope\":\"https://outlook.office.com/SMTP.Send offline_access\"")
                .doesNotContain("outlook.office365.com/SMTP.Send");
    }

    @Test
    void partialLegacyConfigurationDoesNotMixWithCityFields() {
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId)))
                .thenReturn(Map.of(MAIL_HOST_KEY, config(MAIL_HOST_KEY, "smtp.partial.example")));

        var settings = service.settings(countyId);

        assertThat(settings.configured()).isFalse();
        assertThat(settings.localOverride()).isTrue();
        assertThat(settings.configurationError()).contains("发件方1配置不完整");
    }

    @Test
    void blankPasswordPreservesExistingLegacySecretWhenPoolIsSaved() throws Exception {
        Map<String, AppConfig> local = senderConfigs("existing-secret", "smtp.old.example");
        String savedPool = "[{\"id\":\"" + serviceId() + "\",\"host\":\"smtp.new.example\","
                + "\"port\":465,\"username\":\"new-user\",\"password\":\"existing-secret\","
                + "\"fromAddress\":\"new@example.com\",\"smtpAuth\":true,"
                + "\"sslEnabled\":true,\"startTlsEnabled\":false}]";
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId)))
                .thenReturn(local, local, Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, savedPool)));
        var existing = service.settings(countyId).senders().getFirst();

        var updated = service.update(updateRequest(List.of(
                senderRequest(existing.id(), "smtp.new.example", "new@example.com", ""))), countyId);

        assertThat(updated.senders()).hasSize(1);
        assertThat(updated.senders().getFirst().host()).isEqualTo("smtp.new.example");
        verify(appConfigService).saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), argThat(value -> value.contains("existing-secret")
                        && value.contains("smtp.new.example")), eq("按配置作用域轮询的SMTP邮件发件池"), eq(countyId));
                verify(appConfigService).deleteLocalScopedConfigs(
                argThat(keys -> keys.contains(MAIL_PASSWORD_KEY) && !keys.contains(MAIL_SENDER_POOL_KEY)), eq(countyId));
    }

    @Test
    void blankOptionalTransportFieldsCanBeClearedWhilePasswordIsPreserved() {
        UUID senderId = uuid(32);
        String originalPool = "[{\"id\":\"" + senderId + "\",\"providerId\":\"CUSTOM\","
                + "\"host\":\"smtp.example.com\",\"port\":465,\"username\":\"sender\","
                + "\"password\":\"existing-secret\",\"fromAddress\":\"sender@example.com\","
                + "\"smtpAuth\":true,\"sslEnabled\":true,\"startTlsEnabled\":false,"
                + "\"authenticationMode\":\"PASSWORD\",\"authMechanism\":\"DIGEST_MD5\","
                + "\"ehloDomain\":\"mail.example.com\",\"authDomain\":\"CORP\","
                + "\"saslRealm\":\"smtp-realm\"}]";
        AtomicReference<String> currentPool = new AtomicReference<>(originalPool);
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenAnswer(invocation ->
                Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, currentPool.get())));
        when(appConfigService.saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), anyString(), anyString(), eq(countyId)))
                .thenAnswer(invocation -> {
                    currentPool.set(invocation.getArgument(1));
                    return null;
                });
        var request = new MailSenderAccountUpdateRequestDTO(
                senderId, "smtp.example.com", 465, "sender", "", "sender@example.com",
                true, true, false, "CUSTOM", "PASSWORD", "DIGEST_MD5", false,
                "", "", "", "", null, null, null, null, null, null, null, null);

        service.update(updateRequest(List.of(request)), countyId);

        assertThat(currentPool.get())
                .contains("\"password\":\"existing-secret\"")
                .contains("\"ehloDomain\":null")
                .contains("\"authDomain\":null")
                .contains("\"saslRealm\":null")
                .contains("\"sslProtocols\":\"TLSv1.2 TLSv1.3\"");
    }

    @Test
    void acceptsRepeatedFromAddressesAndRejectsNewSendersWithoutPassword() {
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenReturn(Map.of());
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenReturn(Map.of());
        var sender = senderRequest(null, "shared@example.com", "secret");

        service.update(updateRequest(List.of(sender, sender)), countyId);
        verify(appConfigService).saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), anyString(), anyString(), eq(countyId));

        assertThatThrownBy(() -> service.update(
                updateRequest(List.of(senderRequest(null, "new@example.com", ""))), countyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须填写邮件密码");
    }

    @Test
    void persistsAndReloadsMoreThanTenSenders() {
        AtomicReference<String> persistedPool = new AtomicReference<>();
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenAnswer(invocation -> {
            String pool = persistedPool.get();
            return pool == null ? Map.of() : Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, pool));
        });
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenReturn(Map.of());
        when(appConfigService.saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), anyString(), anyString(), eq(countyId)))
                .thenAnswer(invocation -> {
                    persistedPool.set(invocation.getArgument(1));
                    return null;
                });

        List<MailSenderAccountUpdateRequestDTO> senders = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> senderRequest(uuid(100 + index), "shared@example.com", "secret-" + index))
                .toList();

        var settings = service.update(updateRequest(senders), countyId);

        assertThat(settings.senders()).hasSize(12);
        assertThat(settings.senders()).extracting(sender -> sender.fromAddress())
                .containsOnly("shared@example.com");
        assertThat(persistedPool.get()).isNotBlank();
    }

    @Test
    void unauthenticatedSmtpRelayDoesNotRequireUsernameOrPassword() {
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenReturn(Map.of());
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenReturn(Map.of());
        var relay = new MailSenderAccountUpdateRequestDTO(
                uuid(120), "relay.example.com", 25, null, null, "relay@example.com", false, false, false,
                "CUSTOM", "PASSWORD", "AUTO", false, null, null, null, "TLSv1.2 TLSv1.3",
                10_000, 30_000, 30_000, null, null, null, null, null);

        service.update(updateRequest(List.of(relay)), countyId);

        verify(appConfigService).saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), argThat(value -> value.contains("relay@example.com")
                && value.contains("\"smtpAuth\":false")), anyString(), eq(countyId));
    }

    @Test
    void persistsOAuthRefreshTokenRotationOnlyWhenStoredTokenStillMatches() {
        UUID senderId = uuid(130);
        String pool = "[{\"id\":\"" + senderId + "\",\"providerId\":\"MICROSOFT_365\","
                + "\"host\":\"smtp.office365.com\",\"port\":587,\"username\":\"sender@example.com\","
                + "\"fromAddress\":\"sender@example.com\",\"smtpAuth\":true,\"sslEnabled\":false,"
                + "\"startTlsEnabled\":true,\"authenticationMode\":\"OAUTH2\","
                + "\"oauthClientId\":\"client\",\"oauthClientSecret\":\"secret\","
                + "\"oauthRefreshToken\":\"old-refresh\"}]";
        AtomicReference<String> currentPool = new AtomicReference<>(pool);
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenAnswer(invocation ->
                Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, currentPool.get())));
        when(appConfigService.saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), anyString(), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    currentPool.set(invocation.getArgument(1));
                    return null;
                });

        boolean updated = service.persistRotatedOAuthRefreshToken(
                null, senderId, "old-refresh", "new-refresh");
        boolean stale = service.persistRotatedOAuthRefreshToken(
                null, senderId, "old-refresh", "stale-refresh");

        assertThat(updated).isTrue();
        assertThat(stale).isFalse();
        verify(appConfigService).saveOrUpdateScopedConfig(
                eq(MAIL_SENDER_POOL_KEY), argThat(value -> value.contains("new-refresh")),
                eq("按配置作用域轮询的SMTP邮件发件池"), isNull());
    }

    @Test
    void noCountyOrCityConfigurationIsExplicitlyUnconfigured() {
        when(appConfigService.findLocalScopedConfigs(anyCollection(), eq(countyId))).thenReturn(Map.of());
        when(appConfigService.findLocalScopedConfigs(anyCollection(), isNull())).thenReturn(Map.of());

        var settings = service.settings(countyId);

        assertThat(settings.configured()).isFalse();
        assertThat(settings.sourceScope()).isEqualTo("UNCONFIGURED");
        assertThat(settings.senders()).isEmpty();
        assertThat(settings.configurationError()).contains("市公司也没有可继承");
    }

    @Test
    void runtimeDefaultReloadsAndNeverReturnsCredentials() throws Exception {
        var runtime = runtimeService(pool(uuid(140), "smtp.runtime.example", "first@example.com", "runtime-secret"));
        var settings = runtime.settings(countyId);
        assertThat(settings.strategy()).isEqualTo(MailSenderPoolStrategy.INHERIT);
        assertThat(settings.managedExternally()).isTrue();
        assertThat(settings.localSenders()).isEmpty();
        assertThat(settings.inheritedSenders()).hasSize(1);
        assertThat(objectMapper.writeValueAsString(settings)).doesNotContain("runtime-secret");
        Files.writeString(tempDirectory.resolve("senders.json"),
                pool(uuid(140), "smtp.runtime.example", "changed@example.com", "changed-secret"));
        assertThat(runtime.settings(countyId).senders().getFirst().fromAddress()).isEqualTo("changed@example.com");
    }

    @Test
    void addingStrategiesDoesNotActivateAccountsCurrentlyOverriddenByRuntimeDefault() throws Exception {
        var store = memoryConfigs();
        var local = poolConfigs(uuid(145), "smtp.inactive.example", "inactive@example.com", "inactive-secret", MailSenderPoolStrategy.REPLACE);
        local.remove(MAIL_SENDER_STRATEGY_KEY);
        store.put(countyId, local);
        var runtime = runtimeService(pool(uuid(146), "smtp.runtime.example", "runtime@example.com", "runtime-secret"));
        var settings = runtime.settings(countyId);
        assertThat(settings.strategy()).isEqualTo(MailSenderPoolStrategy.INHERIT);
        assertThat(settings.localSenders()).hasSize(1);
        assertThat(settings.senders()).extracting(sender -> sender.fromAddress()).containsExactly("runtime@example.com");
    }

    @Test
    void adminCanSaveLocalPoolAndRevokeItWhileRuntimeDefaultIsConfigured() throws Exception {
        var store = memoryConfigs();
        String runtimePool = pool(uuid(140), "smtp.runtime.example", "runtime@example.com", "runtime-secret");
        var runtime = runtimeService(runtimePool);
        var saved = runtime.update(updateRequest(List.of(senderRequest(null, "local@example.com", "local-secret"))), countyId);
        assertThat(saved.strategy()).isEqualTo(MailSenderPoolStrategy.REPLACE);
        assertThat(saved.localOverride()).isTrue();
        assertThat(saved.localSenders()).hasSize(1);
        assertThat(saved.senders()).extracting(sender -> sender.fromAddress()).containsExactly("local@example.com");
        assertThat(Files.readString(tempDirectory.resolve("senders.json"))).isEqualTo(runtimePool);
        assertThat(store.get(countyId)).containsKeys(MAIL_SENDER_POOL_KEY, MAIL_SENDER_STRATEGY_KEY);
        var restored = runtime.deleteLocal(countyId);
        assertThat(restored.localOverride()).isFalse();
        assertThat(restored.senders()).extracting(sender -> sender.fromAddress()).containsExactly("runtime@example.com");
    }

    @Test
    void mergeUsesDistinctAccountsAndPrefersLocalCredentialsForTheSameAccount() throws Exception {
        var store = memoryConfigs();
        store.put(null, poolConfigs(uuid(141), "smtp.same.example", "parent@example.com", "parent-secret", MailSenderPoolStrategy.REPLACE));
        store.put(countyId, poolConfigs(uuid(142), "smtp.same.example", "local@example.com", "local-secret", MailSenderPoolStrategy.MERGE));
        when(configurationScopeContext.currentOrgUnitIdOrNull()).thenReturn(countyId);
        var effective = service.requireCurrent();
        assertThat(effective.senders()).hasSize(1);
        assertThat(effective.senders().getFirst().password()).isEqualTo("local-secret");
        assertThat(effective.senders().getFirst().origin().orgUnitId()).isEqualTo(countyId);
        store.put(countyId, poolConfigs(uuid(142), "smtp.local.example", "local@example.com", "local-secret", MailSenderPoolStrategy.MERGE));
        assertThat(service.settings(countyId).senders()).hasSize(2);
    }

    @Test
    void childInheritsTheParentsEffectiveMergedPoolAcrossOrganizationLevels() throws Exception {
        var store = memoryConfigs();
        UUID parentId = uuid(150);
        store.put(null, poolConfigs(uuid(151), "smtp.tenant.example", "tenant@example.com", "tenant-secret", MailSenderPoolStrategy.REPLACE));
        store.put(parentId, poolConfigs(uuid(152), "smtp.parent.example", "parent@example.com", "parent-secret", MailSenderPoolStrategy.MERGE));
        when(hierarchyService.parentId(tenantId, countyId)).thenReturn(parentId);
        var settings = service.settings(countyId);
        assertThat(settings.strategy()).isEqualTo(MailSenderPoolStrategy.INHERIT);
        assertThat(settings.localSenders()).isEmpty();
        assertThat(settings.senders()).extracting(sender -> sender.fromAddress())
                .containsExactly("tenant@example.com", "parent@example.com");
        assertThat(settings.senders().getLast().sourceOrgUnitId()).isEqualTo(parentId);
    }

    @Test
    void inheritModePreservesInactiveLocalAccountsAndEmptyMergeIsRejected() throws Exception {
        memoryConfigs();
        service.update(updateRequest(List.of(senderRequest(uuid(160), "local@example.com", "local-secret"))), countyId);
        var inherited = service.update(new MailSenderSettingsUpdateRequestDTO(
                List.of(senderRequest(uuid(160), "local@example.com", "")), MailSenderPoolStrategy.INHERIT), countyId);
        assertThat(inherited.localSenders()).hasSize(1);
        assertThat(inherited.localSenders().getFirst().passwordConfigured()).isTrue();
        assertThat(inherited.senders()).isEmpty();
        assertThatThrownBy(() -> service.update(new MailSenderSettingsUpdateRequestDTO(
                List.of(), MailSenderPoolStrategy.MERGE), countyId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.update(new MailSenderSettingsUpdateRequestDTO(
                List.of(), MailSenderPoolStrategy.INHERIT), countyId).localSenders()).isEmpty();
    }

    @Test
    void localPoolWorksWhenParentRuntimeFileIsUnavailable() throws Exception {
        var store = memoryConfigs();
        var runtime = runtimeService(pool(uuid(170), "smtp.runtime.example", "runtime@example.com", "runtime-secret"));
        store.put(countyId, poolConfigs(uuid(171), "smtp.local.example", "local@example.com", "local-secret", MailSenderPoolStrategy.MERGE));
        Files.delete(tempDirectory.resolve("senders.json"));
        var settings = runtime.settings(countyId);
        assertThat(settings.configured()).isTrue();
        assertThat(settings.senders()).hasSize(1);
        assertThat(settings.inheritedConfigurationError()).contains("无法读取");
    }

    @Test
    void inheritedCredentialsCannotBeCopiedOrRedirectedThroughLocalDrafts() throws Exception {
        memoryConfigs();
        UUID senderId = uuid(180);
        var runtime = runtimeService(pool(senderId, "smtp.runtime.example", "parent@example.com", "parent-secret"));
        assertThat(runtime.resolveInherited(senderId, countyId).host()).isEqualTo("smtp.runtime.example");
        assertThatThrownBy(() -> runtime.resolveInherited(uuid(181), countyId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.resolveDraft(senderRequest(senderId,
                "smtp.other.example", "parent@example.com", ""), countyId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("密码");
        assertThatThrownBy(() -> runtime.update(updateRequest(List.of(senderRequest(senderId,
                "smtp.other.example", "parent@example.com", ""))), countyId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("密码");
    }

    private MailSenderConfigurationService runtimeService(String contents) throws Exception {
        Path secretFile = tempDirectory.resolve("senders.json");
        Files.writeString(secretFile, contents, StandardCharsets.UTF_8);
        Environment runtimeEnvironment = mock(Environment.class);
        when(runtimeEnvironment.getProperty("MAIL_SENDER_POOL_FILE")).thenReturn(secretFile.toString());
        return new MailSenderConfigurationService(appConfigService, tenantContext, configurationScopeContext,
                hierarchyService, objectMapper, runtimeEnvironment, new MailSenderPoolWriter(appConfigService));
    }

    private String pool(UUID id, String host, String from, String password) {
        var array = objectMapper.createArrayNode();
        array.addObject().put("id", id.toString()).put("host", host).put("port", 465)
                .put("username", "sender").put("password", password).put("fromAddress", from)
                .put("smtpAuth", true).put("sslEnabled", true).put("startTlsEnabled", false);
        return array.toString();
    }

    private Map<String, AppConfig> poolConfigs(UUID id, String host, String from, String password, MailSenderPoolStrategy strategy) {
        return new LinkedHashMap<>(Map.of(MAIL_SENDER_POOL_KEY, config(MAIL_SENDER_POOL_KEY, pool(id, host, from, password)),
                MAIL_SENDER_STRATEGY_KEY, config(MAIL_SENDER_STRATEGY_KEY, strategy.name())));
    }

    private Map<UUID, Map<String, AppConfig>> memoryConfigs() {
        Map<UUID, Map<String, AppConfig>> store = new LinkedHashMap<>();
        when(appConfigService.findLocalScopedConfigs(anyCollection(), nullable(UUID.class)))
                .thenAnswer(invocation -> Map.copyOf(store.getOrDefault(invocation.getArgument(1), Map.of())));
        when(appConfigService.saveOrUpdateScopedConfig(anyString(), anyString(), anyString(), nullable(UUID.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    store.computeIfAbsent(invocation.getArgument(3), ignored -> new LinkedHashMap<>())
                            .put(key, config(key, invocation.getArgument(1)));
                    return null;
                });
        when(appConfigService.deleteLocalScopedConfigs(anyCollection(), nullable(UUID.class)))
                .thenAnswer(invocation -> {
                    Map<String, AppConfig> local = store.get(invocation.getArgument(1));
                    if (local == null) return 0;
                    int before = local.size();
                    ((java.util.Collection<?>) invocation.getArgument(0)).forEach(local::remove);
                    return before - local.size();
                });
        return store;
    }

    private Map<String, AppConfig> senderConfigs(String password, String host) {
        Map<String, AppConfig> configs = new LinkedHashMap<>();
        configs.put(MAIL_HOST_KEY, config(MAIL_HOST_KEY, host));
        configs.put(MAIL_PORT_KEY, config(MAIL_PORT_KEY, "465"));
        configs.put(MAIL_USERNAME_KEY, config(MAIL_USERNAME_KEY, "sender@example.com"));
        configs.put(MAIL_PASSWORD_KEY, config(MAIL_PASSWORD_KEY, password));
        configs.put(MAIL_FROM_KEY, config(MAIL_FROM_KEY, "sender@example.com"));
        configs.put(MAIL_SMTP_AUTH_KEY, config(MAIL_SMTP_AUTH_KEY, "true"));
        configs.put(MAIL_SSL_ENABLE_KEY, config(MAIL_SSL_ENABLE_KEY, "true"));
        configs.put(MAIL_STARTTLS_ENABLE_KEY, config(MAIL_STARTTLS_ENABLE_KEY, "false"));
        return Map.copyOf(configs);
    }

    private MailSenderSettingsUpdateRequestDTO updateRequest(List<MailSenderAccountUpdateRequestDTO> senders) {
        return new MailSenderSettingsUpdateRequestDTO(senders, MailSenderPoolStrategy.REPLACE);
    }

    private MailSenderAccountUpdateRequestDTO senderRequest(UUID id, String from, String password) {
        return senderRequest(id, "smtp.example.com", from, password);
    }

    private MailSenderAccountUpdateRequestDTO senderRequest(UUID id, String host, String from, String password) {
        return new MailSenderAccountUpdateRequestDTO(
                id, host, 465, "sender", password, from, true, true, false,
                "CUSTOM", "PASSWORD", "AUTO", false, null, null, null, "TLSv1.2 TLSv1.3",
                10_000, 30_000, 30_000, null, null, null, null, null);
    }

    private String serviceId() {
        return UUID.nameUUIDFromBytes(
                ("legacy-mail-sender:" + tenantId + ":" + countyId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private AppConfig config(String key, String value) {
        return new AppConfig(key, value, "test");
    }

    private UUID uuid(long seed) {
        return com.pro.ereportbacken.TestIds.uuid(seed);
    }
}
