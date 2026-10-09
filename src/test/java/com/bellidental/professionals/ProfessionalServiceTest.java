package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.ProfessionalSpecialtyId;
import com.bellidental.persistence.entity.Specialty;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.SpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfessionalServiceTest {

    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    @InjectMocks
    private ProfessionalService professionalService;

    @Test
    void syncSpecialties_addsNewAndDeactivatesOld() {
        UUID profId = UUID.randomUUID();
        Professional prof = Professional.builder().id(profId).build();

        Specialty spec1 = Specialty.builder().id(1L).active(true).build();
        Specialty spec2 = Specialty.builder().id(2L).active(true).build();

        ProfessionalSpecialty ps1 = ProfessionalSpecialty.builder()
                .id(new ProfessionalSpecialtyId(profId, 1L))
                .professional(prof).specialty(spec1).active(true).build();

        when(professionalSpecialtyRepository.findByProfessionalId(profId)).thenReturn(List.of(ps1));
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(spec2));
        
        when(professionalRepository.findByIdAndDeletedAtIsNull(profId)).thenReturn(Optional.of(prof));
        when(professionalRepository.save(any(Professional.class))).thenReturn(prof);
        UpdateProfessionalRequest req = new UpdateProfessionalRequest(null, null, null, null, null, null, null, null, List.of(2L));
        
        professionalService.update(profId, req);

        assertThat(ps1.isActive()).isFalse(); // spec 1 is removed
        verify(professionalSpecialtyRepository).save(ps1);
        org.mockito.Mockito.verify(professionalSpecialtyRepository, org.mockito.Mockito.times(2)).save(any(ProfessionalSpecialty.class));
    }

    @Test
    void create_userIdAlreadyLinked_throwsConflict() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(UUID.randomUUID(), DocumentType.CI, "1710034065", "Juan", "Perez", "123", "0987654321", null, "#FF0000", null);
        
        when(userRepository.findByIdAndDeletedAtIsNull(req.userId())).thenReturn(Optional.of(User.builder().id(req.userId()).build()));
        when(professionalRepository.findByUserId(req.userId())).thenReturn(Optional.of(Professional.builder().build()));

        assertThatThrownBy(() -> professionalService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("El usuario ya tiene un perfil de profesional");
    }

    @Test
    void create_duplicateDocument_throwsBusinessException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(null, DocumentType.CI, "1710034065", "Juan", "Perez", "123", "0987654321", null, "#FF0000", null);
        when(professionalRepository.existsByDocumentTypeAndDocumentNumber(DocumentType.CI, "1710034065")).thenReturn(true);

        assertThatThrownBy(() -> professionalService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe un profesional con el documento");
    }

    @Test
    void create_duplicateLicense_throwsBusinessException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(null, DocumentType.CI, "1710034065", "Juan", "Perez", "LIC-123", "0987654321", null, "#FF0000", null);
        when(professionalRepository.existsByLicenseNumber("LIC-123")).thenReturn(true);

        assertThatThrownBy(() -> professionalService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("El número de licencia 'LIC-123' ya está registrado.");
    }

    @Test
    void syncSpecialties_reactivatesInactiveSpecialty() {
        UUID profId = UUID.randomUUID();
        Professional prof = Professional.builder().id(profId).build();

        Specialty spec1 = Specialty.builder().id(1L).active(true).build();

        ProfessionalSpecialty ps1 = ProfessionalSpecialty.builder()
                .id(new ProfessionalSpecialtyId(profId, 1L))
                .professional(prof).specialty(spec1).active(false).build();

        when(professionalSpecialtyRepository.findByProfessionalId(profId)).thenReturn(List.of(ps1));
        
        when(professionalRepository.findByIdAndDeletedAtIsNull(profId)).thenReturn(Optional.of(prof));
        when(professionalRepository.save(any(Professional.class))).thenReturn(prof);
        UpdateProfessionalRequest req = new UpdateProfessionalRequest(null, null, null, null, null, null, null, null, List.of(1L));
        
        professionalService.update(profId, req);

        assertThat(ps1.isActive()).isTrue();
        verify(professionalSpecialtyRepository).save(ps1);
    }
}
