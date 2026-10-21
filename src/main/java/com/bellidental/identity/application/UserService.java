package com.bellidental.identity.application;

import com.bellidental.identity.api.dto.CreateUserRequest;
import com.bellidental.identity.api.dto.ResetPasswordRequest;
import com.bellidental.identity.api.dto.UpdateUserRequest;
import com.bellidental.identity.api.dto.UpdateUserRolesRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.identity.domain.PasswordRules;
import com.bellidental.persistence.entity.Role;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.entity.UserRole;
import com.bellidental.persistence.entity.UserRoleId;
import com.bellidental.persistence.repository.RoleRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestión de cuentas por el ADMIN (D-24). El ADMIN no se crea ni se modifica por API.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ODONTOLOGO", "SECRETARIA");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllActive().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.from(findActiveUserOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return UserResponse.from(createAccount(request.email(), request.username(),
                request.temporaryPassword(), request.roles()));
    }

    /** Crea una cuenta con roles asignables. Lo usa también el alta de acceso de un profesional. */
    @Transactional
    public User createAccount(String rawEmail, String rawUsername, String temporaryPassword, Set<String> roleCodes) {
        String email = normalizeEmail(rawEmail);
        String username = rawUsername == null || rawUsername.isBlank()
                ? PasswordRules.usernameFromEmail(email)
                : rawUsername.trim();
        if (username.isEmpty()) {
            throw new InvalidInputException("No se pudo derivar un nombre de usuario del email; indíquelo.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("El email '" + email + "' ya está en uso.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("El nombre de usuario '" + username + "' ya está en uso.");
        }
        PasswordRules.validate(temporaryPassword);
        List<Role> roles = resolveAssignableRoles(roleCodes);

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .build();
        User saved = userRepository.saveAndFlush(user);
        addRoles(saved, roles);
        return saved;
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findActiveUserOrThrow(id);
        requireNotSystem(user);
        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException("El email '" + email + "' ya está en uso.");
        }
        user.setEmail(email);
        return UserResponse.from(userRepository.save(user));
    }

    /** Reemplaza los roles por los pedidos: solo quita los que sobran y añade los que faltan. */
    @Transactional
    public UserResponse updateRoles(UUID id, UpdateUserRolesRequest request) {
        User user = findActiveUserOrThrow(id);
        requireNotSystem(user);
        if (currentRoleCodes(user).contains("ADMIN")) {
            throw new BusinessException("No se modifican los roles de un administrador.");
        }
        List<Role> requestedRoles = resolveAssignableRoles(request.roles());
        Set<String> requestedCodes = requestedRoles.stream().map(Role::getCode).collect(Collectors.toSet());

        user.getUserRoles().removeIf(userRole -> !requestedCodes.contains(userRole.getRole().getCode()));
        Set<String> remainingCodes = currentRoleCodes(user);
        addRoles(user, requestedRoles.stream()
                .filter(role -> !remainingCodes.contains(role.getCode()))
                .toList());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User user = findActiveUserOrThrow(id);
        requireNotSystem(user);
        PasswordRules.validate(request.temporaryPassword());
        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse deactivate(UUID id, UUID currentUserId) {
        if (id.equals(currentUserId)) {
            throw new BusinessException("No puedes desactivar tu propia cuenta.");
        }
        return setActive(id, false);
    }

    @Transactional
    public UserResponse activate(UUID id) {
        return setActive(id, true);
    }

    private UserResponse setActive(UUID id, boolean active) {
        User user = findActiveUserOrThrow(id);
        requireNotSystem(user);
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    private User findActiveUserOrThrow(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    private void requireNotSystem(User user) {
        if (user.isSystem()) {
            throw new BusinessException("Los usuarios del sistema no se pueden modificar.");
        }
    }

    private List<Role> resolveAssignableRoles(Collection<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> {
                    Role role = roleRepository.findByCode(code)
                            .orElseThrow(() -> new InvalidInputException("Rol inexistente: " + code + "."));
                    if (!ASSIGNABLE_ROLES.contains(code)) {
                        throw new InvalidInputException("Rol no permitido: " + code + ".");
                    }
                    return role;
                })
                .toList();
    }

    private void addRoles(User user, Collection<Role> roles) {
        for (Role role : roles) {
            user.getUserRoles().add(UserRole.builder()
                    .id(new UserRoleId(user.getId(), role.getId()))
                    .user(user)
                    .role(role)
                    .build());
        }
    }

    private Set<String> currentRoleCodes(User user) {
        return user.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
