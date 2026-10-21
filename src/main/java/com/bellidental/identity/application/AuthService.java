package com.bellidental.identity.application;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.ChangePasswordRequest;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.identity.api.dto.MeResponse;
import com.bellidental.identity.domain.PasswordRules;
import com.bellidental.persistence.entity.User;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.security.CurrentUser;
import com.bellidental.security.JwtService;
import com.bellidental.security.UserAuthorities;
import com.bellidental.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de autenticación.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CurrentUser currentUser;
    private final UserAuthorities userAuthorities;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!user.isActive() || user.isDeleted() || user.isSystem()) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        UUID professionalId = professionalRepository.findByUserId(user.getId())
                .map(prof -> prof.getId())
                .orElse(null);

        String token = jwtService.generateToken(user, professionalId);

        List<String> roles = userAuthorities.getRoles(user);

        return new AuthResponse(token, jwtService.getExpirationTime(), roles);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(currentUser.userId())
                .orElseThrow(() -> new BusinessException("Usuario no encontrado."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("La contraseña actual no es correcta.");
        }
        PasswordRules.validate(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidInputException("La nueva contraseña debe ser distinta de la actual.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        UUID userId = currentUser.userId();
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado."));

        List<String> roles = userAuthorities.getRoles(user);
        List<String> permissions = userAuthorities.getPermissions(user);

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                roles,
                permissions,
                currentUser.professionalId().orElse(null)
        );
    }
}
