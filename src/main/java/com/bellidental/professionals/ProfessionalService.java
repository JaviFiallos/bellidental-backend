package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio CRUD de profesionales clínicos y administrativos.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProfessionalResponse> findAll() {
        return professionalRepository.findAllActive().stream()
                .map(ProfessionalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessionalResponse findById(UUID id) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));
        return ProfessionalResponse.from(professional);
    }

    @Transactional
    public ProfessionalResponse create(CreateProfessionalRequest request) {
        if (professionalRepository.existsByDocumentNumberAndDeletedAtIsNull(request.documentNumber())) {
            throw new BusinessException("Ya existe un profesional con el documento '" + request.documentNumber() + "'.");
        }
        if (professionalRepository.existsByLicenseNumberAndDeletedAtIsNull(request.licenseNumber())) {
            throw new BusinessException("El número de licencia '" + request.licenseNumber() + "' ya está registrado.");
        }

        Professional.ProfessionalBuilder builder = Professional.builder()
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .licenseNumber(request.licenseNumber())
                .phoneE164(request.phoneE164())
                .colorTag(request.colorTag());

        if (request.userId() != null) {
            User user = userRepository.findByIdAndDeletedAtIsNull(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", request.userId()));
            builder.user(user);
        }

        return ProfessionalResponse.from(professionalRepository.save(builder.build()));
    }

    @Transactional
    public ProfessionalResponse update(UUID id, UpdateProfessionalRequest request) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));

        if (request.documentNumber() != null
                && !request.documentNumber().equals(professional.getDocumentNumber())
                && professionalRepository.existsByDocumentNumberAndDeletedAtIsNull(request.documentNumber())) {
            throw new BusinessException("El número de documento '" + request.documentNumber() + "' ya está en uso.");
        }
        if (request.licenseNumber() != null
                && !request.licenseNumber().equals(professional.getLicenseNumber())
                && professionalRepository.existsByLicenseNumberAndDeletedAtIsNull(request.licenseNumber())) {
            throw new BusinessException("El número de licencia '" + request.licenseNumber() + "' ya está en uso.");
        }

        if (request.documentType() != null)  professional.setDocumentType(request.documentType());
        if (request.documentNumber() != null) professional.setDocumentNumber(request.documentNumber());
        if (request.firstName() != null)     professional.setFirstName(request.firstName());
        if (request.lastName() != null)      professional.setLastName(request.lastName());
        if (request.licenseNumber() != null) professional.setLicenseNumber(request.licenseNumber());
        if (request.phoneE164() != null)     professional.setPhoneE164(request.phoneE164());
        if (request.colorTag() != null)      professional.setColorTag(request.colorTag());
        if (request.active() != null)        professional.setActive(request.active());

        return ProfessionalResponse.from(professionalRepository.save(professional));
    }

    @Transactional
    public void delete(UUID id) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional", id));
        professional.softDelete();
        professionalRepository.save(professional);
    }
}
