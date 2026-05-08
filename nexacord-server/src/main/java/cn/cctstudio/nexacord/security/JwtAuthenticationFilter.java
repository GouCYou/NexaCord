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
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
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
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
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
