package cn.cctstudio.nexacord.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-token.expiration}")
    private long jwtRefreshTokenExpirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // 推荐：jwt.secret 放 Base64（更标准，也更容易保证长度够 HS512）
        // 如果你现在放的是普通字符串，也能兜底跑起来（fallback）
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (DecodingException ex) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String sessionId, String deviceName) {
        return buildToken(username, sessionId, deviceName, jwtExpirationMs, false);
    }

    public String generateRefreshToken(String username, String sessionId, String deviceName) {
        return buildToken(username, sessionId, deviceName, jwtRefreshTokenExpirationMs, true);
    }

    private String buildToken(String username, String sessionId, String deviceName, long ttlMs, boolean refresh) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                // 可选：加个类型，方便你区分 access/refresh
                .claim("typ", refresh ? "refresh" : "access")
                .claim("sid", sessionId)
                .claim("device", deviceName)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException 包含：过期、签名不对、格式不对等
            return false;
        }
    }

    public Instant getExpirationDateFromToken(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String getSessionIdFromToken(String token) {
        return parseClaims(token).get("sid", String.class);
    }

    public String getDeviceNameFromToken(String token) {
        return parseClaims(token).get("device", String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("typ", String.class));
    }

    private Claims parseClaims(String token) {
        // ✅ 0.12.x 推荐：verifyWith + build + parseSignedClaims
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}
