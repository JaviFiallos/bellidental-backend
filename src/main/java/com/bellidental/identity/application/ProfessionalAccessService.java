package com.bellidental.identity.application;

import com.bellidental.identity.api.dto.GrantAccessRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfessionalAccessService {

    private final ProfessionalRepository professionalRepository;
    private final UserService userService;

    @Transactional
    public UserResponse grantAccess(UUID professionalId, GrantAccessRequest request) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", professionalId));
        if (professional.getUser() != null) {
            throw new BusinessException("El profesional ya tiene una cuenta de acceso.");
        }

        User user = userService.createAccount(request.email(), null, request.temporaryPassword(), Set.of("ODONTOLOGO"));
        professional.setUser(user);
        professionalRepository.save(professional);
        return UserResponse.from(user);
    }
}
