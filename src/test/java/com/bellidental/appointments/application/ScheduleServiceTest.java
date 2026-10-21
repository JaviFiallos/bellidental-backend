package com.bellidental.appointments.application;

import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.persistence.entity.ProfessionalSchedule;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ProfessionalScheduleRepository scheduleRepository;

    @Mock
    private ProfessionalDirectory professionalDirectory;

    @InjectMocks
    private ScheduleService scheduleService;

    private UUID professionalId;

    @BeforeEach
    void setUp() {
        professionalId = UUID.randomUUID();
    }

    @Test
    void create_weekdayAndSpecificDate_throwsInvalidInput() {
        when(professionalDirectory.findById(professionalId)).thenReturn(Optional.of(new ProfessionalSummary(professionalId, null, "John", "Doe", null, null, null, true, null)));

        ScheduleRequest request = new ScheduleRequest((short) 1, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(12, 0), true);
        
        assertThatThrownBy(() -> scheduleService.create(professionalId, request))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Debe indicar exactamente uno: weekday o specificDate.");
    }

    @Test
    void create_overlappingSchedule_throwsBusinessException() {
        when(professionalDirectory.findById(professionalId)).thenReturn(Optional.of(new ProfessionalSummary(professionalId, null, "John", "Doe", null, null, null, true, null)));
        
        ProfessionalSchedule existing = ProfessionalSchedule.builder()
                .id(1L)
                .weekday((short) 1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .available(true)
                .build();
                
        when(scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId)).thenReturn(List.of(existing));

        ScheduleRequest request = new ScheduleRequest((short) 1, null, LocalTime.of(10, 0), LocalTime.of(14, 0), true);

        assertThatThrownBy(() -> scheduleService.create(professionalId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El turno se solapa con otro horario existente.");
    }

    @Test
    void update_notOverlappingItself_saves() {
        ProfessionalSchedule existing = ProfessionalSchedule.builder()
                .id(1L)
                .professionalId(professionalId)
                .weekday((short) 1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .available(true)
                .active(true)
                .build();

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId)).thenReturn(List.of(existing));
        when(scheduleRepository.save(any())).thenReturn(existing);

        ScheduleRequest request = new ScheduleRequest((short) 1, null, LocalTime.of(9, 0), LocalTime.of(13, 0), true);

        scheduleService.update(1L, request);

        verify(scheduleRepository).save(any(ProfessionalSchedule.class));
    }
}
