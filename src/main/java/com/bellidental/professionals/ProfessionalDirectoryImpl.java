package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProfessionalDirectoryImpl implements ProfessionalDirectory {

    private final ProfessionalRepository professionalRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    private ProfessionalSummary toSummary(Professional p) {
        return new ProfessionalSummary(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getFirstName(),
                p.getLastName(),
                p.getPhoneE164(),
                p.getGoogleCalendarId(),
                p.getColorTag(),
                p.isActive()
        );
    }

    @Override
    public Optional<ProfessionalSummary> findById(UUID id) {
        return professionalRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toSummary);
    }

    @Override
    public List<ProfessionalSummary> findActive() {
        return professionalRepository.findAllActive().stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public boolean hasActiveSpecialty(UUID professionalId, Long specialtyId) {
        List<ProfessionalSpecialty> specs = professionalSpecialtyRepository.findByProfessionalId(professionalId);
        return specs.stream()
                .anyMatch(ps -> ps.isActive() && ps.getSpecialty().getId().equals(specialtyId) && ps.getSpecialty().isActive());
    }

    @Override
    public Optional<ProfessionalSummary> findByUserId(UUID userId) {
        return professionalRepository.findByUserId(userId)
                .map(this::toSummary);
    }
}
