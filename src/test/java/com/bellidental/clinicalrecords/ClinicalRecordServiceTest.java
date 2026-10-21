package com.bellidental.clinicalrecords;

import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.ClinicalRecord;
import com.bellidental.persistence.entity.MedicalHistoryCondition;
import com.bellidental.persistence.entity.MedicalHistoryEntry;
import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.shared.enums.HistoryScope;
import com.bellidental.persistence.repository.ClinicalRecordRepository;
import com.bellidental.persistence.repository.MedicalHistoryConditionRepository;
import com.bellidental.persistence.repository.MedicalHistoryEntryRepository;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ClinicalRecordServiceTest {

    @Mock
    private ClinicalRecordRepository clinicalRecordRepository;
    @Mock
    private MedicalHistoryConditionRepository conditionRepository;
    @Mock
    private MedicalHistoryEntryRepository entryRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private PatientDirectory patientDirectory;

    @InjectMocks
    private ClinicalRecordService service;

    private final UUID patientId = UUID.randomUUID();
    private final UUID professionalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "clinicZone", "America/Guayaquil");
    }

    @Test
    void open_patientNotFound_throwsNotFound() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(patientId, professionalId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Paciente");
    }

    @Test
    void open_noProfessional_throwsBusinessException() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));

        assertThatThrownBy(() -> service.open(patientId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene un perfil de profesional asociado");
    }

    @Test
    void open_alreadyHasRecord_throwsBusinessException() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(clinicalRecordRepository.findByPatientId(patientId)).thenReturn(Optional.of(ClinicalRecord.builder().build()));

        assertThatThrownBy(() -> service.open(patientId, professionalId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya tiene una historia clínica abierta");
    }

    @Test
    void open_success_returnsFormattedNumberAndDate() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(clinicalRecordRepository.findByPatientId(patientId)).thenReturn(Optional.empty());
        when(clinicalRecordRepository.getNextRecordNumber()).thenReturn(42L);
        when(patientRepository.getReferenceById(patientId)).thenReturn(Patient.builder().id(patientId).build());
        when(professionalRepository.getReferenceById(professionalId)).thenReturn(Professional.builder().id(professionalId).build());
        when(clinicalRecordRepository.save(any(ClinicalRecord.class))).thenAnswer(i -> i.getArgument(0));

        ClinicalRecordResponse response = service.open(patientId, professionalId);

        assertThat(response.recordNumber()).isEqualTo("HC-000042");
        assertThat(response.openedAt()).isEqualTo(LocalDate.now(ZoneId.of("America/Guayaquil")));
    }

    @Test
    void openIfAbsent_withExistingRecord_doesNotCreateNew() {
        ClinicalRecord existing = ClinicalRecord.builder()
                .id(UUID.randomUUID())
                .patient(Patient.builder().id(patientId).build())
                .recordNumber("HC-000010")
                .build();
        when(clinicalRecordRepository.findByPatientId(patientId)).thenReturn(Optional.of(existing));

        ClinicalRecordSummary summary = service.openIfAbsent(patientId, professionalId);

        assertThat(summary.recordNumber()).isEqualTo("HC-000010");
        verify(clinicalRecordRepository, never()).save(any());
    }

    @Test
    void addEntry_familyWithoutRelative_throwsInvalidInput() {
        UUID recordId = UUID.randomUUID();
        when(clinicalRecordRepository.findById(recordId)).thenReturn(Optional.of(ClinicalRecord.builder().build()));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(MedicalHistoryCondition.builder().id(1L).active(true).build()));

        CreateMedicalHistoryEntryRequest req = new CreateMedicalHistoryEntryRequest(1L, HistoryScope.FAMILY, null, null);

        assertThatThrownBy(() -> service.addEntry(recordId, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("familiar (relative)");
    }

    @Test
    void addEntry_personalWithRelative_throwsInvalidInput() {
        UUID recordId = UUID.randomUUID();
        when(clinicalRecordRepository.findById(recordId)).thenReturn(Optional.of(ClinicalRecord.builder().build()));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(MedicalHistoryCondition.builder().id(1L).active(true).build()));

        CreateMedicalHistoryEntryRequest req = new CreateMedicalHistoryEntryRequest(1L, HistoryScope.PERSONAL, "Padre", null);

        assertThatThrownBy(() -> service.addEntry(recordId, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("no debe incluir un familiar");
    }

    @Test
    void addEntry_otherConditionWithoutDescription_throwsInvalidInput() {
        UUID recordId = UUID.randomUUID();
        when(clinicalRecordRepository.findById(recordId)).thenReturn(Optional.of(ClinicalRecord.builder().build()));
        when(conditionRepository.findById(10L)).thenReturn(Optional.of(MedicalHistoryCondition.builder().id(10L).active(true).build()));

        CreateMedicalHistoryEntryRequest req = new CreateMedicalHistoryEntryRequest(10L, HistoryScope.PERSONAL, null, null);

        assertThatThrownBy(() -> service.addEntry(recordId, req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("condición 'Otro' requiere una descripción");
    }

    @Test
    void addEntry_valid_setsNoKnownHistoryToFalse() {
        UUID recordId = UUID.randomUUID();
        ClinicalRecord record = ClinicalRecord.builder().id(recordId).noKnownHistory(true).build();
        when(clinicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(MedicalHistoryCondition.builder().id(1L).name("Cond1").active(true).build()));
        when(entryRepository.save(any())).thenAnswer(i -> {
            MedicalHistoryEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        CreateMedicalHistoryEntryRequest req = new CreateMedicalHistoryEntryRequest(1L, HistoryScope.PERSONAL, null, null);
        service.addEntry(recordId, req);

        assertThat(record.isNoKnownHistory()).isFalse();
        verify(clinicalRecordRepository).save(record);
    }

    @Test
    void update_noKnownHistoryTrueWithActiveEntries_throwsBusinessException() {
        UUID recordId = UUID.randomUUID();
        when(clinicalRecordRepository.findById(recordId)).thenReturn(Optional.of(ClinicalRecord.builder().id(recordId).build()));
        when(entryRepository.existsByClinicalRecordIdAndActiveTrue(recordId)).thenReturn(true);

        UpdateClinicalRecordRequest req = new UpdateClinicalRecordRequest(true, null);

        assertThatThrownBy(() -> service.update(recordId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No se puede marcar como sin antecedentes mientras haya antecedentes activos");
    }

    @Test
    void voidEntry_success_setsVoidedAtAndActiveFalse() {
        UUID entryId = UUID.randomUUID();
        MedicalHistoryEntry entry = MedicalHistoryEntry.builder().id(entryId).active(true).build();
        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(entryRepository.save(any())).thenReturn(entry);

        service.voidEntry(entryId);

        assertThat(entry.isActive()).isFalse();
        assertThat(entry.getVoidedAt()).isNotNull();
    }

    @Test
    void voidEntry_alreadyVoided_throwsBusinessException() {
        UUID entryId = UUID.randomUUID();
        MedicalHistoryEntry entry = MedicalHistoryEntry.builder().id(entryId).active(false).build();
        when(entryRepository.findById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.voidEntry(entryId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("El antecedente ya se encuentra anulado");
    }
}
