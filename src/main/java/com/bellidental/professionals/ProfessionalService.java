package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.ProfessionalSpecialtyId;
import com.bellidental.persistence.entity.Specialty;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.SpecialtyRepository;
import com.bellidental.shared.validation.DocumentValidator;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio CRUD de profesionales clínicos y administrativos.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    @Transactional(readOnly = true)
    public List<ProfessionalResponse> findAll() {
        return professionalRepository.findAllActive().stream()
                .map(p -> ProfessionalResponse.from(p, getSpecialties(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessionalResponse findById(UUID id) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));
        return ProfessionalResponse.from(professional, getSpecialties(id));
    }

    private List<Specialty> getSpecialties(UUID professionalId) {
        return professionalSpecialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(ProfessionalSpecialty::isActive)
                .map(ProfessionalSpecialty::getSpecialty)
                .filter(Specialty::isActive)
                .toList();
    }

    @Transactional
    public ProfessionalResponse create(CreateProfessionalRequest request) {
        if (professionalRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), request.documentNumber())) {
            throw new BusinessException("Ya existe un profesional con el documento '" + request.documentNumber() + "'.");
        }
        if (professionalRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new BusinessException("El número de licencia '" + request.licenseNumber() + "' ya está registrado.");
        }
        
        DocumentValidator.validate(request.documentType(), request.documentNumber());

        Professional.ProfessionalBuilder builder = Professional.builder()
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .licenseNumber(request.licenseNumber())
                .phoneE164(PhoneNumberNormalizer.normalize(request.phoneE164()))
                .googleCalendarId(request.googleCalendarId())
                .colorTag(request.colorTag());

        if (request.userId() != null) {
            User user = userRepository.findByIdAndDeletedAtIsNull(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", request.userId()));
            
            if (professionalRepository.findByUserId(request.userId()).isPresent()) {
                 throw new BusinessException("El usuario ya tiene un perfil de profesional asociado.");
            }
            builder.user(user);
        }

        Professional professional = professionalRepository.save(builder.build());
        
        syncSpecialties(professional, request.specialtyIds());

        return ProfessionalResponse.from(professional, getSpecialties(professional.getId()));
    }

    @Transactional
    public ProfessionalResponse update(UUID id, UpdateProfessionalRequest request) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));

        if (request.documentNumber() != null
                && !request.documentNumber().equals(professional.getDocumentNumber())
                && professionalRepository.existsByDocumentTypeAndDocumentNumber(request.documentType() != null ? request.documentType() : professional.getDocumentType(), request.documentNumber())) {
            throw new BusinessException("El número de documento '" + request.documentNumber() + "' ya está en uso.");
        }
        if (request.licenseNumber() != null
                && !request.licenseNumber().equals(professional.getLicenseNumber())
                && professionalRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new BusinessException("El número de licencia '" + request.licenseNumber() + "' ya está en uso.");
        }

        if (request.documentType() != null)  professional.setDocumentType(request.documentType());
        if (request.documentNumber() != null) professional.setDocumentNumber(request.documentNumber());
        
        DocumentValidator.validate(professional.getDocumentType(), professional.getDocumentNumber());

        if (request.firstName() != null)     professional.setFirstName(request.firstName());
        if (request.lastName() != null)      professional.setLastName(request.lastName());
        if (request.licenseNumber() != null) professional.setLicenseNumber(request.licenseNumber());
        if (request.phoneE164() != null)     professional.setPhoneE164(PhoneNumberNormalizer.normalize(request.phoneE164()));
        if (request.googleCalendarId() != null) professional.setGoogleCalendarId(request.googleCalendarId());
        if (request.colorTag() != null)      professional.setColorTag(request.colorTag());

        Professional saved = professionalRepository.save(professional);

        if (request.specialtyIds() != null) {
            syncSpecialties(saved, request.specialtyIds());
        }

        return ProfessionalResponse.from(saved, getSpecialties(saved.getId()));
    }

    private void syncSpecialties(Professional professional, List<Long> newSpecialtyIds) {
        if (newSpecialtyIds == null) return;
        
        List<ProfessionalSpecialty> currentSpecs = professionalSpecialtyRepository.findByProfessionalId(professional.getId());
        
        // Desactivar las que ya no están
        for (ProfessionalSpecialty ps : currentSpecs) {
             if (!newSpecialtyIds.contains(ps.getSpecialty().getId())) {
                 ps.setActive(false);
                 professionalSpecialtyRepository.save(ps);
             }
        }
        
        // Agregar o reactivar
        for (Long specId : newSpecialtyIds) {
             Optional<ProfessionalSpecialty> existing = currentSpecs.stream()
                  .filter(ps -> ps.getSpecialty().getId().equals(specId))
                  .findFirst();
                  
             if (existing.isPresent()) {
                  ProfessionalSpecialty ps = existing.get();
                  if (!ps.isActive()) {
                      ps.setActive(true);
                      professionalSpecialtyRepository.save(ps);
                  }
             } else {
                  Specialty specialty = specialtyRepository.findById(specId)
                       .orElseThrow(() -> new BusinessException("La especialidad con ID " + specId + " no existe."));
                  
                  ProfessionalSpecialty newPs = ProfessionalSpecialty.builder()
                       .id(new ProfessionalSpecialtyId(professional.getId(), specialty.getId()))
                       .professional(professional)
                       .specialty(specialty)
                       .active(true)
                       .build();
                  professionalSpecialtyRepository.save(newPs);
             }
        }
    }

    @Transactional
    public void changeActiveStatus(UUID id, boolean active) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));
        professional.setActive(active);
        professionalRepository.save(professional);
    }
}
