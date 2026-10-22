package com.pro.ereportbacken.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.ereportbacken.model.dto.MailSenderAccountDTO;
import com.pro.ereportbacken.model.dto.MailSenderAccountUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.MailSenderSettingsDTO;
import com.pro.ereportbacken.model.dto.MailSenderSettingsUpdateRequestDTO;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.service.AppConfigService;
import com.pro.ereportbacken.service.OrganizationHierarchyService;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pro.ereportbacken.config.EmailConstant.MAIL_FROM_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_HOST_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_PASSWORD_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_PORT_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_SENDER_CONFIG_KEYS;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_SENDER_POOL_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_SENDER_STRATEGY_KEY;
import static com.pro.ereportbacken.notification.MailSenderPoolWriter.POOL_DESCRIPTION;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_SMTP_AUTH_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_SSL_ENABLE_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_STARTTLS_ENABLE_KEY;
import static com.pro.ereportbacken.config.EmailConstant.MAIL_USERNAME_KEY;

@Service
@Log4j2
public class MailSenderConfigurationService {

    public static final String DEFAULT_SSL_PROTOCOLS = "TLSv1.2 TLSv1.3";
    public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 30_000;
    public static final int DEFAULT_WRITE_TIMEOUT_MS = 30_000;

    private static final TypeReference<List<StoredMailSenderAccount>> STORED_POOL_TYPE = new TypeReference<>() {
    };
    private static final Set<String> TLS_PROTOCOLS = Set.of("TLSv1.2", "TLSv1.3");

    private final AppConfigService appConfigService;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;
    private final OrganizationHierarchyService hierarchyService;
    private final ObjectMapper objectMapper;
    private final String externalSenderPoolFile;
    private final MailSenderPoolWriter poolWriter;

    public MailSenderConfigurationService(AppConfigService appConfigService,
                                          TenantContext tenantContext,
                                          ConfigurationScopeContext configurationScopeContext,
                                          OrganizationHierarchyService hierarchyService,
                                          ObjectMapper objectMapper,
                                          Environment environment,
                                          MailSenderPoolWriter poolWriter) {
        this.appConfigService = appConfigService;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
        this.hierarchyService = hierarchyService;
        this.objectMapper = objectMapper;
        this.externalSenderPoolFile = environment.getProperty("MAIL_SENDER_POOL_FILE");
        this.poolWriter = poolWriter;
    }

    public MailSenderSettingsDTO settings(UUID orgUnitId) {
        return resolve(orgUnitId).toDto();
    }

    public MailSenderConfiguration requireCurrent() {
        MailSenderConfiguration configuration = resolve(configurationScopeContext.currentOrgUnitIdOrNull());
        if (!configuration.configured()) {
            throw new IllegalStateException(configuration.configurationError());
        }
        return configuration;
    }

    public MailSenderSettingsDTO update(MailSenderSettingsUpdateRequestDTO request, UUID orgUnitId) {
        if (request == null || request.senders() == null) {
            throw new IllegalArgumentException("本级发件方列表不能为空。");
        }
        if (request.strategy() != MailSenderPoolStrategy.INHERIT && request.senders().isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个邮件发件方。");
        }

        Map<UUID, MailSenderAccountConfiguration> existingById = indexById(localSenders(
                appConfigService.findLocalScopedConfigs(MAIL_SENDER_CONFIG_KEYS, orgUnitId), origin(orgUnitId)));
        List<StoredMailSenderAccount> updatedSenders = new ArrayList<>(request.senders().size());
        Set<UUID> ids = new HashSet<>();
        for (MailSenderAccountUpdateRequestDTO senderRequest : request.senders()) {
            StoredMailSenderAccount sender = toStoredAccount(senderRequest, existingById);
            if (!ids.add(sender.id())) {
                throw new IllegalArgumentException("发件方标识不能重复。");
            }
            updatedSenders.add(sender);
        }

        String serializedPool = updatedSenders.isEmpty() ? null : serializePool(updatedSenders);
        poolWriter.replace(serializedPool, request.strategy(), orgUnitId);
        return resolve(orgUnitId).toDto();
    }

