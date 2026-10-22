package com.pro.ereportbacken.service; // 通常放在 service 包

import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.model.pojo.UserStatus;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.TenantContext;
import com.pro.ereportbacken.security.UserAuthorizationSnapshot;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("userDetailsService") // 给 Bean 一个明确的名称
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserAuthorizationCache userAuthorizationCache;
    private final TenantContext tenantContext;

    public UserDetailsServiceImpl(UserRepository userRepository,
                                  UserAuthorizationCache userAuthorizationCache,
                                  TenantContext tenantContext) {
        this.userRepository = userRepository;
        this.userAuthorizationCache = userAuthorizationCache;
        this.tenantContext = tenantContext;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalizeUsername(username);
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("User is not active: " + normalizedUsername);
        }
        return tenantContext.callInTenant(user.getTenantId(), () -> {
            UserAuthorizationSnapshot snapshot = userAuthorizationCache.get(user.getTenantId(), user.getId());
            if (!matchesCurrentUser(snapshot, user)) {
                snapshot = userAuthorizationCache.snapshot(user);
            }
            return toUserDetails(snapshot, user.getPassword(), true);
        });
    }

    @Transactional(readOnly = true)
    public TenantUserDetails loadForToken(UUID tenantId, UUID membershipId, String username) {
        String normalizedUsername = normalizeUsername(username);
        return tenantContext.callInTenant(tenantId, () -> {
            UserAuthorizationSnapshot snapshot = userAuthorizationCache.get(
                    tenantId,
                    membershipId,
                    normalizedUsername
            );
            return toUserDetails(snapshot, "", true);
        });
    }

    @Transactional(readOnly = true)
    public TenantUserDetails loadForSession(UUID tenantId, UUID membershipId) {
        return tenantContext.callInTenant(tenantId, () ->
                toUserDetails(userAuthorizationCache.get(tenantId, membershipId), "", true));
    }

    @Transactional(readOnly = true)
    public TenantUserDetails loadForApiToken(UUID tenantId,
                                             UUID membershipId,
                                             Set<String> selectedPermissionNames) {
        return tenantContext.callInTenant(tenantId, () -> {
            UserAuthorizationSnapshot snapshot = userAuthorizationCache.get(tenantId, membershipId)
                    .restrictedTo(selectedPermissionNames);
            return toUserDetails(snapshot, "", false);
        });
    }

    private TenantUserDetails toUserDetails(UserAuthorizationSnapshot snapshot,
                                            String password,
                                            boolean includeSystemAuthorities) {
        Set<SimpleGrantedAuthority> authorities = snapshot.authorities(includeSystemAuthorities).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return new TenantUserDetails(
                snapshot.membershipId(),
                snapshot.tenantId(),
                snapshot.orgUnitId(),
                snapshot.authorizationVersion(),
                snapshot.username(),
                password,
                true,
                authorities,
                snapshot
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Username must not be blank.");
        }
        return username.trim();
    }

    private boolean matchesCurrentUser(UserAuthorizationSnapshot snapshot, User user) {
        return snapshot != null
                && Objects.equals(snapshot.membershipId(), user.getId())
                && Objects.equals(snapshot.tenantId(), user.getTenantId())
                && Objects.equals(snapshot.orgUnitId(), user.getOrgUnitId())
                && Objects.equals(snapshot.authorizationVersion(), user.getAuthorizationVersion())
                && Objects.equals(snapshot.username(), user.getUsername())
                && snapshot.systemKey() == user.getSystemKey()
                && snapshot.status() == user.getStatus();
    }
}


