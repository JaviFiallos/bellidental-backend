package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.validation.DocumentValidator;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Servicio CRUD de pacientes de la clínica.
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Value("${application.clinic.zone}")
    private String clinicZone;

    private ZoneId getZone() {
        return ZoneId.of(clinicZone);
    }

    @Transactional(readOnly = true)
    public Page<PatientResponse> search(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Patient> patientsPage;
        if (query.matches("\\d+")) {
            String prefix = toPhonePrefix(query) + "%";
            patientsPage = patientRepository.findActiveByDocumentOrPhonePrefix(query, prefix, pageable);
        } else {
            if (query.trim().length() < 2) {
                throw new InvalidInputException("La búsqueda por texto requiere al menos 2 caracteres.");
            }
            patientsPage = patientRepository.findActiveByNameOrLastName(query.trim(), pageable);
        }

        return patientsPage.map(p -> PatientResponse.from(p, getZone()));
    }
    
    private String toPhonePrefix(String digits) {
        if (digits.startsWith("0")) {
            return "593" + digits.substring(1);
        }
        return digits;
    }

    @Transactional(readOnly = true)
    public PatientResponse findById(UUID id) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
        return PatientResponse.from(patient, getZone());
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        if (patientRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), request.documentNumber())) {
            throw new BusinessException("Ya existe un paciente con el documento '" + request.documentNumber() + "'.");
        }

        DocumentValidator.validate(request.documentType(), request.documentNumber());

        String phoneE164 = PhoneNumberNormalizer.normalize(request.phoneE164());
        String emergencyPhoneE164 = PhoneNumberNormalizer.normalize(request.emergencyContactPhoneE164());

        Patient.PatientBuilder builder = Patient.builder()
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .sex(request.sex())
                .bloodType(request.bloodType())
                .phoneE164(phoneE164)
                .email(request.email())
                .emergencyContactName(request.emergencyContactName())
                .emergencyContactPhoneE164(emergencyPhoneE164);

        return PatientResponse.from(patientRepository.save(builder.build()), getZone());
    }

    @Transactional
    public PatientResponse update(UUID id, UpdatePatientRequest request) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));

        if (request.documentNumber() != null
                && !request.documentNumber().equals(patient.getDocumentNumber())
                && patientRepository.existsByDocumentTypeAndDocumentNumber(request.documentType() != null ? request.documentType() : patient.getDocumentType(), request.documentNumber())) {
            throw new BusinessException("El número de documento '" + request.documentNumber() + "' ya está en uso.");
        }

        if (request.documentType() != null) patient.setDocumentType(request.documentType());
        if (request.documentNumber() != null) patient.setDocumentNumber(request.documentNumber());

        DocumentValidator.validate(patient.getDocumentType(), patient.getDocumentNumber());

        if (request.firstName() != null)              patient.setFirstName(request.firstName());
        if (request.lastName() != null)               patient.setLastName(request.lastName());
        if (request.birthDate() != null)              patient.setBirthDate(request.birthDate());
        if (request.sex() != null)                    patient.setSex(request.sex());
        if (request.bloodType() != null)              patient.setBloodType(request.bloodType());
        if (request.phoneE164() != null)              patient.setPhoneE164(PhoneNumberNormalizer.normalize(request.phoneE164()));
        if (request.email() != null)                  patient.setEmail(request.email());
        if (request.emergencyContactName() != null)   patient.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhoneE164() != null) patient.setEmergencyContactPhoneE164(PhoneNumberNormalizer.normalize(request.emergencyContactPhoneE164()));
        if (request.adminNotes() != null)             patient.setAdminNotes(request.adminNotes());

        return PatientResponse.from(patientRepository.save(patient), getZone());
    }

    @Transactional
    public void changeActiveStatus(UUID id, boolean active) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
        patient.setActive(active);
        patientRepository.save(patient);
    }
}
