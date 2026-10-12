package com.bellidental.appointments;

import com.bellidental.appointments.api.dto.EffectiveDayResponse;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.appointments.api.dto.ScheduleResponse;
import com.bellidental.appointments.domain.Shift;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 006c: todos los endpoints de horarios responden con el envoltorio ApiResponse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ScheduleApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private UserRepository userRepository;

    private HttpHeaders headers;
    private UUID professionalId;

    @BeforeEach
    void setUp() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().getData().accessToken());

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional professional = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = professional.getId();
    }

    @Test
    void createSchedule_thenEffectiveSchedule_returnsWrappedData() {
        LocalDate day = LocalDate.now().plusDays(45);
        ScheduleRequest request = new ScheduleRequest(null, day, LocalTime.of(8, 0), LocalTime.of(12, 0), true);

        ResponseEntity<ApiResponse<ScheduleResponse>> created = restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().isSuccess()).isTrue();
        assertThat(created.getBody().getMessage()).isEqualTo("Horario registrado.");
        assertThat(created.getBody().getData().id()).isNotNull();

        ResponseEntity<ApiResponse<List<EffectiveDayResponse>>> effective = restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules/effective?from=" + day + "&to=" + day,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(effective.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(effective.getBody().isSuccess()).isTrue();
        assertThat(effective.getBody().getData()).hasSize(1);
        EffectiveDayResponse effectiveDay = effective.getBody().getData().get(0);
        assertThat(effectiveDay.date()).isEqualTo(day);
        assertThat(effectiveDay.shifts()).contains(new Shift(LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void updateAndDeactivateSchedule_returnWrappedMessages() {
        LocalDate day = LocalDate.now().plusDays(46);
        ScheduleRequest request = new ScheduleRequest(null, day, LocalTime.of(8, 0), LocalTime.of(12, 0), true);
        ResponseEntity<ApiResponse<ScheduleResponse>> created = restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );
        Long scheduleId = created.getBody().getData().id();

        ScheduleRequest update = new ScheduleRequest(null, day, LocalTime.of(9, 0), LocalTime.of(13, 0), true);
        ResponseEntity<ApiResponse<ScheduleResponse>> updated = restTemplate.exchange(
                "/api/v1/schedules/" + scheduleId,
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getMessage()).isEqualTo("Horario actualizado.");
        assertThat(updated.getBody().getData().startTime()).isEqualTo(LocalTime.of(9, 0));

        ResponseEntity<ApiResponse<Void>> deactivated = restTemplate.exchange(
                "/api/v1/schedules/" + scheduleId + "/deactivate",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivated.getBody().isSuccess()).isTrue();
        assertThat(deactivated.getBody().getMessage()).isEqualTo("Horario desactivado.");
    }
}
