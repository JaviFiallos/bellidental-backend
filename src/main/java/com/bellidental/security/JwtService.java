package com.bellidental.security;

import com.bellidental.persistence.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio para generación y validación de tokens JWT.
 */
@Service
public class JwtService {

    @Value("${application.security.jwt.secret}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    // ──────────────────────────────────────────────────────────
    // Generación
    // ──────────────────────────────────────────────────────────

    public String generateToken(User user, UUID professionalId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        
        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toList());
        claims.put("roles", roles);
        
        if (professionalId != null) {
            claims.put("professionalId", professionalId.toString());
        }
        
        return buildToken(claims, user.getId().toString(), jwtExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public long getExpirationTime() {
        return jwtExpiration / 1000;
    }

    // ──────────────────────────────────────────────────────────
    // Validación
    // ──────────────────────────────────────────────────────────

    public boolean isTokenValid(String token, String subjectId) {
        try {
            final String sub = extractSubject(token);
            return sub.equals(subjectId) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ──────────────────────────────────────────────────────────
    // Extracción de claims
    // ──────────────────────────────────────────────────────────

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
