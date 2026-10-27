package com.bellidental.bot;

import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.bot.api.dto.BotAppointmentResponse;
import com.bellidental.bot.api.dto.BotSlotResponse;
import com.bellidental.bot.api.dto.BotSpecialtyResponse;
import com.bellidental.bot.api.dto.UrgentSlotsResponse;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientResponse;
import com.bellidental.professionals.CreateSpecialtyRequest;
import com.bellidental.professionals.SpecialtyResponse;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.MutableClock;
import com.bellidental.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BotAvailabilityIT {

    private static final String BOT_KEY = "it-bot-key";
    private final ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MutableClock clock;

    private HttpHeaders staffHeaders;
    private UUID professionalId;
    private LocalDate today;
    private LocalDate tomorrow;
    private LocalDate dayAfterTomorrow;

    private HttpHeaders botHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Bot-Key", BOT_KEY);
        return headers;
    }

    @BeforeEach
    void setUp() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        staffHeaders = new HttpHeaders();
        staffHeaders.setBearerAuth(loginResp.getBody().getData().accessToken());

        User user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional professional = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = professional.getId();

        today = LocalDate.now(clinicZone);
        tomorrow = today.plusDays(1);
        dayAfterTomorrow = today.plusDays(2);

        // Fijar MutableClock a hoy 10:00 en la zona de la clínica
        clock.fix(today.atTime(10, 0).atZone(clinicZone).toInstant());

        // Horario de odontóloga para mañana y pasado mañana creado por la API de personal
        createSchedule(tomorrow);
        createSchedule(dayAfterTomorrow);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private void createSchedule(LocalDate day) {
        ScheduleRequest scheduleReq = new ScheduleRequest(null, day, LocalTime.of(8, 0), LocalTime.of(20, 0), true);
        restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleReq, staffHeaders),
                Void.class
        );
    }

    @Test
    void availability_withoutProfessional_listsDaysAndSlotsWithProfessionalName() {
        // 1. days contiene mañana
        ResponseEntity<ApiResponse<List<LocalDate>>> daysResp = restTemplate.exchange(
                "/api/v1/bot/availability/days?specialtyId=1",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(daysResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(daysResp.getBody()).isNotNull();
        List<LocalDate> days = daysResp.getBody().getData();
        assertThat(days).contains(tomorrow);

        // 2. availability de mañana → 1..10 huecos ordenados con professionalName
        ResponseEntity<ApiResponse<List<BotSlotResponse>>> slotsResp = restTemplate.exchange(
                "/api/v1/bot/availability?specialtyId=1&date=" + tomorrow,
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(slotsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slotsResp.getBody()).isNotNull();
        List<BotSlotResponse> slots = slotsResp.getBody().getData();
        assertThat(slots).isNotEmpty();
        assertThat(slots.size()).isLessThanOrEqualTo(10);
        assertThat(slots).isSortedAccordingTo(Comparator.comparing(BotSlotResponse::startAt));
        assertThat(slots.get(0).professionalName()).isNotBlank();
    }

    @Test
    void specialties_withoutActiveProfessional_omitsSpecialty() {
        LoginRequest login = new LoginRequest("director@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        HttpHeaders directorHeaders = new HttpHeaders();
        directorHeaders.setBearerAuth(loginResp.getBody().getData().accessToken());

        // Especialidad activa sin profesionales
        ResponseEntity<ApiResponse<SpecialtyResponse>> created = restTemplate.exchange(
                "/api/v1/specialties",
                HttpMethod.POST,
                new HttpEntity<>(new CreateSpecialtyRequest("Sin profesionales " + UUID.randomUUID(), 30), directorHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long emptySpecialtyId = created.getBody().getData().id();

        ResponseEntity<ApiResponse<List<BotSpecialtyResponse>>> resp = restTemplate.exchange(
                "/api/v1/bot/specialties",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<BotSpecialtyResponse> specialties = resp.getBody().getData();
        assertThat(specialties).noneMatch(s -> s.specialtyId().equals(emptySpecialtyId));
        assertThat(specialties).anyMatch(s -> s.specialtyId().equals(1L));
    }

    @Test
    void urgentSlots_noScheduleToday_returnsTomorrowAndFlagsNoSlotsToday() {
        ResponseEntity<ApiResponse<UrgentSlotsResponse>> resp = restTemplate.exchange(
                "/api/v1/bot/urgent-slots",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        UrgentSlotsResponse data = resp.getBody().getData();
        assertThat(data.specialtyId()).isEqualTo(1L);
        assertThat(data.noSlotsToday()).isTrue();
        assertThat(data.slots()).isNotEmpty();
        assertThat(data.slots().size()).isLessThanOrEqualTo(3);
        assertThat(data.slots()).allMatch(s -> s.startAt().toLocalDate().equals(tomorrow));
    }

    @Test
    void appointments_phoneNotOwningPatient_returns404() {
        String patientPhone = "098" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        CreatePatientRequest p = new CreatePatientRequest(
                DocumentType.PASAPORTE, "PASS-" + UUID.randomUUID().toString().substring(0, 8),
                "Paciente", "Prueba", LocalDate.of(1995, 3, 10), "M", null,
                patientPhone, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> created = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(p, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID patientId = created.getBody().getData().id();

        // Consulta con un teléfono ajeno
        String wrongPhone = "593970000000";
        ResponseEntity<ApiResponse<List<BotAppointmentResponse>>> resp = restTemplate.exchange(
                "/api/v1/bot/appointments?phone=" + wrongPhone + "&patientId=" + patientId,
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getMessage()).isEqualTo("Paciente no encontrado.");
    }
}
