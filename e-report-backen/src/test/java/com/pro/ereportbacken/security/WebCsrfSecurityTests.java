package com.pro.ereportbacken.security;

import com.pro.ereportbacken.service.AuthSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class WebCsrfSecurityTests {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rejectsCookieAuthenticatedWriteWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .with(user("web-user"))
                        .cookie(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "test-cookie"))
                        .header("X-Client-Platform", "WEB")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCrossOriginWriteEvenWhenCsrfTokenIsPresent() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .with(user("web-user"))
                        .with(csrf().asHeader())
                        .cookie(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "test-cookie"))
                        .header("X-Client-Platform", "WEB")
                        .header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rawCookieTokenSubmittedBySinglePageApplicationPassesCsrfFilter() throws Exception {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/csrf-probe");
        CsrfToken csrfToken = repository.generateToken(request);
        request.setCookies(new Cookie("XSRF-TOKEN", csrfToken.getToken()));
        request.addHeader("X-XSRF-TOKEN", csrfToken.getToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        csrfFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void androidRequestIsNotRejectedByBrowserCsrfMatcher() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("X-Client-Platform", "ANDROID"))
                .andExpect(status().isNoContent());
    }

    @Test
    void staleWebSessionCookieIsClearedAndProtectedEndpointRemainsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "invalid-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(AuthSessionService.WEB_COOKIE_NAME + "="),
                                org.hamcrest.Matchers.containsString("Max-Age=0")
                        )
                )));
    }

    @Test
    void staleWebSessionCookieDoesNotBlockIdempotentLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .with(csrf().asHeader())
                        .cookie(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "invalid-session"))
                        .header("X-Client-Platform", "WEB")
                        .header("Origin", "http://192.168.101.126:5173"))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));
    }

    @Test
    void staleWebSessionCookieDoesNotBlockPublicAuthenticationEndpoint() throws Exception {
        mockMvc.perform(get("/auth/csrf")
                        .cookie(new Cookie(AuthSessionService.WEB_COOKIE_NAME, "invalid-session")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));
    }

}
