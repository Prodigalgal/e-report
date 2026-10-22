package com.pro.ereportbacken.controller;

import com.pro.ereportbacken.model.dto.AuthChallengeDTO;
import com.pro.ereportbacken.model.dto.AuthChallengeRequestDTO;
import com.pro.ereportbacken.model.dto.AuthSessionPageDTO;
import com.pro.ereportbacken.model.dto.CurrentUserUpdateRequestDTO;
import com.pro.ereportbacken.model.dto.LoginRequestDTO;
import com.pro.ereportbacken.model.dto.LoginResponseDTO;
import com.pro.ereportbacken.model.dto.PasswordChangeRequestDTO;
import com.pro.ereportbacken.model.dto.PasswordPolicyDTO;
import com.pro.ereportbacken.model.dto.RefreshTokenRequestDTO;
import com.pro.ereportbacken.model.dto.TokenRefreshResponseDTO;
import com.pro.ereportbacken.model.pojo.AuthChallengeMode;
import com.pro.ereportbacken.model.pojo.AuthClientType;
import com.pro.ereportbacken.model.pojo.TrustedDevice;
import com.pro.ereportbacken.model.pojo.User;
import com.pro.ereportbacken.repository.TenantRepository;
import com.pro.ereportbacken.repository.UserRepository;
import com.pro.ereportbacken.security.AuthSessionRequestContext;
import com.pro.ereportbacken.security.ClientRequestMetadataService;
import com.pro.ereportbacken.security.DeviceProofService;
import com.pro.ereportbacken.security.DeviceRequestSignatureService;
import com.pro.ereportbacken.security.TenantUserDetails;
import com.pro.ereportbacken.security.WebSessionCookieFactory;
import com.pro.ereportbacken.service.AuthChallengeService;
import com.pro.ereportbacken.service.AuthenticationHashWorkloadGuard;
import com.pro.ereportbacken.service.AuthRiskService;
import com.pro.ereportbacken.service.AuthSessionQueryService;
import com.pro.ereportbacken.service.AuthSessionService;
import com.pro.ereportbacken.service.AuthenticationRateLimitService;
import com.pro.ereportbacken.service.PasswordPolicy;
import com.pro.ereportbacken.service.TrustedDeviceService;
import com.pro.ereportbacken.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@Log4j2
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AuthChallengeService authChallengeService;
    private final AuthRiskService authRiskService;
    private final AuthSessionService authSessionService;
    private final AuthSessionQueryService authSessionQueryService;
    private final TrustedDeviceService trustedDeviceService;
    private final DeviceProofService deviceProofService;
    private final ClientRequestMetadataService requestMetadataService;
    private final UserService userService;
    private final AuthenticationRateLimitService authenticationRateLimitService;
    private final AuthenticationHashWorkloadGuard authenticationHashWorkloadGuard;
    private final WebSessionCookieFactory webSessionCookieFactory;
    private final PasswordPolicy passwordPolicy;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          TenantRepository tenantRepository,
                          AuthChallengeService authChallengeService,
                          AuthRiskService authRiskService,
                          AuthSessionService authSessionService,
                          AuthSessionQueryService authSessionQueryService,
                          TrustedDeviceService trustedDeviceService,
                          DeviceProofService deviceProofService,
                          ClientRequestMetadataService requestMetadataService,
                          UserService userService,
                           AuthenticationRateLimitService authenticationRateLimitService,
                           AuthenticationHashWorkloadGuard authenticationHashWorkloadGuard,
                           WebSessionCookieFactory webSessionCookieFactory,
                           PasswordPolicy passwordPolicy) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.authChallengeService = authChallengeService;
        this.authRiskService = authRiskService;
        this.authSessionService = authSessionService;
        this.authSessionQueryService = authSessionQueryService;
        this.trustedDeviceService = trustedDeviceService;
        this.deviceProofService = deviceProofService;
        this.requestMetadataService = requestMetadataService;
        this.userService = userService;
        this.authenticationRateLimitService = authenticationRateLimitService;
        this.authenticationHashWorkloadGuard = authenticationHashWorkloadGuard;
        this.webSessionCookieFactory = webSessionCookieFactory;
        this.passwordPolicy = passwordPolicy;
    }

    @PostMapping("/challenge")
    public ResponseEntity<AuthChallengeDTO> createChallenge(@Valid @RequestBody AuthChallengeRequestDTO challengeRequest,
                                                            HttpServletRequest request) {
        ClientRequestMetadataService.RequestMetadata metadata = requestMetadataService.resolve(request);
        authenticationRateLimitService.requireChallenge(
                challengeRequest.getUsername(), deviceSubject(challengeRequest), metadata);
        return ResponseEntity.ok(authChallengeService.createChallenge(challengeRequest, metadata));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> authenticateUser(@Valid @RequestBody LoginRequestDTO loginRequest,
                                                              HttpServletRequest request,
                                                              CsrfToken csrfToken) {
        ClientRequestMetadataService.RequestMetadata metadata = requestMetadataService.resolve(request);
        String deviceSubject = deviceSubject(loginRequest);
        authenticationRateLimitService.requireLogin(loginRequest.getUsername(), deviceSubject, metadata);
        AuthRiskService.RiskAssessment currentRisk = authRiskService.assess(
                loginRequest.getUsername(), metadata, deviceSubject);
        if (currentRisk.mode() == AuthChallengeMode.COOLDOWN) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "登录保护已启用，请在" + currentRisk.retryAfterSeconds() + "秒后重试。"
            );
        }

        try {
            AuthChallengeService.VerifiedChallenge challenge = authChallengeService.verifyAndConsume(loginRequest);
            DeviceProofService.VerifiedDeviceKey verifiedDeviceKey = verifyLoginDeviceProof(loginRequest, challenge);
            Authentication authentication;
            TenantUserDetails principal;
            User appUser;
            try (AuthenticationHashWorkloadGuard.Permit ignored = authenticationHashWorkloadGuard.acquire()) {
                authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequest.getUsername().trim(),
                                loginRequest.getPassword()
                        )
                );
                principal = resolveTenantPrincipal(authentication);
                appUser = userRepository.findByTenantIdAndId(principal.tenantId(), principal.membershipId())
                        .orElseThrow(() -> new IllegalStateException("登录成功后未找到对应账号。"));
                userService.upgradeAuthenticatedPasswordEncodingIfNeeded(
                        appUser.getTenantId(),
                        appUser.getId(),
                        principal.getPassword(),
                        loginRequest.getPassword());
            }

            TrustedDevice trustedDevice = loginRequest.getClientType() == AuthClientType.ANDROID
                    ? trustedDeviceService.registerOrRefresh(appUser, loginRequest.getDeviceContext(), verifiedDeviceKey)
                    : null;
            AuthSessionService.IssuedSession issuedSession = loginRequest.getClientType() == AuthClientType.ANDROID
                    ? authSessionService.createAndroidSession(appUser, trustedDevice, metadata)
                    : authSessionService.createWebSession(
                            appUser,
                            metadata,
                            loginRequest.getWebEnvironment(),
                            rememberWebSession(loginRequest));
            authRiskService.registerSuccess(loginRequest.getUsername(), metadata, deviceSubject);

            LoginResponseDTO response = buildLoginResponse(authentication, issuedSession);
            csrfToken.getToken();
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
            if (issuedSession.webCookieValue() != null) {
                responseBuilder.header(
                        HttpHeaders.SET_COOKIE,
                        webSessionCookieFactory.issue(
                                request,
                                issuedSession.webCookieValue(),
                                rememberWebSession(loginRequest)
                        ).toString()
                );
            }
            return responseBuilder.body(response);
        } catch (DisabledException | BadCredentialsException exception) {
            AuthRiskService.RiskAssessment risk = authRiskService.registerFailure(
                    loginRequest.getUsername(), metadata, deviceSubject);
            log.warn("Authentication rejected; failures={}, retryAfterSeconds={}, network={}",
                    risk.failureCount(), risk.retryAfterSeconds(), metadata.networkHash());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确，请重新输入。");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, friendlyLoginValidationMessage(exception.getMessage()));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Login failed unexpectedly; network={}", metadata.networkHash(), exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理登录请求，请稍后重试。");
        }
    }

    @GetMapping("/csrf")
    public ResponseEntity<java.util.Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(java.util.Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO> currentUser(Authentication authentication,
                                                        HttpServletRequest request) {
        requireAuthenticated(authentication);
        return ResponseEntity.ok(buildCurrentUserResponse(authentication, request));
    }

    @GetMapping("/password-policy")
    public ResponseEntity<PasswordPolicyDTO> passwordPolicy(Authentication authentication) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        return ResponseEntity.ok(passwordPolicy.dtoForScope(principal.orgUnitId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponseDTO> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDTO refreshRequest,
            HttpServletRequest request) {
        UUID sessionId = AuthSessionRequestContext.requireSessionId(request);
        String deviceKeyId = String.valueOf(
                request.getAttribute(AuthSessionRequestContext.DEVICE_KEY_ID_ATTRIBUTE));
        AuthSessionService.RefreshedSession refreshed = authSessionService.rotateRefreshToken(
                sessionId, refreshRequest.getRefreshToken());
        User user = userRepository.findByTenantIdAndId(
                        refreshed.session().getTenantId(), refreshed.session().getMembershipId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录。"));
        if (!refreshed.session().getAuthorizationVersion().equals(user.getAuthorizationVersion())) {
            authSessionService.revoke(sessionId, user.getId(), "用户权限版本已变化");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号权限已变化，请重新登录。");
        }
        TrustedDevice device = trustedDeviceService.requireActive(
                user.getTenantId(), user.getId(), deviceKeyId);
        com.pro.ereportbacken.security.AuthTokenService.IssuedAccessToken accessToken =
                authSessionService.issueAccessToken(refreshed.session(), user, device);
        return ResponseEntity.ok(new TokenRefreshResponseDTO(
                sessionId,
                accessToken.value(),
                refreshed.refreshToken(),
                accessToken.expiresInSeconds()
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<LoginResponseDTO> updateCurrentUser(@Valid @RequestBody CurrentUserUpdateRequestDTO updateRequest,
                                                              Authentication authentication,
                                                              HttpServletRequest request) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        userService.updateCurrentUserProfile(principal.tenantId(), principal.getUsername(), updateRequest);
        return ResponseEntity.ok(buildCurrentUserResponse(authentication, request));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequestDTO passwordRequest,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        userService.changeCurrentPassword(
                principal.tenantId(),
                principal.membershipId(),
                AuthSessionRequestContext.requireSessionId(request),
                passwordRequest.getCurrentPassword(),
                passwordRequest.getNewPassword()
        );
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (AuthSessionRequestContext.clientType(request) == AuthClientType.WEB) {
            response.header(HttpHeaders.SET_COOKIE, webSessionCookieFactory.clear(request).toString());
        }
        return response.build();
    }

    @GetMapping("/sessions/page")
    public ResponseEntity<AuthSessionPageDTO> pagedSessions(Authentication authentication,
                                                             HttpServletRequest request,
                                                             @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                                                             Pageable pageable) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        return ResponseEntity.ok(authSessionQueryService.page(
                principal.tenantId(),
                principal.membershipId(),
                AuthSessionRequestContext.requireSessionId(request),
                pageable
        ));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        UUID currentSessionId = AuthSessionRequestContext.requireSessionId(request);
        if (currentSessionId.equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请使用退出登录关闭当前会话。");
        }
        authSessionService.revoke(sessionId, principal.membershipId(), "用户主动撤销会话");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/others")
    public ResponseEntity<Void> revokeOtherSessions(Authentication authentication,
                                                    HttpServletRequest request) {
        requireAuthenticated(authentication);
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        authSessionService.revokeAllOther(
                principal.tenantId(),
                principal.membershipId(),
                AuthSessionRequestContext.requireSessionId(request),
                "用户撤销其他登录会话"
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails principal) {
            authSessionService.revoke(
                    AuthSessionRequestContext.requireSessionId(request),
                    principal.membershipId(),
                    "用户退出登录"
            );
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, webSessionCookieFactory.clear(request).toString())
                .build();
    }

    private DeviceProofService.VerifiedDeviceKey verifyLoginDeviceProof(
            LoginRequestDTO loginRequest,
            AuthChallengeService.VerifiedChallenge challenge) {
        if (loginRequest.getClientType() == AuthClientType.WEB) {
            if (loginRequest.getWebEnvironment() == null) {
                throw new IllegalArgumentException("WEB 环境信息不完整。");
            }
            if (loginRequest.getDeviceContext() != null
                    || StringUtils.hasText(loginRequest.getDevicePublicKey())
                    || StringUtils.hasText(loginRequest.getDeviceSignature())
                    || StringUtils.hasText(loginRequest.getDeviceKeyId())) {
                throw new IllegalArgumentException("WEB 登录不能提交 Android 设备凭据。");
            }
            return null;
        }
        if (loginRequest.getDeviceContext() == null
                || !StringUtils.hasText(loginRequest.getDeviceKeyId())
                || !StringUtils.hasText(loginRequest.getDevicePublicKey())
                || !StringUtils.hasText(loginRequest.getDeviceSignature())) {
            throw new IllegalArgumentException("Android 设备证明不完整。");
        }
        if (loginRequest.getWebEnvironment() != null) {
            throw new IllegalArgumentException("Android 客户端不能提交 WEB 环境信息。");
        }
        if (Boolean.TRUE.equals(loginRequest.getRememberMe())) {
            throw new IllegalArgumentException("Android 客户端不能提交 WEB 持久登录选项。");
        }
        if (!loginRequest.getDeviceContext().getInstallationId().equals(loginRequest.getClientInstanceId())) {
            throw new IllegalArgumentException("Android 安装实例信息不一致。");
        }
        DeviceProofService.VerifiedDeviceKey verifiedKey = deviceProofService.requireKey(
                loginRequest.getDevicePublicKey(), loginRequest.getDeviceKeyId());
        deviceProofService.requireValidSignature(
                verifiedKey.publicKey(),
                authChallengeService.loginDeviceProofPayload(loginRequest, challenge.serverNonce()),
                loginRequest.getDeviceSignature()
        );
        return verifiedKey;
    }

    private boolean rememberWebSession(LoginRequestDTO loginRequest) {
        return loginRequest.getRememberMe() == null || Boolean.TRUE.equals(loginRequest.getRememberMe());
    }

    private LoginResponseDTO buildLoginResponse(Authentication authentication,
                                                AuthSessionService.IssuedSession issuedSession) {
        LoginResponseDTO response = buildUserResponse(authentication);
        response.setSessionId(issuedSession.session().getId());
        response.setClientType(issuedSession.session().getClientType());
        response.setPasswordChangeRequired(issuedSession.session().getPasswordChangeRequired());
        if (issuedSession.accessToken() != null) {
            response.setToken(issuedSession.accessToken().value());
            response.setAccessTokenExpiresInSeconds(issuedSession.accessToken().expiresInSeconds());
            response.setRefreshToken(issuedSession.refreshToken());
        }
        return response;
    }

    private LoginResponseDTO buildCurrentUserResponse(Authentication authentication, HttpServletRequest request) {
        LoginResponseDTO response = buildUserResponse(authentication);
        response.setSessionId(AuthSessionRequestContext.requireSessionId(request));
        response.setClientType(AuthSessionRequestContext.clientType(request));
        response.setPasswordChangeRequired(Boolean.TRUE.equals(
                request.getAttribute(AuthSessionRequestContext.PASSWORD_CHANGE_REQUIRED_ATTRIBUTE)));
        return response;
    }

    private LoginResponseDTO buildUserResponse(Authentication authentication) {
        TenantUserDetails principal = resolveTenantPrincipal(authentication);
        User appUser = userRepository.findByTenantIdAndId(principal.tenantId(), principal.membershipId())
                .orElseThrow(() -> new IllegalStateException("登录成功后未找到对应账号。"));
        com.pro.ereportbacken.model.pojo.Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new IllegalStateException("登录成功后未找到账号所属市公司。"));
        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        LoginResponseDTO response = new LoginResponseDTO();
        response.setUsername(principal.getUsername());
        response.setName(appUser.getName());
        response.setPermissions(authorities);
        response.setMembershipId(appUser.getId());
        response.setTenantId(tenant.getId());
        response.setTenantCode(tenant.getCode());
        response.setTenantName(tenant.getName());
        response.setOrgUnitId(appUser.getOrgUnitId());
        response.setAuthorizationVersion(appUser.getAuthorizationVersion());
        response.setPhone(appUser.getPhone());
        response.setEmail(appUser.getEmail());
        return response;
    }

    private String deviceSubject(LoginRequestDTO request) {
        if (StringUtils.hasText(request.getDeviceKeyId())) {
            return request.getDeviceKeyId().trim();
        }
        if (request.getWebEnvironment() != null
                && StringUtils.hasText(request.getWebEnvironment().getFingerprintHash())) {
            return request.getWebEnvironment().getFingerprintHash().trim();
        }
        return StringUtils.hasText(request.getClientInstanceId())
                ? request.getClientInstanceId().trim()
                : "unknown-device";
    }

    private String deviceSubject(AuthChallengeRequestDTO request) {
        if (StringUtils.hasText(request.getDeviceKeyId())) {
            return request.getDeviceKeyId().trim();
        }
        if (StringUtils.hasText(request.getWebFingerprintHash())) {
            return request.getWebFingerprintHash().trim();
        }
        return StringUtils.hasText(request.getClientInstanceId())
                ? request.getClientInstanceId().trim()
                : "unknown-device";
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录。");
        }
    }

    private String friendlyLoginValidationMessage(String rawMessage) {
        if (rawMessage != null && rawMessage.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
            return rawMessage;
        }
        return "登录信息校验失败，请刷新安全挑战后重试。";
    }

    private TenantUserDetails resolveTenantPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof TenantUserDetails tenantUserDetails) {
            return tenantUserDetails;
        }
        if (principal instanceof UserDetails) {
            throw new IllegalStateException("Authenticated principal is missing tenant context.");
        }
        throw new IllegalStateException("Authenticated principal is invalid.");
    }
}
