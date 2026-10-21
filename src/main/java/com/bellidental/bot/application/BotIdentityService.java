package com.bellidental.bot.application;

import com.bellidental.bot.api.dto.BotPatientResponse;
import com.bellidental.bot.api.dto.VerifyPatientRequest;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotIdentityService {

    private final PatientDirectory patientDirectory;

    public List<BotPatientResponse> getPatientsByPhone(String phone) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(phone);
        List<PatientSummary> patients = patientDirectory.findActiveByPhone(normalizedPhone);
        return patients.stream()
                .map(p -> new BotPatientResponse(p.id(), formatDisplayName(p.firstName(), p.lastName())))
                .toList();
    }

    public BotPatientResponse verifyPatient(VerifyPatientRequest request) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(request.phone());
        List<PatientSummary> patients = patientDirectory.findActiveByPhone(normalizedPhone);
        String requestedIdNumber = request.idNumber().trim();

        return patients.stream()
                .filter(p -> p.documentNumber() != null && p.documentNumber().trim().equalsIgnoreCase(requestedIdNumber))
                .findFirst()
                .map(p -> new BotPatientResponse(p.id(), formatDisplayName(p.firstName(), p.lastName())))
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));
    }

    public PatientSummary requirePatientOfPhone(String phone, UUID patientId) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(phone);
        List<PatientSummary> patients = patientDirectory.findActiveByPhone(normalizedPhone);
        return patients.stream()
                .filter(p -> p.id().equals(patientId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));
    }

    public static String formatDisplayName(String firstName, String lastName) {
        String first = (firstName == null) ? "" : firstName.trim().split("\\s+")[0];
        String last = (lastName == null) ? "" : lastName.trim().split("\\s+")[0];
        String initial = last.isEmpty() ? "" : last.substring(0, 1).toUpperCase() + ".";
        return initial.isEmpty() ? first : (first + " " + initial).trim();
    }
}
