package com.bellidental.security;

import com.bellidental.persistence.entity.Role;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.entity.UserRole;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    private User user;
    private UUID professionalId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // A dummy 256-bit (32 bytes) secret key for testing
        ReflectionTestUtils.setField(jwtService, "secretKey", "4b68516d546a576e5a7234753778214125442a472d4b6150645367566b597033");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@bellidental.local");
        
        Role role = new Role();
        role.setCode("ADMIN");
        
        UserRole ur = new UserRole();
        ur.setRole(role);
        user.setUserRoles(Set.of(ur));

        professionalId = UUID.randomUUID();
    }

    @Test
    void generateToken_validUser_createsTokenWithClaims() {
        String token = jwtService.generateToken(user, professionalId);
        
        assertThat(token).isNotBlank();

        String subject = jwtService.extractSubject(token);
        assertThat(subject).isEqualTo(user.getId().toString());

        String email = jwtService.extractClaim(token, claims -> claims.get("email", String.class));
        assertThat(email).isEqualTo("test@bellidental.local");

        @SuppressWarnings("unchecked")
        List<String> roles = jwtService.extractClaim(token, claims -> claims.get("roles", List.class));
        assertThat(roles).containsExactly("ADMIN");

        String profId = jwtService.extractClaim(token, claims -> claims.get("professionalId", String.class));
        assertThat(profId).isEqualTo(professionalId.toString());
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L); // Expired 1 second ago
        String token = jwtService.generateToken(user, null);

        assertThat(jwtService.isTokenValid(token, user.getId().toString())).isFalse();
    }

    @Test
    void extractClaim_alteredSignature_throwsSignatureException() {
        String token = jwtService.generateToken(user, null);
        String alteredToken = token + "xyz";

        assertThatThrownBy(() -> jwtService.extractSubject(alteredToken))
                .isInstanceOf(SignatureException.class);
    }
}
