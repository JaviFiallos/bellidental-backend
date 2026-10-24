package com.bellidental.security;

import com.bellidental.shared.time.ClinicClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Service
public class DocumentLinkService {

    public static final String PURPOSE_DOCUMENT = "document";
    public static final String DOC_PRESCRIPTION = "prescription";
    public static final String DOC_RECEIPT = "receipt";

    private final String secretKey;
    private final long ttlSeconds;
    private final ClinicClock clinicClock;

    public DocumentLinkService(
            @Value("${application.security.jwt.secret}") String secretKey,
            @Value("${application.security.document-link.ttl-seconds:300}") long ttlSeconds,
            ClinicClock clinicClock
    ) {
        this.secretKey = secretKey;
        this.ttlSeconds = ttlSeconds;
        this.clinicClock = clinicClock;
    }

    public DocumentLinkResponse generateLink(String doc, UUID id, String pathPattern) {
        OffsetDateTime now = clinicClock.now();
        OffsetDateTime expiresAt = now.plusSeconds(ttlSeconds);

        String token = Jwts.builder()
                .subject(id.toString())
                .claim("purpose", PURPOSE_DOCUMENT)
                .claim("doc", doc)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(expiresAt.toInstant()))
                .signWith(getSigningKey())
                .compact();

        String url = String.format(pathPattern, id, token);
        return new DocumentLinkResponse(url, expiresAt);
    }

    public boolean verify(String token, String doc, UUID id) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .clock(() -> Date.from(clinicClock.now().toInstant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String purpose = claims.get("purpose", String.class);
            String docClaim = claims.get("doc", String.class);
            String subject = claims.getSubject();

            return PURPOSE_DOCUMENT.equals(purpose)
                    && doc.equals(docClaim)
                    && id.toString().equals(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
