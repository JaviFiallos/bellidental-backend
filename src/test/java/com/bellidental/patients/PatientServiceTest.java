package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(patientService, "clinicZone", "America/Guayaquil");
    }

    @Test
    void create_duplicateDocument_throwsBusinessException() {
        CreatePatientRequest req = new CreatePatientRequest(DocumentType.CI, "1710034065", "Juan", "Perez", LocalDate.now(), "M", null, "0987654321", null, null, null, null);
        when(patientRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.CI, "1710034065")).thenReturn(true);

        assertThatThrownBy(() -> patientService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe un paciente con el documento");
    }

    @Test
    void search_withDigits_usesDocumentOrPhone() {
        Patient patient = Patient.builder().id(UUID.randomUUID()).birthDate(LocalDate.now()).build();
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepository.findActiveByDocumentOrPhonePrefix(eq("1710"), eq("1710%"), any(Pageable.class)))
                .thenReturn(page);

        Page<PatientResponse> res = patientService.search("1710", 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void search_withPhone0_replacesWith593() {
        Patient patient = Patient.builder().id(UUID.randomUUID()).birthDate(LocalDate.now()).build();
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepository.findActiveByDocumentOrPhonePrefix(eq("0987"), eq("593987%"), any(Pageable.class)))
                .thenReturn(page);

        Page<PatientResponse> res = patientService.search("0987", 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void search_withText_usesNameOrLastName() {
        Patient patient = Patient.builder().id(UUID.randomUUID()).birthDate(LocalDate.now()).build();
        Page<Patient> page = new PageImpl<>(List.of(patient));
        when(patientRepository.findActiveByNameOrLastName(eq("Juan"), any(Pageable.class)))
                .thenReturn(page);

        Page<PatientResponse> res = patientService.search("Juan", 0, 10);
        assertThat(res.getContent()).hasSize(1);
    }

    @Test
    void search_withTextLessThan2Chars_throwsInvalidInputException() {
        assertThatThrownBy(() -> patientService.search("J", 0, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("al menos 2 caracteres");
    }

    @Test
    void changeActiveStatus_deactivate_changesActiveToFalse() {
        UUID id = UUID.randomUUID();
        Patient patient = Patient.builder().id(id).active(true).build();
        when(patientRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(patient));

        patientService.changeActiveStatus(id, false);

        assertThat(patient.isActive()).isFalse();
        verify(patientRepository).save(patient);
    }

    @Test
    void findById_bornTenYearsAgoInClinicZone_returnsAgeTen() {
        UUID id = UUID.randomUUID();
        // Nació hace 10 años en Guayaquil
        LocalDate birthDate = LocalDate.now(ZoneId.of("America/Guayaquil")).minusYears(10);
        Patient patient = Patient.builder().id(id).birthDate(birthDate).build();
        
        when(patientRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(patient));

        PatientResponse res = patientService.findById(id);
        assertThat(res.age()).isEqualTo(10);
    }
}
