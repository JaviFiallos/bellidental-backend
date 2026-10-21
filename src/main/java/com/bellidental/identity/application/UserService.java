package com.bellidental.identity.application;

import com.bellidental.identity.api.dto.UpdateUserRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio CRUD de usuarios del sistema.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllActive().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if (request.email() != null && !request.email().isBlank()) {
            boolean emailTaken = userRepository.existsByEmailAndDeletedAtIsNull(request.email())
                    && !request.email().equalsIgnoreCase(user.getEmail());
            if (emailTaken) {
                throw new BusinessException("El email '" + request.email() + "' ya está en uso.");
            }
            user.setEmail(request.email());
        }

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.active() != null) {
            user.setActive(request.active());
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        user.softDelete();
        userRepository.save(user);
    }
}