    public MailSenderAccountConfiguration resolveDraft(MailSenderAccountUpdateRequestDTO request, UUID orgUnitId) {
        Map<UUID, MailSenderAccountConfiguration> localById = indexById(localSenders(
                appConfigService.findLocalScopedConfigs(MAIL_SENDER_CONFIG_KEYS, orgUnitId), origin(orgUnitId)));
        return toConfiguration(toStoredAccount(request, localById), origin(orgUnitId));
    }

    public MailSenderAccountConfiguration resolveInherited(UUID senderId, UUID orgUnitId) {
        return resolve(orgUnitId).inheritedSenders().stream()
                .filter(sender -> sender.id().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前作用域没有可测试的该继承发件方。"));
    }

    @Transactional
    public boolean persistRotatedOAuthRefreshToken(UUID sourceOrgUnitId,
                                                  UUID senderId,
                                                  String expectedRefreshToken,
                                                  String rotatedRefreshToken) {
        if (senderId == null || !StringUtils.hasText(expectedRefreshToken)
                || !StringUtils.hasText(rotatedRefreshToken)
                || expectedRefreshToken.equals(rotatedRefreshToken)) {
            return false;
        }
        Map<String, AppConfig> configs = appConfigService.findLocalScopedConfigs(
                MAIL_SENDER_CONFIG_KEYS, sourceOrgUnitId);
        String rawPool = value(configs, MAIL_SENDER_POOL_KEY);
        if (!StringUtils.hasText(rawPool)) {
            return false;
        }

        List<StoredMailSenderAccount> storedSenders = parseStoredPool(rawPool);
        boolean updated = false;
        List<StoredMailSenderAccount> rotatedPool = new ArrayList<>(storedSenders.size());
        for (StoredMailSenderAccount sender : storedSenders) {
            if (senderId.equals(sender.id()) && expectedRefreshToken.equals(sender.oauthRefreshToken())) {
                rotatedPool.add(sender.withOAuthRefreshToken(rotatedRefreshToken));
                updated = true;
            } else {
                rotatedPool.add(sender);
            }
        }
        if (!updated) {
            return false;
        }
        appConfigService.saveOrUpdateScopedConfig(
                MAIL_SENDER_POOL_KEY, serializePool(rotatedPool), POOL_DESCRIPTION, sourceOrgUnitId);
        return true;
    }

    private Map<UUID, MailSenderAccountConfiguration> indexById(
            List<MailSenderAccountConfiguration> senders) {
        Map<UUID, MailSenderAccountConfiguration> accounts = new LinkedHashMap<>();
        senders.forEach(sender -> accounts.put(sender.id(), sender));
        return accounts;
    }

    public MailSenderSettingsDTO deleteLocal(UUID orgUnitId) {
        int deleted = appConfigService.deleteLocalScopedConfigs(MAIL_SENDER_CONFIG_KEYS, orgUnitId);
        if (deleted == 0) {
            throw new IllegalArgumentException("当前作用域没有可删除的发件方配置。");
        }
        return resolve(orgUnitId).toDto();
    }

    private StoredMailSenderAccount toStoredAccount(
            MailSenderAccountUpdateRequestDTO request,
            Map<UUID, MailSenderAccountConfiguration> existingById) {
        if (request == null) {
            throw new IllegalArgumentException("发件方配置不能为空。");
        }
        boolean sslEnabled = Boolean.TRUE.equals(request.sslEnabled());
        boolean startTlsEnabled = Boolean.TRUE.equals(request.startTlsEnabled());
        boolean smtpAuth = Boolean.TRUE.equals(request.smtpAuth());
        if (sslEnabled && startTlsEnabled) {
            throw new IllegalArgumentException("SSL直连与STARTTLS不能同时启用，请选择一种加密方式。");
        }
        if (Boolean.TRUE.equals(request.startTlsRequired()) && !startTlsEnabled) {
            throw new IllegalArgumentException("强制STARTTLS必须同时启用STARTTLS。");
        }

        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        MailSenderAccountConfiguration existing = existingById.get(id);
        String providerId = choice(request.providerId(), existing == null ? null : existing.providerId(),
                MailSenderProvider.CUSTOM.name(), MailSenderProvider.class, "邮箱服务商");
        String authenticationMode = choice(request.authenticationMode(),
                existing == null ? null : existing.authenticationMode(),
                MailSenderAuthenticationMode.PASSWORD.name(), MailSenderAuthenticationMode.class, "认证方式");
        MailSenderProvider provider = MailSenderProvider.valueOf(providerId);
        MailSenderAuthenticationMode authMode = MailSenderAuthenticationMode.valueOf(authenticationMode);
        String authMechanism = choice(request.authMechanism(), existing == null ? null : existing.authMechanism(),
                MailSenderAuthenticationMechanism.AUTO.name(),
                MailSenderAuthenticationMechanism.class, "SMTP认证机制");
        if (authMode == MailSenderAuthenticationMode.OAUTH2) {
            if (!provider.supportsOAuth2()) {
                throw new IllegalArgumentException("当前邮箱服务商尚未配置安全的OAuth2令牌端点。");
            }
            if (!smtpAuth) {
                throw new IllegalArgumentException("OAuth2认证必须启用SMTP认证。");
            }
            authMechanism = MailSenderAuthenticationMechanism.XOAUTH2.name();
        } else if (MailSenderAuthenticationMechanism.XOAUTH2.name().equals(authMechanism)) {
            throw new IllegalArgumentException("XOAUTH2必须使用OAuth2认证方式。");
        }

        String username = trimToNull(request.username());
        if (smtpAuth && !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("启用SMTP认证时必须填写用户名。");
        }
        String password = smtpAuth && authMode == MailSenderAuthenticationMode.PASSWORD
                ? firstText(request.password(), existing == null ? null : existing.password())
                : null;
        String oauthClientId = authMode == MailSenderAuthenticationMode.OAUTH2
                ? requestedOrExisting(request.oauthClientId(), existing == null ? null : existing.oauthClientId())
                : null;
        String oauthClientSecret = authMode == MailSenderAuthenticationMode.OAUTH2
                ? firstText(request.oauthClientSecret(), existing == null ? null : existing.oauthClientSecret())
                : null;
        String oauthRefreshToken = authMode == MailSenderAuthenticationMode.OAUTH2
                ? firstText(request.oauthRefreshToken(), existing == null ? null : existing.oauthRefreshToken())
                : null;
        String oauthTenantId = authMode == MailSenderAuthenticationMode.OAUTH2
                ? requestedOrExisting(request.oauthTenantId(), existing == null ? null : existing.oauthTenantId())
                : null;
        String oauthScope = authMode == MailSenderAuthenticationMode.OAUTH2
                ? provider.resolveOAuthScope(
                        requestedOrExisting(request.oauthScope(), existing == null ? null : existing.oauthScope()))
                : null;
        if (authMode == MailSenderAuthenticationMode.PASSWORD && smtpAuth && !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("首次配置该发件方时必须填写邮件密码或授权码。");
        }
        if (authMode == MailSenderAuthenticationMode.OAUTH2) {
            requireText(oauthClientId, "OAuth Client ID不能为空。");
            requireText(oauthClientSecret, "首次配置OAuth2时必须填写Client Secret。");
            requireText(oauthRefreshToken, "首次配置OAuth2时必须填写Refresh Token。");
            if (provider == MailSenderProvider.MICROSOFT_365) {
                oauthTenantId = StringUtils.hasText(oauthTenantId) ? oauthTenantId : "common";
                if (!oauthTenantId.matches("[A-Za-z0-9.-]{1,128}")) {
                    throw new IllegalArgumentException("Microsoft OAuth租户标识格式不正确。");
                }
            }
        }

        Integer connectionTimeoutMs = timeout(
                request.connectionTimeoutMs(), existing == null ? null : existing.connectionTimeoutMs(),
                DEFAULT_CONNECTION_TIMEOUT_MS, "连接");
        Integer readTimeoutMs = timeout(
                request.readTimeoutMs(), existing == null ? null : existing.readTimeoutMs(),
                DEFAULT_READ_TIMEOUT_MS, "读取");
        Integer writeTimeoutMs = timeout(
                request.writeTimeoutMs(), existing == null ? null : existing.writeTimeoutMs(),
                DEFAULT_WRITE_TIMEOUT_MS, "写入");
        String sslProtocols = tlsProtocols(
                requestedOrExisting(request.sslProtocols(), existing == null ? null : existing.sslProtocols()));
        boolean startTlsRequired = request.startTlsRequired() != null
                ? request.startTlsRequired()
                : existing == null
                        ? startTlsEnabled
                        : existing.startTlsRequired();
        if (startTlsRequired && !startTlsEnabled) {
            throw new IllegalArgumentException("强制STARTTLS必须同时启用STARTTLS。");
        }

        String ehloDomain = requestedOrExisting(request.ehloDomain(), existing == null ? null : existing.ehloDomain());
        String authDomain = requestedOrExisting(request.authDomain(), existing == null ? null : existing.authDomain());
        String saslRealm = requestedOrExisting(request.saslRealm(), existing == null ? null : existing.saslRealm());
        if (StringUtils.hasText(saslRealm)
                && (authMode != MailSenderAuthenticationMode.PASSWORD
                    || !MailSenderAuthenticationMechanism.DIGEST_MD5.name().equals(authMechanism))) {
            throw new IllegalArgumentException("SASL Realm仅适用于DIGEST-MD5认证机制。");
        }
        String host = trimToNull(request.host());
        String fromAddress = trimToNull(request.fromAddress());
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("邮件服务器地址不能为空。");
        }
        if (host.chars().anyMatch(character -> Character.isWhitespace(character)
                || character == '/' || character == '\\' || character == '@'
                || character == '?' || character == '#')) {
            throw new IllegalArgumentException("邮件服务器地址不能包含URL路径或特殊字符。");
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65_535) {
            throw new IllegalArgumentException("邮件服务器端口必须在1至65535之间。");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new IllegalArgumentException("发件地址不能为空。");
        }

        return new StoredMailSenderAccount(
                id, providerId, host, request.port(), username, password, fromAddress,
                smtpAuth, sslEnabled, startTlsEnabled, startTlsRequired,
                authenticationMode, authMechanism, ehloDomain, authDomain, saslRealm, sslProtocols,
                connectionTimeoutMs, readTimeoutMs, writeTimeoutMs,
                oauthClientId, oauthClientSecret, oauthRefreshToken, oauthTenantId, oauthScope);
    }

