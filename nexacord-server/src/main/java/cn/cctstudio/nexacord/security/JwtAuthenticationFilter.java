package cn.cctstudio.nexacord.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);
        try {
            if (StringUtils.hasText(jwt)) {
                if (!jwtTokenProvider.validateToken(jwt)) {
                    if (!isAuthenticationOptionalRequest(request)) {
                        writeInvalidTokenResponse(response);
                        return;
                    }
                } else {
                    String username = jwtTokenProvider.getUsernameFromToken(jwt);
                    String sessionId = jwtTokenProvider.getSessionIdFromToken(jwt);

                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                    if (userDetails instanceof cn.cctstudio.nexacord.model.User user && !isCurrentSession(user, sessionId)) {
                        writeSessionReplacedResponse(response, user.getActiveDeviceName());
                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            SecurityContextHolder.clearContext();
            if (StringUtils.hasText(jwt) && !isAuthenticationOptionalRequest(request) && !response.isCommitted()) {
                writeInvalidTokenResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCurrentSession(cn.cctstudio.nexacord.model.User user, String sessionId) {
        return !StringUtils.hasText(user.getActiveSessionId()) || Objects.equals(user.getActiveSessionId(), sessionId);
    }

    private void writeSessionReplacedResponse(HttpServletResponse response, String deviceName) throws IOException {
        String normalizedDeviceName = StringUtils.hasText(deviceName) ? deviceName : "另一台设备";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Nexacord-Auth-Reason", "SESSION_REPLACED");
        response.setHeader("X-Nexacord-Auth-Device", URLEncoder.encode(normalizedDeviceName, StandardCharsets.UTF_8));
        response.getWriter().write("""
                {"error":"你的账号已在其他设备登录。","reason":"SESSION_REPLACED","deviceName":"%s"}
                """.formatted(escapeJson(normalizedDeviceName)).trim());
    }

    private void writeInvalidTokenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Nexacord-Auth-Reason", "TOKEN_INVALID_OR_EXPIRED");
        response.getWriter().write("""
                {"error":"登录状态已过期，请刷新后重试。","reason":"TOKEN_INVALID_OR_EXPIRED"}
                """.trim());
    }

    private boolean isAuthenticationOptionalRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = getRequestPath(request);
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/public/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/ws/");
    }

    private String getRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
