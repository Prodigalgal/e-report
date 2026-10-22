package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.ApiTokenCreateRequestDTO;
import com.pro.ereportbacken.model.dto.ApiTokenCreatedDTO;
import com.pro.ereportbacken.model.dto.ApiTokenDTO;
import com.pro.ereportbacken.model.dto.ApiTokenPermissionOptionDTO;
import com.pro.ereportbacken.model.pojo.ApiToken;
import com.pro.ereportbacken.model.pojo.ApiTokenStatus;
import com.pro.ereportbacken.repository.ApiTokenRepository;
import com.pro.ereportbacken.security.ApiTokenAuthenticationDetails;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.SecurityHashingService;
import com.pro.ereportbacken.security.TenantUserDetails;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class ApiTokenService {

    public static final String TOKEN_PREFIX = "erpt_";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "^erpt_([A-Za-z0-9_-]{22})\\.([A-Za-z0-9_-]{43})$");
    private static final int MAXIMUM_ACTIVE_TOKENS = 20;
    private static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(5);

    private final ApiTokenRepository tokenRepository;
    private final UserAuthorizationCache authorizationCache;
    private final SecurityHashingService hashingService;
    private final RedisFixedWindowRateLimiter rateLimiter;
    private final int rateLimitPerMinute;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(ApiTokenRepository tokenRepository,
                           UserAuthorizationCache authorizationCache,
                           SecurityHashingService hashingService,
                           RedisFixedWindowRateLimiter rateLimiter,
                           @Value("${app.security.api-token.rate-limit.per-minute:300}") int rateLimitPerMinute) {
        if (rateLimitPerMinute <= 0) {
            throw new IllegalArgumentException("API Token rate limit must be positive.");
        }
        this.tokenRepository = tokenRepository;
        this.authorizationCache = authorizationCache;
        this.hashingService = hashingService;
        this.rateLimiter = rateLimiter;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Transactional(readOnly = true)
    public List<ApiTokenDTO> listOwnTokens() {
        TenantUserDetails principal = requireInteractivePrincipal();
        LocalDateTime now = UtcTime.now();
        return tokenRepository.findTop100ByTenantIdAndMembershipIdOrderByCreatedAtDesc(
                        principal.tenantId(), principal.membershipId())
                .stream()
                .map(token -> toDto(token, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApiTokenPermissionOptionDTO> ownPermissionOptions() {
        TenantUserDetails principal = requireInteractivePrincipal();
        Set<String> permissionNames = authorizationCache.get(principal.tenantId(), principal.membershipId())
                .authorities();
        return permissionNames.stream()
                .filter(PermissionCatalog::assignableToApiToken)
                .map(PermissionCatalog::require)
                .sorted(java.util.Comparator.comparing(PermissionCatalog.Entry::displayName)
                        .thenComparing(PermissionCatalog.Entry::name))
                .map(entry -> new ApiTokenPermissionOptionDTO(
                        entry.name(), entry.displayName(), entry.description(), entry.accessMode(), entry.riskLevel()))
                .toList();
    }

    @Transactional
    public ApiTokenCreatedDTO create(ApiTokenCreateRequestDTO request) {
        TenantUserDetails principal = requireInteractivePrincipal();
        LocalDateTime now = UtcTime.now();
        LocalDateTime expiresAt = fromInstant(request.expiresAt());
        if (expiresAt != null && expiresAt.isAfter(now.plusYears(10))) {
            throw new IllegalArgumentException("API Token 有效期最长为10年。" );
        }
        Set<String> selectedPermissions = normalizePermissions(request.permissionNames());
        Set<String> currentPermissions = authorizationCache.get(principal.tenantId(), principal.membershipId())
                .authorities();
        Set<String> exceeded = new LinkedHashSet<>(selectedPermissions);
        exceeded.removeAll(currentPermissions);
        if (!exceeded.isEmpty()) {
            throw new AccessDeniedException("API Token 不能持有当前用户尚未拥有的权限。" );
        }
        if (tokenRepository.countActiveByOwner(principal.tenantId(), principal.membershipId(), now)
                >= MAXIMUM_ACTIVE_TOKENS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前账号最多保留" + MAXIMUM_ACTIVE_TOKENS + "个有效 API Token，请先撤销不再使用的 Token。"
            );
        }

        String lookupKey = randomUrlSafe(16);
        String secret = randomUrlSafe(32);
        String rawToken = TOKEN_PREFIX + lookupKey + "." + secret;
        ApiToken token = new ApiToken();
        token.setTenantId(principal.tenantId());
        token.setMembershipId(principal.membershipId());
        token.setName(request.name().trim());
        token.setLookupKey(lookupKey);
        token.setSecretHash(hashingService.protectedHash("api-token", rawToken));
        token.setPermissionNames(new LinkedHashSet<>(selectedPermissions));
        token.setCreatedAt(now);
        token.setExpiresAt(expiresAt);
        ApiToken saved = tokenRepository.save(token);
        log.info("API Token created; tokenId={}, membershipId={}, expiresAt={}, permissions={}",
                saved.getId(), saved.getMembershipId(), saved.getExpiresAt(), selectedPermissions.size());
        return new ApiTokenCreatedDTO(toDto(saved, now), rawToken);
    }

    @Transactional
    public void revokeOwnToken(UUID tokenId) {
        TenantUserDetails principal = requireInteractivePrincipal();
        tokenRepository.findByTenantIdAndMembershipIdAndId(
                        principal.tenantId(), principal.membershipId(), tokenId)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(UtcTime.now());
                    token.setRevokeReason("用户主动撤销");
                    tokenRepository.save(token);
                    log.info("API Token revoked; tokenId={}, membershipId={}", token.getId(), token.getMembershipId());
                });
    }

    @Transactional
    public int revokeAllForCredentialChange(UUID tenantId, UUID membershipId, String reason) {
        int revoked = tokenRepository.revokeAllByOwner(
                tenantId,
                membershipId,
                UtcTime.now(),
                limitReason(reason)
        );
        if (revoked > 0) {
            log.info("API Tokens revoked after credential change; membershipId={}, count={}", membershipId, revoked);
        }
        return revoked;
    }

    @Transactional(readOnly = true)
    public AuthenticatedApiToken authenticate(String rawToken) {
        Matcher matcher = TOKEN_PATTERN.matcher(String.valueOf(rawToken));
        if (!matcher.matches()) {
            throw unauthorized();
        }
        String lookupKey = matcher.group(1);
        rateLimiter.require(
                "api-token:minute",
                lookupKey,
                1,
                rateLimitPerMinute,
                Duration.ofMinutes(1),
                "API 调用过于频繁，请稍后重试。"
        );
        ApiToken token = tokenRepository.findByLookupKey(lookupKey).orElseThrow(this::unauthorized);
        LocalDateTime now = UtcTime.now();
        if (token.getRevokedAt() != null
                || (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now))
                || !hashingService.constantTimeEquals(
                        token.getSecretHash(), hashingService.protectedHash("api-token", rawToken))) {
            throw unauthorized();
        }
        return new AuthenticatedApiToken(
                token.getId(), token.getTenantId(), token.getMembershipId(), token.getName(),
                token.getExpiresAt(), Set.copyOf(token.getPermissionNames())
        );
    }

    @Transactional
    public void markUsed(UUID tokenId) {
        LocalDateTime usedAt = UtcTime.now();
        tokenRepository.touchLastUsedAt(
                tokenId,
                usedAt,
                usedAt.minus(LAST_USED_WRITE_INTERVAL)
        );
    }

    public int rateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    private Set<String> normalizePermissions(Set<String> permissionNames) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permissionName : permissionNames) {
            if (!StringUtils.hasText(permissionName)) {
                throw new IllegalArgumentException("API Token 权限不能为空。" );
            }
            String name = permissionName.trim();
            PermissionCatalog.require(name);
            if (!PermissionCatalog.assignableToApiToken(name)) {
                throw new IllegalArgumentException("该系统能力不能授予 API Token。" );
            }
            normalized.add(name);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一项 API 权限。" );
        }
        return Set.copyOf(normalized);
    }

    private TenantUserDetails requireInteractivePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantUserDetails principal)) {
            throw new AccessDeniedException("需要交互式登录后管理 API Token。" );
        }
        if (authentication.getDetails() instanceof ApiTokenAuthenticationDetails) {
            throw new AccessDeniedException("API Token 不能创建或管理其他 API Token。" );
        }
        return principal;
    }

    private ApiTokenDTO toDto(ApiToken token, LocalDateTime now) {
        ApiTokenStatus status = token.getRevokedAt() != null
                ? ApiTokenStatus.REVOKED
                : token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now)
                ? ApiTokenStatus.EXPIRED
                : ApiTokenStatus.ACTIVE;
        return new ApiTokenDTO(
                token.getId(),
                token.getName(),
                TOKEN_PREFIX + token.getLookupKey().substring(0, 8) + "...",
                toInstant(token.getCreatedAt()),
                toInstant(token.getExpiresAt()),
                toInstant(token.getLastUsedAt()),
                toInstant(token.getRevokedAt()),
                status,
                token.getPermissionNames()
        );
    }

    private String randomUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String limitReason(String reason) {
        String normalized = StringUtils.hasText(reason) ? reason.trim() : "凭据变更";
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private LocalDateTime fromInstant(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Token 无效、已过期或已撤销。" );
    }

    public record AuthenticatedApiToken(
            UUID tokenId,
            UUID tenantId,
            UUID membershipId,
            String name,
            LocalDateTime expiresAt,
            Set<String> permissionNames
    ) {
        public AuthenticatedApiToken {
            permissionNames = Set.copyOf(permissionNames);
        }
    }
}
