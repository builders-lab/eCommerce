package com.ecommerce.platform.modules.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.ecommerce.platform.modules.users.api.UserIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be set and at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserIdentity user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(String.valueOf(user.id()))
                .claim("email", user.email())
                .claim("displayName", user.displayName())
                .claim("roles", user.roles())
                .claim("userDiscountPercentage", user.userDiscountPercentage())
                .claim("userDiscountStartDate", user.userDiscountStartDate())
                .claim("userDiscountEndDate", user.userDiscountEndDate())
                .claim("enabled", user.enabled())
                .claim("accountNonLocked", user.accountNonLocked())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) throws JwtException {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }

    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        return new AuthenticatedUser(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("displayName", String.class),
                readRoles(claims),
                readBigDecimal(claims, "userDiscountPercentage"),
                readLocalDate(claims, "userDiscountStartDate"),
                readLocalDate(claims, "userDiscountEndDate"),
                readBoolean(claims, "enabled", true),
                readBoolean(claims, "accountNonLocked", true)
        );
    }

    private List<String> readRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> rawRoles) {
            return rawRoles.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private BigDecimal readBigDecimal(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        return switch (value) {
            case null -> BigDecimal.ZERO;
            case BigDecimal bigDecimal -> bigDecimal;
            case Number number -> BigDecimal.valueOf(number.doubleValue());
            default -> new BigDecimal(String.valueOf(value));
        };
    }

    private LocalDate readLocalDate(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        switch (value) {
            case null -> {
                return null;
            }
            case LocalDate localDate -> {
                return localDate;
            }
            case Map<?, ?> dateMap when dateMap.get("year") != null -> {
                int year = Integer.parseInt(String.valueOf(dateMap.get("year")));
                int month = Integer.parseInt(String.valueOf(dateMap.get("monthValue")));
                int day = Integer.parseInt(String.valueOf(dateMap.get("dayOfMonth")));
                return LocalDate.of(year, month, day);
            }
            default -> {
            }
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private boolean readBoolean(Claims claims, String claimName, boolean defaultValue) {
        Object value = claims.get(claimName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
