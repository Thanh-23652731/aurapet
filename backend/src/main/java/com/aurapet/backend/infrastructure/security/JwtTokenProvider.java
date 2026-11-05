package com.aurapet.backend.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    /** Các secret từng bị đưa lên GitHub — không bao giờ được dùng lại. */
    private static final Set<String> LEAKED_SECRETS = Set.of(
            "aurapet_super_secret_jwt_key_2026_very_long_secure_token_512bits_aiot",
            "d774dfc564fce6b2d9d56de4985e8152f0e3bb944f53c1f87bb15bc2d4a11ff2");

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${aurapet.jwt.secret:}") String secret,
            @Value("${aurapet.jwt.expiration-ms:604800000}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu AURAPET_JWT_SECRET. Thêm vào file .env một chuỗi ngẫu nhiên >= 32 ký tự "
                    + "(tạo bằng: openssl rand -hex 32 hoặc PowerShell: [guid]::NewGuid().ToString('N')*2)");
        }
        if (LEAKED_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException("AURAPET_JWT_SECRET đang dùng giá trị đã bị lộ công khai trên GitHub. Hãy tạo secret mới.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("AURAPET_JWT_SECRET quá ngắn (cần >= 32 ký tự).");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT không hợp lệ: {}", e.getMessage());
            return false;
        }
    }
}
