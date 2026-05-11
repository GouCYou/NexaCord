package cn.cctstudio.nexacord.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final CustomUserDetailsService customUserDetailsService = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidBearerTokenOnProtectedRouteReturnsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (_request, _response) -> chainCalled.set(true);

        when(jwtTokenProvider.validateToken("expired-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("X-Nexacord-Auth-Reason")).isEqualTo("TOKEN_INVALID_OR_EXPIRED");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("TOKEN_INVALID_OR_EXPIRED");
        verifyNoInteractions(customUserDetailsService);
    }

    @Test
    void invalidBearerTokenOnPublicAuthRouteDoesNotBlockRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (_request, _response) -> chainCalled.set(true);

        when(jwtTokenProvider.validateToken("expired-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verifyNoInteractions(customUserDetailsService);
    }
}
