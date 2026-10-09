package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio CRUD de pacientes de la clínica.
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public List<PatientResponse> findAll() {
        return patientRepository.findAllActive().stream()
                .map(PatientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientResponse findById(UUID id) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
        return PatientResponse.from(patient);
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        if (patientRepository.existsByDocumentNumberAndDeletedAtIsNull(request.documentNumber())) {
            throw new BusinessException("Ya existe un paciente con el documento '" + request.documentNumber() + "'.");
        }

        Patient.PatientBuilder builder = Patient.builder()
                .documentType(request.documentType())
                .documentNumber(request.documentNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .sex(request.sex())
                .bloodType(request.bloodType())
                .phoneE164(request.phoneE164())
                .email(request.email())
                .emergencyContactName(request.emergencyContactName())
                .emergencyContactPhoneE164(request.emergencyContactPhoneE164());


        return PatientResponse.from(patientRepository.save(builder.build()));
    }

    @Transactional
    public PatientResponse update(UUID id, UpdatePatientRequest request) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));

        if (request.documentNumber() != null
                && !request.documentNumber().equals(patient.getDocumentNumber())
                && patientRepository.existsByDocumentNumberAndDeletedAtIsNull(request.documentNumber())) {
            throw new BusinessException("El número de documento '" + request.documentNumber() + "' ya está en uso.");
        }

        if (request.documentType() != null)          patient.setDocumentType(request.documentType());
        if (request.documentNumber() != null)         patient.setDocumentNumber(request.documentNumber());
        if (request.firstName() != null)              patient.setFirstName(request.firstName());
        if (request.lastName() != null)               patient.setLastName(request.lastName());
        if (request.birthDate() != null)              patient.setBirthDate(request.birthDate());
        if (request.sex() != null)                    patient.setSex(request.sex());
        if (request.bloodType() != null)              patient.setBloodType(request.bloodType());
        if (request.phoneE164() != null)              patient.setPhoneE164(request.phoneE164());
        if (request.email() != null)                  patient.setEmail(request.email());
        if (request.emergencyContactName() != null)   patient.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhoneE164() != null) patient.setEmergencyContactPhoneE164(request.emergencyContactPhoneE164());
        if (request.active() != null)                 patient.setActive(request.active());

        return PatientResponse.from(patientRepository.save(patient));
    }

    @Transactional
    public void delete(UUID id) {
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", id));
        patient.softDelete();
        patientRepository.save(patient);
    }
}
