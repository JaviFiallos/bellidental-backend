package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.SpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfessionalDirectoryImpl implements ProfessionalDirectory {

    private final ProfessionalRepository professionalRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final SpecialtyRepository specialtyRepository;

    private ProfessionalSummary toSummary(Professional p) {
        return new ProfessionalSummary(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getFirstName(),
                p.getLastName(),
                p.getPhoneE164(),
                p.getGoogleCalendarId(),
                p.getColorTag(),
                p.isActive(),
                p.getLicenseNumber()
        );
    }

    @Override
    public Optional<ProfessionalSummary> findById(UUID id) {
        return professionalRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toSummary);
    }

    @Override
    public List<ProfessionalSummary> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return professionalRepository.findAllById(ids).stream()
                .map(this::toSummary)
                .toList();
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
    
    @Override
    public Optional<SpecialtySummary> findSpecialty(Long specialtyId) {
        return specialtyRepository.findById(specialtyId)
                .map(s -> new SpecialtySummary(s.getId(), s.getName(), s.getDefaultDurationMinutes(), s.isActive()));
    }

    @Override
    public List<SpecialtySummary> findActiveSpecialties() {
        return specialtyRepository.findByActiveTrueOrderByName().stream()
                .map(s -> new SpecialtySummary(s.getId(), s.getName(), s.getDefaultDurationMinutes(), s.isActive()))
                .toList();
    }

    @Override
    public List<ProfessionalSummary> findActiveBySpecialty(Long specialtyId) {
        List<ProfessionalSpecialty> specs = professionalSpecialtyRepository.findBySpecialtyIdAndActiveTrue(specialtyId);
        return specs.stream()
                .map(ProfessionalSpecialty::getProfessional)
                .filter(p -> p.isActive() && p.getDeletedAt() == null)
                .map(this::toSummary)
                .toList();
    }
}