    private String choice(String requested,
                          String existing,
                          String defaultValue,
                          Class<? extends Enum<?>> enumType,
                          String label) {
        String value = firstText(requested, existing);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (Enum<?> constant : enumType.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException(label + "不受支持。");
    }

    private Integer timeout(Integer requested, Integer existing, int defaultValue, String label) {
        int value = requested != null ? requested : existing != null ? existing : defaultValue;
        if (value < 1_000 || value > 120_000) {
            throw new IllegalArgumentException(label + "超时必须在1秒至120秒之间。");
        }
        return value;
    }

    private String tlsProtocols(String requested) {
        if (!StringUtils.hasText(requested)) {
            return DEFAULT_SSL_PROTOCOLS;
        }
        List<String> protocols = List.of(requested.trim().split("[,\\s]+"));
        if (protocols.isEmpty() || protocols.stream().anyMatch(protocol -> !TLS_PROTOCOLS.contains(protocol))) {
            throw new IllegalArgumentException("仅支持TLSv1.2和TLSv1.3协议。");
        }
        return String.join(" ", protocols.stream().distinct().toList());
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String firstText(String requested, String existing) {
        return StringUtils.hasText(requested) ? requested.trim() : trimToNull(existing);
    }

    private String requestedOrExisting(String requested, String existing) {
        return requested == null ? trimToNull(existing) : trimToNull(requested);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String serializePool(List<StoredMailSenderAccount> senders) {
        try {
            return objectMapper.writeValueAsString(senders);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("邮件发件池配置无法保存。", exception);
        }
    }

    private MailSenderConfiguration resolve(UUID orgUnitId) {
        return resolve(orgUnitId, new HashSet<>());
    }

    private MailSenderConfiguration resolve(UUID orgUnitId, Set<UUID> visited) {
        if (orgUnitId != null && !visited.add(orgUnitId)) {
            throw new IllegalStateException("邮件发件池的组织继承关系存在循环。");
        }
        Map<String, AppConfig> local = appConfigService.findLocalScopedConfigs(MAIL_SENDER_CONFIG_KEYS, orgUnitId);
        MailSenderOrigin localOrigin = origin(orgUnitId);
        List<MailSenderAccountConfiguration> own = localSenders(local, localOrigin);
        MailSenderPoolStrategy strategy = strategy(local, own);
        MailSenderConfiguration parent = resolveParent(orgUnitId, visited);
        List<MailSenderAccountConfiguration> inherited = parent.configured() ? parent.senders() : List.of();
        String parentError = parent.configured() ? null : parent.inheritedConfigurationError() != null
                ? parent.inheritedConfigurationError() : parent.configurationError();
        String ownError = strategy == MailSenderPoolStrategy.INHERIT ? null : configurationError(own, orgUnitId);
        List<MailSenderAccountConfiguration> effective = switch (strategy) {
            case INHERIT -> inherited;
            case REPLACE -> own;
            case MERGE -> ownError == null ? merge(inherited, own) : own;
        };
        String error = ownError;
        if (error == null) error = configurationError(effective, orgUnitId);
        boolean inheritOnly = strategy == MailSenderPoolStrategy.INHERIT;
        String sourceScope = inheritOnly ? parent.sourceScope()
                : strategy == MailSenderPoolStrategy.MERGE && !inherited.isEmpty() ? "MIXED" : localOrigin.scope();
        return new MailSenderConfiguration(
                effective, error == null, !local.isEmpty(), sourceScope,
                inheritOnly ? parent.sourceOrgUnitId() : orgUnitId,
                inheritOnly ? parent.sourceOrgUnitName() : localOrigin.orgUnitName(),
                inheritOnly && !inherited.isEmpty(), error,
                inheritOnly && parent.managedExternally(), strategy, own, inherited,
                parentError);
    }

    private MailSenderConfiguration resolveParent(UUID orgUnitId, Set<UUID> visited) {
        try {
            if (orgUnitId != null) {
                UUID parentId = hierarchyService.parentId(tenantContext.currentTenantIdOrDefault(), orgUnitId);
                return resolve(parentId, visited);
            }
            if (hasExternalSenderPool()) {
                List<MailSenderAccountConfiguration> senders = parsePool(
                        readExternalSenderPool(), new MailSenderOrigin(null, null, true));
                String error = configurationError(senders, null);
                return new MailSenderConfiguration(senders, error == null, false, "RUNTIME_SECRET", null, null,
                        true, error, true, MailSenderPoolStrategy.INHERIT, List.of(), List.of(), null);
            }
            return MailSenderConfiguration.unconfigured(null);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            log.warn("上级邮件发件池不可用。orgUnitId={}, reason={}", orgUnitId, exception.getMessage());
            return MailSenderConfiguration.unavailable(exception.getMessage());
        }
    }

    private MailSenderOrigin origin(UUID orgUnitId) {
        String name = orgUnitId == null ? null : hierarchyService.namesById(
                tenantContext.currentTenantIdOrDefault(), List.of(orgUnitId)).get(orgUnitId);
        return new MailSenderOrigin(orgUnitId, name, false);
    }

    private List<MailSenderAccountConfiguration> localSenders(Map<String, AppConfig> configs, MailSenderOrigin origin) {
        if (StringUtils.hasText(value(configs, MAIL_SENDER_POOL_KEY))) {
            return parsePool(value(configs, MAIL_SENDER_POOL_KEY), origin);
        }
        return configs.keySet().stream().anyMatch(key -> !MAIL_SENDER_STRATEGY_KEY.equals(key))
                ? legacySender(configs, origin) : List.of();
    }

    private MailSenderPoolStrategy strategy(Map<String, AppConfig> configs, List<MailSenderAccountConfiguration> own) {
        String configured = value(configs, MAIL_SENDER_STRATEGY_KEY);
        if (!StringUtils.hasText(configured)) {
            return hasExternalSenderPool() || own.isEmpty()
                    ? MailSenderPoolStrategy.INHERIT : MailSenderPoolStrategy.REPLACE;
        }
        try {
            return MailSenderPoolStrategy.valueOf(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("邮件发件池继承策略无效。");
        }
    }

    private List<MailSenderAccountConfiguration> merge(List<MailSenderAccountConfiguration> inherited,
                                                       List<MailSenderAccountConfiguration> own) {
        Map<String, MailSenderAccountConfiguration> merged = new LinkedHashMap<>();
        inherited.forEach(sender -> merged.put(accountIdentity(sender), sender));
        own.forEach(sender -> {
            merged.values().removeIf(existing -> existing.id().equals(sender.id()));
            merged.put(accountIdentity(sender), sender);
        });
        return List.copyOf(merged.values());
    }

    private String accountIdentity(MailSenderAccountConfiguration sender) {
        return sender.host().toLowerCase(Locale.ROOT) + "\u0000" + sender.port() + "\u0000"
                + (sender.smtpAuth() ? sender.username() : sender.fromAddress().toLowerCase(Locale.ROOT));
    }

    private boolean hasExternalSenderPool() {
        return StringUtils.hasText(externalSenderPoolFile);
    }

    private String readExternalSenderPool() {
        try {
            return Files.readString(Path.of(externalSenderPoolFile), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取运行时部署的邮件发件池 Secret。", exception);
        }
    }

    private List<MailSenderAccountConfiguration> parsePool(String rawPool, MailSenderOrigin origin) {
        return parseStoredPool(rawPool).stream().map(stored -> toConfiguration(stored, origin)).toList();
    }

    private List<StoredMailSenderAccount> parseStoredPool(String rawPool) {
        try {
            List<StoredMailSenderAccount> storedSenders = objectMapper.readValue(rawPool, STORED_POOL_TYPE);
            if (storedSenders == null || storedSenders.isEmpty()) {
                throw new IllegalStateException("邮件发件池配置格式无效。");
            }
            return storedSenders;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("邮件发件池配置格式无效。", exception);
        }
    }

    private MailSenderAccountConfiguration toConfiguration(StoredMailSenderAccount stored, MailSenderOrigin origin) {
        if (stored == null || stored.id() == null) {
            throw new IllegalStateException("邮件发件池配置缺少发件方标识。");
        }
        String providerId = defaultValue(stored.providerId(), MailSenderProvider.CUSTOM.name());
        String authenticationMode = defaultValue(
                stored.authenticationMode(), MailSenderAuthenticationMode.PASSWORD.name());
        String authMechanism = defaultValue(
                stored.authMechanism(), MailSenderAuthenticationMechanism.AUTO.name());
        String oauthScope = MailSenderAuthenticationMode.OAUTH2.name().equals(authenticationMode)
                ? MailSenderProvider.valueOf(providerId).resolveOAuthScope(stored.oauthScope())
                : stored.oauthScope();
        boolean startTlsEnabled = Boolean.TRUE.equals(stored.startTlsEnabled());
        return new MailSenderAccountConfiguration(
                stored.id(), providerId, stored.host(), stored.port(), stored.username(), stored.password(),
                stored.fromAddress(), Boolean.TRUE.equals(stored.smtpAuth()), Boolean.TRUE.equals(stored.sslEnabled()),
                startTlsEnabled,
                stored.startTlsRequired() == null ? startTlsEnabled : stored.startTlsRequired(),
                authenticationMode, authMechanism, stored.ehloDomain(), stored.authDomain(), stored.saslRealm(),
                defaultValue(stored.sslProtocols(), DEFAULT_SSL_PROTOCOLS),
                stored.connectionTimeoutMs() == null ? DEFAULT_CONNECTION_TIMEOUT_MS : stored.connectionTimeoutMs(),
                stored.readTimeoutMs() == null ? DEFAULT_READ_TIMEOUT_MS : stored.readTimeoutMs(),
                stored.writeTimeoutMs() == null ? DEFAULT_WRITE_TIMEOUT_MS : stored.writeTimeoutMs(),
                stored.oauthClientId(), stored.oauthClientSecret(), stored.oauthRefreshToken(),
                stored.oauthTenantId(), oauthScope, origin);
    }

    private List<MailSenderAccountConfiguration> legacySender(Map<String, AppConfig> configs, MailSenderOrigin origin) {
        boolean hasLegacyValue = configs.keySet().stream()
                .anyMatch(key -> !MAIL_SENDER_POOL_KEY.equals(key) && !MAIL_SENDER_STRATEGY_KEY.equals(key));
        if (!hasLegacyValue) {
            return List.of();
        }
        String host = value(configs, MAIL_HOST_KEY);
        Integer port = parsePort(value(configs, MAIL_PORT_KEY));
        String username = value(configs, MAIL_USERNAME_KEY);
        String password = value(configs, MAIL_PASSWORD_KEY);
        String fromAddress = value(configs, MAIL_FROM_KEY);
        Boolean smtpAuth = parseBoolean(value(configs, MAIL_SMTP_AUTH_KEY));
        Boolean sslEnabled = parseBoolean(value(configs, MAIL_SSL_ENABLE_KEY));
        Boolean startTlsEnabled = parseBoolean(value(configs, MAIL_STARTTLS_ENABLE_KEY));
        return List.of(new MailSenderAccountConfiguration(
                legacySenderId(origin.orgUnitId()), MailSenderProvider.CUSTOM.name(), host, port, username,
                password, fromAddress, Boolean.TRUE.equals(smtpAuth), Boolean.TRUE.equals(sslEnabled),
                Boolean.TRUE.equals(startTlsEnabled),
                Boolean.TRUE.equals(startTlsEnabled), MailSenderAuthenticationMode.PASSWORD.name(),
                MailSenderAuthenticationMechanism.AUTO.name(), null, null, null, DEFAULT_SSL_PROTOCOLS,
                DEFAULT_CONNECTION_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, DEFAULT_WRITE_TIMEOUT_MS,
                null, null, null, null, null, origin));
    }

    private UUID legacySenderId(UUID sourceOrgUnitId) {
        String scope = tenantContext.currentTenantIdOrDefault() + ":" + (sourceOrgUnitId == null
                ? "TENANT"
                : sourceOrgUnitId);
        return UUID.nameUUIDFromBytes(("legacy-mail-sender:" + scope).getBytes(StandardCharsets.UTF_8));
    }

    private String configurationError(List<MailSenderAccountConfiguration> senders, UUID requestedOrgUnitId) {
        if (senders.isEmpty()) {
            return requestedOrgUnitId == null
                    ? "市公司尚未配置邮件发件方。"
                    : "当前区县尚未配置邮件发件方，市公司也没有可继承的默认配置。";
        }
        List<String> incompleteSenders = new ArrayList<>();
        for (int index = 0; index < senders.size(); index++) {
            if (!senders.get(index).configured()) {
                incompleteSenders.add("发件方" + (index + 1));
            }
        }
        return incompleteSenders.isEmpty()
                ? null
                : String.join("、", incompleteSenders) + "配置不完整，请补齐服务器、认证信息和发件地址。";
    }

    private String value(Map<String, AppConfig> configs, String key) {
        AppConfig config = configs.get(key);
        return config == null ? null : config.getConfigValue();
    }

    private Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port >= 1 && port <= 65_535 ? port : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return null;
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private record StoredMailSenderAccount(
            UUID id,
            String providerId,
            String host,
            Integer port,
            String username,
            String password,
            String fromAddress,
            Boolean smtpAuth,
            Boolean sslEnabled,
            Boolean startTlsEnabled,
            Boolean startTlsRequired,
            String authenticationMode,
            String authMechanism,
            String ehloDomain,
            String authDomain,
            String saslRealm,
            String sslProtocols,
            Integer connectionTimeoutMs,
            Integer readTimeoutMs,
            Integer writeTimeoutMs,
            String oauthClientId,
            String oauthClientSecret,
            String oauthRefreshToken,
            String oauthTenantId,
            String oauthScope
    ) {
        private StoredMailSenderAccount withOAuthRefreshToken(String refreshToken) {
            return new StoredMailSenderAccount(
                    id, providerId, host, port, username, password, fromAddress, smtpAuth,
                    sslEnabled, startTlsEnabled, startTlsRequired, authenticationMode, authMechanism,
                    ehloDomain, authDomain, saslRealm, sslProtocols, connectionTimeoutMs,
                    readTimeoutMs, writeTimeoutMs, oauthClientId, oauthClientSecret, refreshToken,
                    oauthTenantId, oauthScope);
        }
    }

    public record MailSenderConfiguration(
            List<MailSenderAccountConfiguration> senders,
            boolean configured,
            boolean localOverride,
            String sourceScope,
            UUID sourceOrgUnitId,
            String sourceOrgUnitName,
            boolean inherited,
            String configurationError,
            boolean managedExternally,
            MailSenderPoolStrategy strategy,
            List<MailSenderAccountConfiguration> localSenders,
            List<MailSenderAccountConfiguration> inheritedSenders,
            String inheritedConfigurationError
    ) {
        public MailSenderConfiguration {
            senders = List.copyOf(senders);
            localSenders = List.copyOf(localSenders);
            inheritedSenders = List.copyOf(inheritedSenders);
        }

        private static MailSenderConfiguration unconfigured(UUID requestedOrgUnitId) {
            String error = requestedOrgUnitId == null
                    ? "市公司尚未配置邮件发件方。"
                    : "当前区县尚未配置邮件发件方，市公司也没有可继承的默认配置。";
            return new MailSenderConfiguration(
                    List.of(), false, false, "UNCONFIGURED", null, null, false, error, false,
                    MailSenderPoolStrategy.INHERIT, List.of(), List.of(), null);
        }

        private static MailSenderConfiguration unavailable(String error) {
            return new MailSenderConfiguration(
                    List.of(), false, false, "UNCONFIGURED", null, null, true, error, false,
                    MailSenderPoolStrategy.INHERIT, List.of(), List.of(), null);
        }

        private MailSenderSettingsDTO toDto() {
            return new MailSenderSettingsDTO(
                    configured, localOverride, senders.stream().map(this::accountDto).toList(),
                    sourceScope, sourceOrgUnitId, sourceOrgUnitName, inherited, configurationError,
                    managedExternally, strategy, localSenders.stream().map(this::accountDto).toList(),
                    inheritedSenders.stream().map(this::accountDto).toList(), inheritedConfigurationError);
        }

        private MailSenderAccountDTO accountDto(MailSenderAccountConfiguration sender) {
            return new MailSenderAccountDTO(
                            sender.id(), sender.host(), sender.port(), sender.username(), sender.fromAddress(),
                            sender.smtpAuth(), sender.sslEnabled(), sender.startTlsEnabled(),
                            StringUtils.hasText(sender.password()), sender.providerId(), sender.authenticationMode(),
                            sender.authMechanism(), sender.startTlsRequired(), sender.ehloDomain(),
                            sender.authDomain(), sender.saslRealm(), sender.sslProtocols(),
                            sender.connectionTimeoutMs(), sender.readTimeoutMs(), sender.writeTimeoutMs(),
                            sender.oauthClientId(), sender.oauthTenantId(), sender.oauthScope(),
                            StringUtils.hasText(sender.oauthClientSecret()),
                            StringUtils.hasText(sender.oauthRefreshToken()), sender.origin().scope(),
                            sender.origin().orgUnitId(), sender.origin().orgUnitName());
        }
    }

    public record MailSenderAccountConfiguration(
            UUID id,
            String providerId,
            String host,
            Integer port,
            String username,
            String password,
            String fromAddress,
            boolean smtpAuth,
            boolean sslEnabled,
            boolean startTlsEnabled,
            boolean startTlsRequired,
            String authenticationMode,
            String authMechanism,
            String ehloDomain,
            String authDomain,
            String saslRealm,
            String sslProtocols,
            int connectionTimeoutMs,
            int readTimeoutMs,
            int writeTimeoutMs,
            String oauthClientId,
            String oauthClientSecret,
            String oauthRefreshToken,
            String oauthTenantId,
            String oauthScope,
            MailSenderOrigin origin
    ) {
        public boolean configured() {
            boolean authenticationConfigured = !smtpAuth
                    || (MailSenderAuthenticationMode.OAUTH2.name().equals(authenticationMode)
                        ? StringUtils.hasText(oauthClientId)
                            && StringUtils.hasText(oauthClientSecret)
                            && StringUtils.hasText(oauthRefreshToken)
                        : StringUtils.hasText(username) && StringUtils.hasText(password));
            return StringUtils.hasText(host)
                    && port != null
                    && StringUtils.hasText(fromAddress)
                    && authenticationConfigured;
        }
    }

}
