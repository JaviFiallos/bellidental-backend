package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.shared.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PatientDirectoryImpl implements PatientDirectory {

    private final PatientRepository patientRepository;

    private PatientSummary toSummary(Patient p) {
        return new PatientSummary(
                p.getId(),
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getBirthDate(),
                p.getSex(),
                p.getPhoneE164(),
                p.isActive()
        );
    }

    @Override
    public Optional<PatientSummary> findById(UUID id) {
        return patientRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toSummary);
    }

    @Override
    public List<PatientSummary> findActiveByPhone(String phoneE164) {
        return patientRepository.findByPhoneE164AndActiveTrueAndDeletedAtIsNull(phoneE164).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<PatientSummary> findByDocument(DocumentType type, String documentNumber) {
        return patientRepository.findByDocumentTypeAndDocumentNumberAndDeletedAtIsNull(type, documentNumber)
                .map(this::toSummary);
    }
}
