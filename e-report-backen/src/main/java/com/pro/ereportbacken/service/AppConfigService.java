package com.pro.ereportbacken.service;

import java.util.UUID;

import com.pro.ereportbacken.config.AppConfigConstant;
import com.pro.ereportbacken.config.CacheNames;
import com.pro.ereportbacken.config.NotificationConstant;
import com.pro.ereportbacken.model.pojo.AppConfig;
import com.pro.ereportbacken.model.pojo.TenantAppConfig;
import com.pro.ereportbacken.model.pojo.OrgAppConfig;
import com.pro.ereportbacken.model.pojo.OrgUnit;
import com.pro.ereportbacken.repository.AppConfigRepository;
import com.pro.ereportbacken.repository.TenantAppConfigRepository;
import com.pro.ereportbacken.repository.OrgAppConfigRepository;
import com.pro.ereportbacken.repository.OrgUnitRepository;
import com.pro.ereportbacken.security.ConfigurationScopeContext;
import com.pro.ereportbacken.security.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pro.ereportbacken.config.AppConfigConstant.AMAP_API_KEY;
import static com.pro.ereportbacken.config.AppConfigConstant.AMAP_API_KEY_POOL;

@Service
@Log4j2
public class AppConfigService {

    /**
     * 初始化数据库中的一些基础配置项（如果它们不存在）。
     * 这通常在应用启动时执行一次。
     */
    @PostConstruct
    @Transactional // 确保数据库操作在事务中
    public void initializeDefaultConfigs() {
        log.info("Initializing default application configurations in database if not present...");

        ensureDefaultConfig(AMAP_API_KEY, defaultAmapApiKey, "高德网页服务接口密钥（用于静态地图等）");
        ensureDefaultConfig(AMAP_API_KEY_POOL, defaultAmapApiKeyPool, "高德网页服务接口密钥池，支持逗号、分号、换行分隔");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_CLIENT_ENABLED, "true", "是否启用客户端运行诊断采集");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_WARNING_ENABLED, "true", "是否采集客户端警告事件");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_OPERATION_ENABLED, "true", "是否采集用户操作与关键写操作事件");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_OPERATION_SUCCESS_SAMPLE_RATE_PERCENT, "20", "客户端正常操作采样率（百分比；失败与定位操作不采样）");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_BATCH_SIZE, "20", "客户端单批运行诊断事件数量");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_FLUSH_INTERVAL_SECONDS, "15", "客户端运行诊断发送间隔（秒）");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_RETENTION_DAYS, "30", "运行诊断事件保留天数");
        ensureDefaultConfig(AppConfigConstant.OBSERVABILITY_OPERATION_RETENTION_DAYS, "14", "正常用户操作轨迹保留天数");
        ensureDefaultConfig(AppConfigConstant.PASSWORD_POLICY_MIN_LENGTH, "8", "密码最小长度（系统上限100位）");
        ensureDefaultConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_UPPERCASE, "true", "密码是否必须包含大写字母");
        ensureDefaultConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_LOWERCASE, "true", "密码是否必须包含小写字母");
        ensureDefaultConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_DIGIT, "true", "密码是否必须包含数字");
        ensureDefaultConfig(AppConfigConstant.PASSWORD_POLICY_REQUIRE_SPECIAL, "true", "密码是否必须包含特殊字符");
        ensureDefaultConfig(
                AppConfigConstant.IMAGE_REDACTION_SUBMISSION_ENABLED,
                "true",
                "是否允许主体服务提交新的图片脱敏任务（关闭后已提交任务继续处理）"
        );
        invalidateConfigAfterCommit();
        log.info("Default application configurations initialization complete.");
    }

    private void ensureDefaultConfig(String key, String value, String description) {
        Optional<AppConfig> existingConfig = appConfigRepository.findByConfigKey(key);
        if (existingConfig.isEmpty()) {
            appConfigRepository.save(new AppConfig(key, value, description));
            log.info("Created default config: {} = {}", key, cover(key, value));
            return;
        }

        AppConfig config = existingConfig.get();
        if (isMisencodedDescriptionOf(config.getDescription(), description)) {
            config.setDescription(description);
            appConfigRepository.save(config);
            log.info("Repaired misencoded description for config: {}", key);
        }
    }

    static boolean isMisencodedDescriptionOf(String storedDescription, String expectedDescription) {
        if (!StringUtils.hasText(storedDescription)
                || !StringUtils.hasText(expectedDescription)
                || storedDescription.equals(expectedDescription)) {
            return false;
        }
        String decodedDescription = decodeMojibake(storedDescription);
        return expectedDescription.equals(decodedDescription);
    }

    private static String decodeMojibake(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            int encodedByte = toWindows1252Byte(value.charAt(index));
            if (encodedByte < 0) {
                return value;
            }
            bytes.write(encodedByte);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int toWindows1252Byte(char character) {
        if (character <= 0xFF) {
            return character;
        }
        return switch (character) {
            case '\u20AC' -> 0x80;
            case '\u201A' -> 0x82;
            case '\u0192' -> 0x83;
            case '\u201E' -> 0x84;
            case '\u2026' -> 0x85;
            case '\u2020' -> 0x86;
            case '\u2021' -> 0x87;
            case '\u02C6' -> 0x88;
            case '\u2030' -> 0x89;
            case '\u0160' -> 0x8A;
            case '\u2039' -> 0x8B;
            case '\u0152' -> 0x8C;
            case '\u017D' -> 0x8E;
            case '\u2018' -> 0x91;
            case '\u2019' -> 0x92;
            case '\u201C' -> 0x93;
            case '\u201D' -> 0x94;
            case '\u2022' -> 0x95;
            case '\u2013' -> 0x96;
            case '\u2014' -> 0x97;
            case '\u02DC' -> 0x98;
            case '\u2122' -> 0x99;
            case '\u0161' -> 0x9A;
            case '\u203A' -> 0x9B;
            case '\u0153' -> 0x9C;
            case '\u017E' -> 0x9E;
            case '\u0178' -> 0x9F;
            default -> -1;
        };
    }

    private final AppConfigRepository appConfigRepository;
    private final TenantAppConfigRepository tenantAppConfigRepository;
    private final OrgAppConfigRepository orgAppConfigRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final TenantContext tenantContext;
    private final ConfigurationScopeContext configurationScopeContext;
    private final CacheInvalidationService cacheInvalidationService;
    private final CacheManager cacheManager;

    @Value("${appconfig.initial.amap_key:}")
    private String defaultAmapApiKey;
    @Value("${map.amap.api-key-pool:${appconfig.initial.amap_key:}}")
    private String defaultAmapApiKeyPool;

    public AppConfigService(AppConfigRepository appConfigRepository,
                            TenantAppConfigRepository tenantAppConfigRepository,
                            OrgAppConfigRepository orgAppConfigRepository,
                            OrgUnitRepository orgUnitRepository,
                            TenantContext tenantContext,
                            ConfigurationScopeContext configurationScopeContext,
                            CacheInvalidationService cacheInvalidationService,
                            CacheManager cacheManager) {
        this.appConfigRepository = appConfigRepository;
        this.tenantAppConfigRepository = tenantAppConfigRepository;
        this.orgAppConfigRepository = orgAppConfigRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.tenantContext = tenantContext;
        this.configurationScopeContext = configurationScopeContext;
        this.cacheInvalidationService = cacheInvalidationService;
        this.cacheManager = cacheManager;
    }



    // 简单的遮掩函数，避免日志中完整打印密码
    private String cover(String key, String value){
        return maskIfSensitive(key, value);
    }


    /**
     * 根据键获取配置值。使用 Spring Cache 进行缓存以提高性能。
     * "appConfigCache" 是缓存的名称，可以在 CacheConfig 中配置。
     * @param key 配置键
     * @return 配置值，如果找不到则返回 null
     */
    public String getConfigValue(String key) {
        String cacheKey = configCacheKey(key);
        String cached = readConfigCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        log.debug("Fetching config value for key: {}", key); // 缓存未命中时会打印
        String resolved = resolveConfig(key, configurationScopeContext.currentOrgUnitIdOrNull())
                .map(resolvedConfig -> resolvedConfig.config().getConfigValue())
                .orElse(null);
        if (resolved != null) {
            writeConfigCache(cacheKey, resolved);
        }
        return resolved;
    }

    /**
     * 根据键获取配置值，如果数据库中不存在，则返回提供的默认值。
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 更新或创建配置项。
     * 当配置更新时，需要清除相应的缓存。
     * @param key 配置键
     * @param value 配置值
     * @param description 描述
     * @return 更新后的 AppConfig 对象
     */
    @Transactional
    public AppConfig setConfigValue(String key, String value, String description) {
        validateKnownConfigValue(key, value);
        TenantAppConfig config = tenantConfig(key);
        config.setConfigValue(value);
        if (description != null) config.setDescription(description);
        log.info("Updating config: {} = {}", key, cover(key, value));
        AppConfig result = toAppConfig(tenantAppConfigRepository.save(config));
        invalidateConfigAfterCommit();
        return result;
    }

    // 可以为特定类型的配置值提供便利方法
    public int getConfigValueAsInt(String key, int defaultValue) {
        String value = getConfigValue(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Could not parse int for config key '{}', value: '{}'. Returning default: {}",
                    key, maskIfSensitive(key, value), defaultValue, e);
            return defaultValue;
        }
    }

    public boolean getConfigValueAsBoolean(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        return parseBoolean(value, defaultValue);
    }

    /**
     * 读取租户级配置，不受当前登录用户所属组织的覆盖影响。
     * 图片脱敏提交开关属于租户级服务开关，后台巡检和上传请求必须使用同一份判定。
     */
    @Transactional(readOnly = true)
    public boolean getTenantConfigValueAsBoolean(UUID tenantId, String key, boolean defaultValue) {
        if (tenantId == null || !StringUtils.hasText(key)) {
            return defaultValue;
        }
        String cacheKey = tenantId + ":TENANT:" + key;
        String cached = readConfigCache(cacheKey);
        if (cached != null) {
            return parseBoolean(cached, defaultValue);
        }
        String value = tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, key)
                .map(TenantAppConfig::getConfigValue)
                .orElseGet(() -> appConfigRepository.findByConfigKey(key)
                        .map(AppConfig::getConfigValue)
                        .orElse(null));
        if (value != null) {
            writeConfigCache(cacheKey, value);
        }
        return parseBoolean(value, defaultValue);
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    @Transactional
    public AppConfig saveOrUpdateConfig(String configKey, String configValue, String description) {
        if (!StringUtils.hasText(configKey)) {
            throw new IllegalArgumentException("Config key cannot be empty.");
        }
        validateKnownConfigValue(configKey, configValue);
        TenantAppConfig config = tenantConfig(configKey);
        boolean existing = config.getId() != null;
        config.setConfigValue(configValue);
        if (description != null) config.setDescription(description);
        log.info("{} tenant config: {} = {}", existing ? "Updating" : "Creating", configKey,
                maskIfSensitive(configKey, configValue));
        AppConfig result = toAppConfig(tenantAppConfigRepository.save(config));
        invalidateConfigAfterCommit();
        return result;
    }

    public static String maskIfSensitive(String key, String value) {
        if (isKeySensitive(key)) {
            return StringUtils.hasText(value) ? "[已配置]" : "[未配置]";
        }
        return value;
    }

    public static boolean isKeySensitive(String key) {
        if (key == null) return false;
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password")
                || lowerKey.contains("secret")
                || lowerKey.contains("token")
                || lowerKey.contains("key")
                || lowerKey.contains("auth")
                || lowerKey.contains("webhook");
    }

    // 新增：获取所有配置项 (分页)
    @Transactional(readOnly = true)
    public Page<AppConfig> getAllConfigs(Pageable pageable) {
        return mergedConfigs(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AppConfig> searchConfigs(String searchTerm, Pageable pageable) {
        return mergedConfigs(StringUtils.hasText(searchTerm) ? searchTerm.trim() : null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ResolvedAppConfig> searchScopedConfigs(String searchTerm, Pageable pageable, UUID orgUnitId) {
        return searchScopedConfigs(searchTerm, pageable, orgUnitId, true);
    }

    @Transactional(readOnly = true)
    public Page<ResolvedAppConfig> searchScopedConfigs(String searchTerm,
                                                       Pageable pageable,
                                                       UUID orgUnitId,
                                                       boolean includeNotification) {
        return searchScopedConfigs(searchTerm, pageable, orgUnitId, includeNotification, true);
    }

    @Transactional(readOnly = true)
    public Page<ResolvedAppConfig> searchScopedConfigs(String searchTerm,
                                                       Pageable pageable,
                                                       UUID orgUnitId,
                                                       boolean includeNotification,
                                                       boolean includePasswordPolicy) {
        String normalizedSearch = StringUtils.hasText(searchTerm)
                ? searchTerm.trim().toLowerCase(Locale.ROOT)
                : null;
        List<ResolvedAppConfig> configs = resolveAll(orgUnitId).values().stream()
                .filter(resolved -> includeNotification
                        || !NotificationConstant.isManagedConfigKey(resolved.config().getConfigKey()))
                .filter(resolved -> includePasswordPolicy
                        || !AppConfigConstant.isPasswordPolicyKey(resolved.config().getConfigKey()))
                .filter(resolved -> orgUnitId == null
                        || !AppConfigConstant.isTenantWideOnlyKey(resolved.config().getConfigKey()))
                .filter(resolved -> normalizedSearch == null
                        || contains(resolved.config().getConfigKey(), normalizedSearch)
                        || searchableConfigValueContains(resolved.config(), normalizedSearch)
                        || contains(resolved.config().getDescription(), normalizedSearch))
                .sorted(resolvedConfigComparator(pageable))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), configs.size()));
        int end = Math.min(configs.size(), start + pageable.getPageSize());
        return new PageImpl<>(new ArrayList<>(configs.subList(start, end)), pageable, configs.size());
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedAppConfig> findScopedConfigByKey(String configKey, UUID orgUnitId) {
        return resolveConfig(configKey, orgUnitId);
    }

    @Transactional(readOnly = true)
    public Map<String, AppConfig> findLocalScopedConfigs(Collection<String> configKeys, UUID orgUnitId) {
        Set<String> requestedKeys = configKeys == null ? Set.of() : Set.copyOf(configKeys);
        if (requestedKeys.isEmpty()) return Map.of();
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        if (orgUnitId == null) {
            return tenantAppConfigRepository.findByTenantId(tenantId).stream()
                    .filter(config -> requestedKeys.contains(config.getConfigKey()))
                    .collect(Collectors.toUnmodifiableMap(
                            TenantAppConfig::getConfigKey,
                            this::toAppConfig));
        }
        requireOrgUnit(tenantId, orgUnitId);
        return orgAppConfigRepository.findByTenantIdAndOrgUnitId(tenantId, orgUnitId).stream()
                .filter(config -> requestedKeys.contains(config.getConfigKey()))
                .collect(Collectors.toUnmodifiableMap(
                        OrgAppConfig::getConfigKey,
                        this::toAppConfig));
    }

    @Transactional
    public int deleteLocalScopedConfigs(Collection<String> configKeys, UUID orgUnitId) {
        Set<String> requestedKeys = configKeys == null ? Set.of() : Set.copyOf(configKeys);
        if (requestedKeys.isEmpty()) return 0;
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        int deleted = 0;
        if (orgUnitId == null) {
            List<TenantAppConfig> configs = tenantAppConfigRepository.findByTenantId(tenantId).stream()
                    .filter(config -> requestedKeys.contains(config.getConfigKey()))
                    .toList();
            tenantAppConfigRepository.deleteAll(configs);
            deleted = configs.size();
        } else {
            requireOrgUnit(tenantId, orgUnitId);
            List<OrgAppConfig> configs = orgAppConfigRepository.findByTenantIdAndOrgUnitId(tenantId, orgUnitId).stream()
                    .filter(config -> requestedKeys.contains(config.getConfigKey()))
                    .toList();
            orgAppConfigRepository.deleteAll(configs);
            deleted = configs.size();
        }
        if (deleted > 0) invalidateConfigAfterCommit();
        log.info("Deleted scoped config group. tenantId={}, orgUnitId={}, keys={}, deleted={}",
                tenantId, orgUnitId, requestedKeys, deleted);
        return deleted;
    }

    @Transactional
    public ResolvedAppConfig saveOrUpdateScopedConfig(String configKey,
                                                      String configValue,
                                                      String description,
                                                      UUID orgUnitId) {
        return saveOrUpdateScopedConfig(configKey, configValue, description, orgUnitId, false);
    }

    @Transactional
    public ResolvedAppConfig saveOrUpdateScopedConfig(String configKey,
                                                      String configValue,
                                                      String description,
                                                      UUID orgUnitId,
                                                      boolean retainSensitiveValue) {
        if (AppConfigConstant.isTenantWideOnlyKey(configKey) && orgUnitId != null) {
            throw new IllegalArgumentException("图片脱敏提交开关只能在租户默认配置中修改，不能按组织覆盖。");
        }
        if (retainSensitiveValue && !isKeySensitive(configKey)) {
            throw new IllegalArgumentException("只有敏感配置可以保留现有值。");
        }
        if (retainSensitiveValue && configValue != null) {
            throw new IllegalArgumentException("保留敏感配置现有值时不能同时提交新值。");
        }
        if (isKeySensitive(configKey) && !retainSensitiveValue && !StringUtils.hasText(configValue)) {
            throw new IllegalArgumentException("敏感配置的新值不能为空；如需保留现有值，请使用保留现有值操作。");
        }
        String valueToPersist = retainSensitiveValue
                ? resolveConfig(configKey, orgUnitId)
                        .map(resolved -> resolved.config().getConfigValue())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Sensitive config with key '" + configKey + "' was not found."))
                : configValue;
        validateKnownConfigValue(configKey, valueToPersist);
        if (orgUnitId == null) {
            saveOrUpdateConfig(configKey, valueToPersist, description);
            return resolveConfig(configKey, null).orElseThrow();
        }
        if (!StringUtils.hasText(configKey)) {
            throw new IllegalArgumentException("Config key cannot be empty.");
        }
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        requireOrgUnit(tenantId, orgUnitId);
        OrgAppConfig config = orgAppConfigRepository.findByTenantIdAndOrgUnitIdAndConfigKey(
                        tenantId, orgUnitId, configKey)
                .orElseGet(() -> {
                    OrgAppConfig created = new OrgAppConfig();
                    created.setTenantId(tenantId);
                    created.setOrgUnitId(orgUnitId);
                    created.setConfigKey(configKey);
                    resolveConfig(configKey, orgUnitId).ifPresent(inherited -> {
                        created.setConfigValue(inherited.config().getConfigValue());
                        created.setDescription(inherited.config().getDescription());
                    });
                    return created;
                });
        config.setConfigValue(valueToPersist);
        if (description != null) {
            config.setDescription(description);
        }
        orgAppConfigRepository.saveAndFlush(config);
        log.info("Saved organization config override: tenantId={}, orgUnitId={}, key={}, value={}",
                tenantId, orgUnitId, configKey, maskIfSensitive(configKey, valueToPersist));
        ResolvedAppConfig result = resolveConfig(configKey, orgUnitId).orElseThrow();
        invalidateConfigAfterCommit();
        return result;
    }

    @Transactional
    public void deleteScopedConfig(String configKey, UUID orgUnitId) {
        if (AppConfigConstant.isTenantWideOnlyKey(configKey) && orgUnitId != null) {
            throw new IllegalArgumentException("图片脱敏提交开关只能在租户默认配置中修改，不能按组织覆盖。");
        }
        if (orgUnitId == null) {
            deleteConfig(configKey);
            return;
        }
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        requireOrgUnit(tenantId, orgUnitId);
        if (orgAppConfigRepository.findByTenantIdAndOrgUnitIdAndConfigKey(tenantId, orgUnitId, configKey).isEmpty()) {
            throw new EntityNotFoundException("Organization config override with key '" + configKey + "' was not found.");
        }
        orgAppConfigRepository.deleteByTenantIdAndOrgUnitIdAndConfigKey(tenantId, orgUnitId, configKey);
        invalidateConfigAfterCommit();
        log.info("Deleted organization config override: tenantId={}, orgUnitId={}, key={}", tenantId, orgUnitId, configKey);
    }

    // 新增：根据 key 获取单个 AppConfig 实体
    @Transactional(readOnly = true)
    public Optional<AppConfig> findConfigByKey(String configKey) {
        return resolveConfig(configKey, configurationScopeContext.currentOrgUnitIdOrNull())
                .map(ResolvedAppConfig::config);
    }

    // 新增：删除配置项 (要小心使用，有些配置可能不应删除)
    @Transactional
    public void deleteConfig(String configKey) {
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        boolean tenantConfigExists = tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, configKey).isPresent();
        if (!tenantConfigExists) {
            throw new EntityNotFoundException("Tenant config override with key '" + configKey + "' was not found.");
        }
        tenantAppConfigRepository.deleteByTenantIdAndConfigKey(tenantId, configKey);
        invalidateConfigAfterCommit();
        log.info("Deleted tenant config override: {}", configKey);
    }

    private void invalidateConfigAfterCommit() {
        cacheInvalidationService.clearAfterCommit(CacheNames.APP_CONFIG);
    }

    private void validateKnownConfigValue(String configKey, String configValue) {
        if (!AppConfigConstant.isTenantWideOnlyKey(configKey)) {
            return;
        }
        String normalized = configValue == null ? "" : configValue.trim();
        if (!Set.of("true", "false", "1", "0", "yes", "no").contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("图片脱敏提交开关只能填写 true/false（也接受 1/0、yes/no）。");
        }
    }

    private String configCacheKey(String configKey) {
        return tenantContext.currentTenantIdOrDefault()
                + ":" + Objects.toString(configurationScopeContext.currentOrgUnitIdOrNull(), "TENANT")
                + ":" + configKey;
    }

    private String readConfigCache(String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.APP_CONFIG);
            return cache == null ? null : cache.get(cacheKey, String.class);
        } catch (RuntimeException exception) {
            log.warn("系统配置缓存读取失败，将回源数据库。cacheKey={}", cacheKey, exception);
            return null;
        }
    }

    private void writeConfigCache(String cacheKey, String value) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.APP_CONFIG);
            if (cache != null) {
                cache.put(cacheKey, value);
            }
        } catch (RuntimeException exception) {
            log.warn("系统配置缓存写入失败，本次继续使用数据库结果。cacheKey={}", cacheKey, exception);
        }
    }

    private TenantAppConfig tenantConfig(String configKey) {
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        return tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, configKey)
                .orElseGet(() -> {
                    TenantAppConfig config = new TenantAppConfig();
                    config.setTenantId(tenantId);
                    config.setConfigKey(configKey);
                    appConfigRepository.findByConfigKey(configKey).ifPresent(platformDefault -> {
                        config.setConfigValue(platformDefault.getConfigValue());
                        config.setDescription(platformDefault.getDescription());
                    });
                    return config;
                });
    }

    private Page<AppConfig> mergedConfigs(String searchTerm, Pageable pageable) {
        Map<String, AppConfig> merged = resolveAll(configurationScopeContext.currentOrgUnitIdOrNull()).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().config(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        String normalizedSearch = searchTerm == null ? null : searchTerm.toLowerCase(Locale.ROOT);
        List<AppConfig> configs = merged.values().stream()
                .filter(config -> normalizedSearch == null
                        || contains(config.getConfigKey(), normalizedSearch)
                        || searchableConfigValueContains(config, normalizedSearch)
                        || contains(config.getDescription(), normalizedSearch))
                .sorted(configComparator(pageable))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), configs.size()));
        int end = Math.min(configs.size(), start + pageable.getPageSize());
        return new PageImpl<>(new ArrayList<>(configs.subList(start, end)), pageable, configs.size());
    }

    private Optional<ResolvedAppConfig> resolveConfig(String configKey, UUID requestedOrgUnitId) {
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        OrgUnit requestedOrg = requestedOrgUnitId == null ? null : requireOrgUnit(tenantId, requestedOrgUnitId);
        ResolvedAppConfig resolved = appConfigRepository.findByConfigKey(configKey)
                .map(config -> resolved(config, requestedOrg, ResolvedAppConfig.SourceScope.PLATFORM_DEFAULT, null, null))
                .orElse(null);
        Optional<TenantAppConfig> tenantOverride = tenantAppConfigRepository.findByTenantIdAndConfigKey(tenantId, configKey);
        if (tenantOverride.isPresent()) {
            resolved = resolved(
                    toAppConfig(tenantOverride.get()),
                    requestedOrg,
                    ResolvedAppConfig.SourceScope.TENANT_OVERRIDE,
                    null,
                    null
            );
        }
        if (requestedOrg != null) {
            for (OrgUnit orgUnit : ancestorChain(tenantId, requestedOrg)) {
                Optional<OrgAppConfig> orgOverride = orgAppConfigRepository.findByTenantIdAndOrgUnitIdAndConfigKey(
                        tenantId, orgUnit.getId(), configKey);
                if (orgOverride.isPresent()) {
                    resolved = resolved(
                            toAppConfig(orgOverride.get()),
                            requestedOrg,
                            ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE,
                            orgUnit.getId(),
                            orgUnit.getName()
                    );
                }
            }
        }
        return Optional.ofNullable(resolved);
    }

    private Map<String, ResolvedAppConfig> resolveAll(UUID requestedOrgUnitId) {
        UUID tenantId = tenantContext.currentTenantIdOrDefault();
        OrgUnit requestedOrg = requestedOrgUnitId == null ? null : requireOrgUnit(tenantId, requestedOrgUnitId);
        Map<String, ResolvedAppConfig> merged = new LinkedHashMap<>();
        appConfigRepository.findAll().forEach(config -> merged.put(
                config.getConfigKey(),
                resolved(config, requestedOrg, ResolvedAppConfig.SourceScope.PLATFORM_DEFAULT, null, null)
        ));
        tenantAppConfigRepository.findByTenantId(tenantId).forEach(config -> merged.put(
                config.getConfigKey(),
                resolved(toAppConfig(config), requestedOrg, ResolvedAppConfig.SourceScope.TENANT_OVERRIDE, null, null)
        ));
        if (requestedOrg != null) {
            for (OrgUnit orgUnit : ancestorChain(tenantId, requestedOrg)) {
                orgAppConfigRepository.findByTenantIdAndOrgUnitId(tenantId, orgUnit.getId()).forEach(config -> merged.put(
                        config.getConfigKey(),
                        resolved(
                                toAppConfig(config),
                                requestedOrg,
                                ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE,
                                orgUnit.getId(),
                                orgUnit.getName()
                        )
                ));
            }
        }
        return merged;
    }

    private ResolvedAppConfig resolved(AppConfig config,
                                       OrgUnit requestedOrg,
                                       ResolvedAppConfig.SourceScope sourceScope,
                                       UUID sourceOrgUnitId,
                                       String sourceOrgUnitName) {
        boolean inherited = requestedOrg == null
                ? sourceScope != ResolvedAppConfig.SourceScope.TENANT_OVERRIDE
                : sourceScope != ResolvedAppConfig.SourceScope.ORGANIZATION_OVERRIDE
                || !Objects.equals(requestedOrg.getId(), sourceOrgUnitId);
        return new ResolvedAppConfig(
                config,
                requestedOrg == null ? null : requestedOrg.getId(),
                requestedOrg == null ? null : requestedOrg.getName(),
                sourceScope,
                sourceOrgUnitId,
                sourceOrgUnitName,
                inherited
        );
    }

    private List<OrgUnit> ancestorChain(UUID tenantId, OrgUnit requestedOrg) {
        List<OrgUnit> reversed = new ArrayList<>();
        OrgUnit current = requestedOrg;
        Set<UUID> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current.getId())) {
            reversed.add(current);
            current = current.getParentId() == null
                    ? null
                    : orgUnitRepository.findByTenantIdAndId(tenantId, current.getParentId()).orElse(null);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private OrgUnit requireOrgUnit(UUID tenantId, UUID orgUnitId) {
        return orgUnitRepository.findByTenantIdAndId(tenantId, orgUnitId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found with id: " + orgUnitId));
    }

    private Comparator<ResolvedAppConfig> resolvedConfigComparator(Pageable pageable) {
        Comparator<AppConfig> appConfigComparator = configComparator(pageable);
        return Comparator.comparing(ResolvedAppConfig::config, appConfigComparator);
    }

    private Comparator<AppConfig> configComparator(Pageable pageable) {
        boolean descending = pageable.getSort().getOrderFor("configKey") != null
                && pageable.getSort().getOrderFor("configKey").isDescending();
        Comparator<AppConfig> comparator = Comparator.comparing(
                AppConfig::getConfigKey,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        );
        return descending ? comparator.reversed() : comparator;
    }

    private boolean contains(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private boolean searchableConfigValueContains(AppConfig config, String normalizedSearch) {
        return !isKeySensitive(config.getConfigKey())
                && contains(config.getConfigValue(), normalizedSearch);
    }

    private AppConfig toAppConfig(TenantAppConfig tenantConfig) {
        AppConfig config = new AppConfig(
                tenantConfig.getConfigKey(),
                tenantConfig.getConfigValue(),
                tenantConfig.getDescription()
        );
        config.setLastUpdatedTime(tenantConfig.getLastUpdatedTime());
        return config;
    }

    private AppConfig toAppConfig(OrgAppConfig orgConfig) {
        AppConfig config = new AppConfig(
                orgConfig.getConfigKey(),
                orgConfig.getConfigValue(),
                orgConfig.getDescription()
        );
        config.setLastUpdatedTime(orgConfig.getLastUpdatedTime());
        return config;
    }
}
