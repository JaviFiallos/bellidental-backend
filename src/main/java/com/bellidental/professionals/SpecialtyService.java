package com.bellidental.professionals;

import com.bellidental.config.BotProperties;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.Specialty;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.SpecialtyRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Alta, edición y desactivación de especialidades por el ADMIN.
 */
@Service
@RequiredArgsConstructor
public class SpecialtyService {

    private static final int MIN_DURATION_MINUTES = 15;
    private static final int MAX_DURATION_MINUTES = 240;
    private static final int DURATION_STEP_MINUTES = 15;

    private final SpecialtyRepository specialtyRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final BotProperties botProperties;

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> findAllActive() {
        return specialtyRepository.findByActiveTrueOrderByName().stream().map(SpecialtyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> findAll() {
        return specialtyRepository.findAllByOrderByName().stream().map(SpecialtyResponse::from).toList();
    }

    @Transactional
    public SpecialtyResponse create(CreateSpecialtyRequest request) {
        requireValidDuration(request.defaultDurationMinutes());
        String name = request.name().trim();
        requireAvailableName(name, null);

        Specialty specialty = Specialty.builder()
                .name(name)
                .defaultDurationMinutes(request.defaultDurationMinutes())
                .active(true)
                .build();
        return SpecialtyResponse.from(specialtyRepository.save(specialty));
    }

    /** Solo afecta a la disponibilidad futura. */
    @Transactional
    public SpecialtyResponse update(Long specialtyId, UpdateSpecialtyRequest request) {
        Specialty specialty = findSpecialty(specialtyId);
        requireValidDuration(request.defaultDurationMinutes());
        String name = request.name().trim();
        requireAvailableName(name, specialtyId);

        specialty.setName(name);
        specialty.setDefaultDurationMinutes(request.defaultDurationMinutes());
        return SpecialtyResponse.from(specialtyRepository.save(specialty));
    }

    @Transactional
    public SpecialtyResponse deactivate(Long specialtyId) {
        Specialty specialty = findSpecialty(specialtyId);
        requireDeactivatable(specialtyId);
        specialty.setActive(false);
        return SpecialtyResponse.from(specialtyRepository.save(specialty));
    }

    @Transactional
    public SpecialtyResponse activate(Long specialtyId) {
        Specialty specialty = findSpecialty(specialtyId);
        specialty.setActive(true);
        return SpecialtyResponse.from(specialtyRepository.save(specialty));
    }

    private Specialty findSpecialty(Long specialtyId) {
        return specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad", specialtyId));
    }

    private void requireValidDuration(int minutes) {
        boolean inRange = minutes >= MIN_DURATION_MINUTES && minutes <= MAX_DURATION_MINUTES;
        if (!inRange || minutes % DURATION_STEP_MINUTES != 0) {
            throw new InvalidInputException("La duración debe ser múltiplo de 15 minutos, entre 15 y 240.");
        }
    }

    private void requireAvailableName(String name, Long specialtyId) {
        boolean taken = specialtyId == null
                ? specialtyRepository.existsByNameIgnoreCase(name)
                : specialtyRepository.existsByNameIgnoreCaseAndIdNot(name, specialtyId);
        if (taken) {
            throw new BusinessException("La especialidad '" + name + "' ya existe.");
        }
    }

    private void requireDeactivatable(Long specialtyId) {
        if (specialtyId.equals(botProperties.urgentSpecialtyId())) {
            throw new BusinessException("Es la especialidad de urgencias del bot; cámbiala en la configuración antes.");
        }
        if (hasActiveProfessionals(specialtyId)) {
            throw new BusinessException("Hay profesionales con esta especialidad; quítasela antes.");
        }
        if (specialtyRepository.countFutureActiveAppointments(specialtyId) > 0) {
            throw new BusinessException("Hay citas futuras con esta especialidad.");
        }
    }

    private boolean hasActiveProfessionals(Long specialtyId) {
        return professionalSpecialtyRepository.findBySpecialtyIdAndActiveTrue(specialtyId).stream()
                .map(ProfessionalSpecialty::getProfessional)
                .anyMatch(professional -> professional.isActive() && !professional.isDeleted());
    }
}
