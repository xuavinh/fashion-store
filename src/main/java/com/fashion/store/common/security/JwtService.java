package com.fashion.store.common.security;

import com.fashion.store.modules.user.Role;
import com.fashion.store.modules.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    public static final String TOKEN_TYPE = "token_type";
    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    @Value("${app.security.jwt.access-token-expiration:900000}") // 15 phút
    private long accessTokenExpiration;

    @Value("${app.security.jwt.refresh-token-expiration:604800000}") // 7 ngày
    private long refreshTokenExpiration;

    public JwtService() throws Exception {
        this.privateKey = KeyUtils.loadPrivateKey("keys/local-only/private_key.pem");
        this.publicKey = KeyUtils.loadPublicKey("keys/local-only/public_key.pem");
    }

    public String extractUsername(final String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(final String token, final Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(final String token) {
        try {
            return Jwts.parser()
                    .verifyWith(this.publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (final JwtException e) {
            throw new RuntimeException("Invalid token: " + e.getMessage(), e);
        }
    }

    public String generateToken(final UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(final Map<String, Object> extraClaims, final UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.putIfAbsent(TOKEN_TYPE, ACCESS_TOKEN);

        if (userDetails instanceof User user) {
            if (user.getUserId() != null) {
                claims.put("userId", user.getUserId().toString());
            }
            if (user.getRole() != null) {
                claims.put("role", user.getRole().name());
            }
            if (user.getFullName() != null) {
                claims.put("fullName", user.getFullName());
            }
        }

        return buildToken(userDetails.getUsername(), claims, this.accessTokenExpiration);
    }

    public String generateRefreshToken(final UserDetails userDetails) {
        final Map<String, Object> claims = Map.of(TOKEN_TYPE, REFRESH_TOKEN);
        return buildToken(userDetails.getUsername(), claims, this.refreshTokenExpiration);
    }

    public String generateRefreshToken(final String username) {
        final Map<String, Object> claims = Map.of(TOKEN_TYPE, REFRESH_TOKEN);
        return buildToken(username, claims, this.refreshTokenExpiration);
    }

    public String generateAccessToken(final User user) {
        return generateToken(user);
    }

    public String generateAccessToken(final String username) {
        final Map<String, Object> claims = Map.of(TOKEN_TYPE, ACCESS_TOKEN);
        return buildToken(username, claims, this.accessTokenExpiration);
    }

    public String buildToken(final String username, final Map<String, Object> claims, final long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(this.privateKey)
                .compact();
    }

    public boolean isTokenValid(final String token, final UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenValid(final String token, final String expectedUsername) {
        final String username = extractUsername(token);
        return username != null && username.equals(expectedUsername) && !isTokenExpired(token);
    }

    public boolean isTokenValid(final String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(final String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get(TOKEN_TYPE, String.class);
            return ACCESS_TOKEN.equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(final String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(final String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String refreshAccessToken(final String refreshToken) {
        final Claims claims = extractAllClaims(refreshToken);

        if (!REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE, String.class))) {
            throw new RuntimeException("Invalid token type: Token này không phải là Refresh Token");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new RuntimeException("Refresh token đã hết hạn");
        }

        final String username = claims.getSubject();
        return generateAccessToken(username);
    }

    public UUID extractUserId(final String token) {
        Claims claims = extractAllClaims(token);
        String userIdStr = claims.get("userId", String.class);
        if (userIdStr != null && !userIdStr.isBlank()) {
            return UUID.fromString(userIdStr);
        }
        return null;
    }

    public String extractEmail(final String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        return email != null ? email : claims.getSubject();
    }

    public String extractFullName(final String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("fullName", String.class);
    }

    public Role extractRole(final String token) {
        Claims claims = extractAllClaims(token);
        String roleStr = claims.get("role", String.class);
        if (roleStr == null) {
            return Role.CUSTOMER;
        }
        try {
            return Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Role.CUSTOMER;
        }
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
