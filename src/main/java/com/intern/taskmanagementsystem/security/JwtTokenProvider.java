package com.intern.taskmanagementsystem.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT token provider: tạo, parse và validate JWT.
 * Dùng JJWT 0.12.x.
 * Demo kiến thức: @Value (spring-property-config), static utility pattern.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes())
        );
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Tạo JWT từ CustomUserDetails.
     */
    public String generateToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())            // jti claim (JWT ID)
                .subject(userDetails.getUsername())          // email làm subject
                .claim("userId", userDetails.getId())
                .claim("tokenVersion", userDetails.getTokenVersion())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Lấy email (subject) từ token.
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Lấy jti từ token (hỗ trợ cả khi token đã hết hạn).
     */
    public String getJtiFromToken(String token) {
        Claims claims = parseClaimsAllowExpired(token);
        return claims != null ? claims.getId() : null;
    }

    /**
     * Lấy userId từ token.
     */
    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Lấy tokenVersion từ token.
     */
    public Integer getTokenVersionFromToken(String token) {
        return parseClaims(token).get("tokenVersion", Integer.class);
    }

    /**
     * Validate token — trả true nếu hợp lệ.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Parse claims của token an toàn, hỗ trợ lấy thông tin kể cả khi token đã hết hạn (ExpiredJwtException).
     * Trả về null nếu token bị lỗi chữ ký hoặc sai cấu trúc.
     */
    public Claims parseClaimsAllowExpired(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    // ----------------------------------------------------------------
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
