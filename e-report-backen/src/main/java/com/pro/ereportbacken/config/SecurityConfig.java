package com.pro.ereportbacken.config;

import com.pro.ereportbacken.exception.ApiErrorResponseWriter;
import com.pro.ereportbacken.security.PermissionCatalog;
import com.pro.ereportbacken.security.ApiTokenAuthenticationFilter;
import com.pro.ereportbacken.security.AuthSessionAuthenticationFilter;
import com.pro.ereportbacken.security.DeviceRequestSignatureFilter;
import com.pro.ereportbacken.security.PasswordChangeRequiredFilter;
import com.pro.ereportbacken.security.WebOriginValidationFilter;
import com.pro.ereportbacken.security.McpOriginValidationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.CorsFilter;
import jakarta.servlet.http.Cookie;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final String[] allowedOrigins;
    private final AuthSessionAuthenticationFilter authSessionAuthenticationFilter;
    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;
    private final DeviceRequestSignatureFilter deviceRequestSignatureFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final WebOriginValidationFilter webOriginValidationFilter;
    private final McpOriginValidationFilter mcpOriginValidationFilter;
    private final boolean secureCookie;

    public SecurityConfig(@Value("${api.cors.allowed-origins:http://192.168.101.126:5173}") String[] allowedOrigins,
                          AuthSessionAuthenticationFilter authSessionAuthenticationFilter,
                          ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
                          ApiErrorResponseWriter apiErrorResponseWriter,
                          DeviceRequestSignatureFilter deviceRequestSignatureFilter,
                          PasswordChangeRequiredFilter passwordChangeRequiredFilter,
                          WebOriginValidationFilter webOriginValidationFilter,
                          @Value("${app.security.cookie-secure:true}") boolean secureCookie) {
        this.allowedOrigins = allowedOrigins;
        this.authSessionAuthenticationFilter = authSessionAuthenticationFilter;
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
        this.deviceRequestSignatureFilter = deviceRequestSignatureFilter;
        this.passwordChangeRequiredFilter = passwordChangeRequiredFilter;
        this.webOriginValidationFilter = webOriginValidationFilter;
        this.mcpOriginValidationFilter = new McpOriginValidationFilter(allowedOrigins, apiErrorResponseWriter);
        this.secureCookie = secureCookie;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.withObjectPostProcessor(
                        new ObjectPostProcessor<HeaderWriterFilter>() {
                            @Override
                            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                                filter.setShouldWriteHeadersEagerly(true);
                                return filter;
                            }
                        }))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(this::requiresWebCsrf))
                .authorizeHttpRequests(authz -> authz
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/error",
                                "/favicon.ico",
                                "/auth/challenge",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/csrf",
                                "/auth/logout",
                                "/observability/policy",
                                "/observability/events/batch"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/*.html", "/*.json", "/*.js", "/*.css", "/static/**", "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/app-updates/latest", "/app-updates/latest/apk", "/app-updates/releases/*/apk").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/app-updates/latest/apk", "/app-updates/releases/*/apk").permitAll()
                        .requestMatchers(HttpMethod.GET, "/app-updates/ota", "/app-updates/ota/assets/*/*").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/app-updates/ota/assets/*/*").permitAll()
                        .requestMatchers("/file-transfers/uploads/**").authenticated()
                        .requestMatchers("/api-tokens/**").authenticated()
                        .requestMatchers("/mcp").hasAuthority(ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY)

                        .requestMatchers(HttpMethod.POST, "/work-orders")
                        .hasAnyAuthority(PermissionCatalog.reportPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.GET, "/location/reverse-geocode")
                        .hasAnyAuthority(PermissionCatalog.reportPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.GET, "/work-orders/statistics", "/work-orders/statistics/scopes")
                        .hasAuthority(PermissionCatalog.DASHBOARD_STATISTICS_VIEW)
                        .requestMatchers(HttpMethod.GET, "/work-orders/search")
                        .hasAnyAuthority(PermissionCatalog.viewPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.GET, "/work-orders/export")
                        .hasAnyAuthority(PermissionCatalog.exportPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/responses")
                        .hasAnyAuthority(PermissionCatalog.responseSubmitPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/claims")
                        .hasAnyAuthority(PermissionCatalog.claimPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/assignments")
                        .hasAnyAuthority(PermissionCatalog.assignmentCreatePermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/assignments/*/acceptance")
                        .hasAnyAuthority(PermissionCatalog.assignmentAcceptPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/assignments/*/rejection")
                        .hasAnyAuthority(PermissionCatalog.assignmentRejectPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/reassignments")
                        .hasAnyAuthority(PermissionCatalog.assignmentReassignPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/verifications")
                        .hasAnyAuthority(merge(List.of(
                                PermissionCatalog.verificationApprovePermissions(),
                                PermissionCatalog.verificationRejectPermissions()
                        )))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/holds")
                        .hasAnyAuthority(PermissionCatalog.holdPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/resumptions")
                        .hasAnyAuthority(PermissionCatalog.resumePermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.POST, "/work-orders/*/cancellations")
                        .hasAnyAuthority(merge(List.of(
                                PermissionCatalog.cancelPermissions(),
                                PermissionCatalog.withdrawPermissions()
                        )))
                        .requestMatchers(HttpMethod.GET, "/work-orders/attachments/*/*", "/work-orders/attachments/*/file/*", "/work-orders/*/attachments/zip")
                        .hasAnyAuthority(PermissionCatalog.attachmentReadPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.HEAD, "/work-orders/attachments/*/*", "/work-orders/attachments/*/file/*", "/work-orders/*/attachments/zip")
                        .hasAnyAuthority(PermissionCatalog.attachmentReadPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.GET, "/work-orders/*/events", "/work-orders/*/responses")
                        .hasAnyAuthority(PermissionCatalog.historyPermissions().toArray(String[]::new))
                        .requestMatchers(HttpMethod.GET, "/work-orders/*")
                        .hasAnyAuthority(PermissionCatalog.viewPermissions().toArray(String[]::new))

                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录。"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                apiErrorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "当前账号没有执行该操作的权限。"))
                )
                .logout(AbstractHttpConfigurer::disable)
                .addFilterAfter(webOriginValidationFilter, CorsFilter.class)
                .addFilterAfter(mcpOriginValidationFilter, CorsFilter.class)
                .addFilterBefore(apiTokenAuthenticationFilter, LogoutFilter.class)
                .addFilterBefore(authSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(deviceRequestSignatureFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(passwordChangeRequiredFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Authorization",
                "Content-Type",
                "Content-Range",
                "Idempotency-Key",
                "MCP-Protocol-Version",
                "Mcp-Method",
                "Mcp-Name",
                "Range",
                "X-Auth-Session-Id",
                "X-Client-App-Build",
                "X-Client-App-Version",
                "X-Client-Network-Type",
                "X-Client-Platform",
                "X-Content-SHA256",
                "X-Device-Key-Id",
                "X-Device-Signature",
                "X-Request-Id",
                "traceparent",
                "tracestate",
                "X-Signature-Created",
                "X-Signature-Expires",
                "X-Signature-Nonce",
                "X-XSRF-TOKEN"
        ));
        configuration.setExposedHeaders(List.of(
                "Content-Disposition",
                "Content-Length",
                "Content-Range",
                "Accept-Ranges",
                "X-File-Range-Download",
                "X-File-Download-Chunk-Size",
                "X-File-Sha256",
                "X-Export-Row-Count",
                "X-Request-Id",
                "MCP-Protocol-Version",
                "X-Trace-Id",
                "traceparent",
                "tracestate"
        ));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/"));
        return repository;
    }

    private boolean requiresWebCsrf(jakarta.servlet.http.HttpServletRequest request) {
        if (Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        return cookies != null && java.util.Arrays.stream(cookies)
                .anyMatch(cookie -> com.pro.ereportbacken.service.AuthSessionService.WEB_COOKIE_NAME.equals(cookie.getName()));
    }

    private static String[] merge(List<Set<String>> groups) {
        Set<String> merged = new java.util.LinkedHashSet<>();
        for (Set<String> group : groups) {
            merged.addAll(group);
        }
        return merged.toArray(String[]::new);
    }
}
