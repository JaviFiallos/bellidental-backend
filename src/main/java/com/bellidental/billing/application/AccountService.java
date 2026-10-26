package com.bellidental.billing.application;

import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.AccountSessionResponse;
import com.bellidental.billing.domain.AccountBalance;
import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Payment;
import com.bellidental.persistence.repository.PaymentRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.treatments.PerformedTreatmentSummary;
import com.bellidental.treatments.TreatmentDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal NO_CHARGE = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private final PatientDirectory patientDirectory;
    private final ClinicalDirectory clinicalDirectory;
    private final TreatmentDirectory treatmentDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public AccountResponse findAccount(UUID patientId) {
        PatientSummary patient = patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
        List<EncounterSummary> encounters = clinicalDirectory.findPatientEncounters(patientId);
        List<Payment> payments = paymentRepository.findByPatientIdAndVoidedAtIsNullOrderByPaidAtAsc(patientId);

        Set<UUID> encounterIds = encounters.stream().map(EncounterSummary::id).collect(Collectors.toSet());
        Map<UUID, List<PerformedTreatmentSummary>> treatmentsByEncounter = treatmentDirectory.findPerformed(encounterIds).stream()
                .collect(Collectors.groupingBy(PerformedTreatmentSummary::encounterId));
        Map<UUID, List<Payment>> paymentsByEncounter = payments.stream()
                .filter(payment -> encounterIds.contains(payment.getEncounterId()))
                .collect(Collectors.groupingBy(Payment::getEncounterId));
        List<Payment> unlinkedPayments = payments.stream()
                .filter(payment -> !encounterIds.contains(payment.getEncounterId()))
                .toList();
        Map<UUID, ProfessionalSummary> professionalsById = findProfessionals(encounters);

        List<SessionDraft> sessions = new ArrayList<>(encounters.stream()
                .map(encounter -> toSession(encounter, treatmentsByEncounter, paymentsByEncounter, professionalsById))
                .toList());
        if (!unlinkedPayments.isEmpty()) {
            sessions.add(unlinkedPaymentsSession(unlinkedPayments));
        }
        sessions.sort(Comparator.comparing(SessionDraft::date));

        return toAccountResponse(patient, sessions, payments);
    }

    private Map<UUID, ProfessionalSummary> findProfessionals(List<EncounterSummary> encounters) {
        List<UUID> professionalIds = encounters.stream().map(EncounterSummary::professionalId).distinct().toList();
        return professionalDirectory.findByIds(professionalIds).stream()
                .collect(Collectors.toMap(ProfessionalSummary::id, professional -> professional));
    }

    private SessionDraft toSession(EncounterSummary encounter,
                                   Map<UUID, List<PerformedTreatmentSummary>> treatmentsByEncounter,
                                   Map<UUID, List<Payment>> paymentsByEncounter,
                                   Map<UUID, ProfessionalSummary> professionalsById) {
        List<PerformedTreatmentSummary> treatments = treatmentsByEncounter.getOrDefault(encounter.id(), List.of());
        List<Payment> sessionPayments = paymentsByEncounter.getOrDefault(encounter.id(), List.of());
        ProfessionalSummary professional = professionalsById.get(encounter.professionalId());

        return new SessionDraft(
                encounter.id(),
                encounter.startAt(),
                professional != null ? fullName(professional.firstName(), professional.lastName()) : null,
                treatments.stream().map(treatment -> treatment.treatmentName() + " x" + treatment.quantity()).toList(),
                sum(treatments.stream().map(treatment -> treatment.price().multiply(BigDecimal.valueOf(treatment.quantity())))),
                sum(sessionPayments.stream().map(Payment::getAmount))
        );
    }

    /** Pagos sin atención o ligados a una atención anulada: una sesión final sin cargo. */
    private SessionDraft unlinkedPaymentsSession(List<Payment> unlinkedPayments) {
        OffsetDateTime lastPaidAt = unlinkedPayments.stream()
                .map(Payment::getPaidAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new SessionDraft(
                null,
                lastPaidAt,
                null,
                List.of(),
                NO_CHARGE,
                sum(unlinkedPayments.stream().map(Payment::getAmount))
        );
    }

    private AccountResponse toAccountResponse(PatientSummary patient, List<SessionDraft> sessions, List<Payment> payments) {
        BigDecimal totalCharges = sum(sessions.stream().map(SessionDraft::charge));
        BigDecimal totalPayments = sum(payments.stream().map(Payment::getAmount));
        List<BigDecimal> runningBalances = AccountBalance.runningBalances(
                sessions.stream().map(SessionDraft::charge).toList(),
                sessions.stream().map(SessionDraft::payment).toList()
        );

        List<AccountSessionResponse> sessionResponses = IntStream.range(0, sessions.size())
                .mapToObj(index -> toSessionResponse(sessions.get(index), runningBalances.get(index)))
                .toList();

        return new AccountResponse(
                patient.id(),
                fullName(patient.firstName(), patient.lastName()),
                totalCharges,
                totalPayments,
                AccountBalance.balance(totalCharges, totalPayments),
                sessionResponses
        );
    }

    private AccountSessionResponse toSessionResponse(SessionDraft session, BigDecimal runningBalance) {
        return new AccountSessionResponse(
                session.encounterId(),
                session.date(),
                session.professionalName(),
                session.treatments(),
                session.charge(),
                session.payment(),
                runningBalance
        );
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String fullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    private record SessionDraft(
            UUID encounterId,
            OffsetDateTime date,
            String professionalName,
            List<String> treatments,
            BigDecimal charge,
            BigDecimal payment
    ) {}
}
