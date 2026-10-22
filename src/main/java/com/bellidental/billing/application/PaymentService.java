package com.bellidental.billing.application;

import com.bellidental.billing.api.dto.CreatePaymentRequest;
import com.bellidental.billing.api.dto.PaymentListItemResponse;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.billing.api.dto.VoidPaymentRequest;
import com.bellidental.billing.domain.ReceiptNumber;
import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.persistence.entity.Payment;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.PaymentRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PatientDirectory patientDirectory;
    private final ClinicalDirectory clinicalDirectory;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ClinicClock clinicClock;

    @Transactional
    public PaymentResponse register(UUID patientId, CreatePaymentRequest request) {
        patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
        if (request.encounterId() != null) {
            requireEncounterOfPatient(request.encounterId(), patientId);
        }

        Payment payment = new Payment();
        payment.setPatientId(patientId);
        payment.setEncounterId(request.encounterId());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setReceiptNumber(ReceiptNumber.format(paymentRepository.nextReceiptSequence()));
        payment.setPaidAt(clinicClock.now());
        payment.setReceivedBy(currentUser.userId());
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse voidPayment(UUID paymentId, VoidPaymentRequest request) {
        Payment payment = findPaymentOrThrow(paymentId);
        if (payment.getVoidedAt() != null) {
            throw new BusinessException("El pago ya está anulado.");
        }
        payment.setVoidedAt(clinicClock.now());
        payment.setVoidReason(request.voidReason());
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentListItemResponse> listByPatient(UUID patientId) {
        patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
        List<Payment> payments = paymentRepository.findByPatientIdOrderByPaidAtDesc(patientId);
        Set<UUID> userIds = payments.stream()
                .map(Payment::getReceivedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> usernames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return payments.stream()
                .map(p -> new PaymentListItemResponse(
                        p.getId(),
                        p.getEncounterId(),
                        p.getAmount() != null ? p.getAmount().setScale(2, RoundingMode.HALF_UP) : null,
                        p.getMethod(),
                        p.getReceiptNumber(),
                        p.getPaidAt(),
                        p.getReceivedBy(),
                        usernames.get(p.getReceivedBy()),
                        p.getVoidedAt() != null,
                        p.getVoidedAt(),
                        p.getVoidReason()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID paymentId) {
        return toResponse(findPaymentOrThrow(paymentId));
    }

    private void requireEncounterOfPatient(UUID encounterId, UUID patientId) {
        EncounterSummary encounter = clinicalDirectory.findEncounter(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));
        if (!encounter.patientId().equals(patientId) || encounter.voided()) {
            throw new BusinessException("La atención no pertenece a este paciente.");
        }
    }

    private Payment findPaymentOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPatientId(),
                payment.getEncounterId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getReceiptNumber(),
                payment.getPaidAt(),
                payment.getReceivedBy(),
                payment.getVoidedAt() != null,
                payment.getVoidReason()
        );
    }
}
