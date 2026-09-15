package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.persistence.repository.UserRepository;
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
    private final UserRepository userRepository;

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
                .gender(request.gender())
                .bloodType(request.bloodType())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .emergencyContactName(request.emergencyContactName())
                .emergencyContactPhone(request.emergencyContactPhone());

        if (request.userId() != null) {
            User user = userRepository.findByIdAndDeletedAtIsNull(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", request.userId()));
            builder.user(user);
        }

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
        if (request.gender() != null)                 patient.setGender(request.gender());
        if (request.bloodType() != null)              patient.setBloodType(request.bloodType());
        if (request.phone() != null)                  patient.setPhone(request.phone());
        if (request.email() != null)                  patient.setEmail(request.email());
        if (request.address() != null)                patient.setAddress(request.address());
        if (request.emergencyContactName() != null)   patient.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhone() != null)  patient.setEmergencyContactPhone(request.emergencyContactPhone());
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
