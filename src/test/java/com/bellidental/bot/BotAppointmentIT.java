package com.bellidental.bot;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.bot.api.dto.BotAppointmentResponse;
import com.bellidental.bot.api.dto.BotBookRequest;
import com.bellidental.bot.api.dto.BotPhoneRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientResponse;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BotAppointmentIT {

    private static final String BOT_KEY = "it-bot-key";
    private final ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentScheduler appointmentScheduler;

    @Autowired
    private MutableClock clock;

    private HttpHeaders staffHeaders;
    private UUID professionalId;
    private LocalDate today;
    private LocalDate tomorrow;
    private LocalDate dayAfterTomorrow;
    private String patientPhone;
    private UUID patientId;

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

        // Fijar MutableClock a hoy 10:00
        clock.fix(today.atTime(10, 0).atZone(clinicZone).toInstant());

        // Horario de odontóloga para mañana y pasado mañana creado por la API de personal
        createSchedule(tomorrow);
        createSchedule(dayAfterTomorrow);

        // Crear un paciente con teléfono conocido
        patientPhone = "098" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        CreatePatientRequest p = new CreatePatientRequest(
                DocumentType.PASAPORTE, "PASS-" + UUID.randomUUID().toString().substring(0, 8),
                "Paciente", "Bot", LocalDate.of(1992, 6, 15), "M", null,
                patientPhone, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> created = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(p, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        patientId = created.getBody().getData().id();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        if (patientId != null) {
            for (AppointmentSummary appt : appointmentScheduler.findActiveByPatient(patientId)) {
                restTemplate.exchange(
                        "/api/v1/appointments/" + appt.id() + "/cancel",
                        HttpMethod.POST,
                        new HttpEntity<>(staffHeaders),
                        Void.class
                );
            }
        }
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
    void book_thenSecondInSameSpecialty_secondIs409BotLimitReached() {
        OffsetDateTime firstSlot = tomorrow.atTime(9, 0).atZone(clinicZone).toOffsetDateTime();
        BotBookRequest firstRequest = new BotBookRequest(patientPhone, patientId, 1L, professionalId, firstSlot);

        ResponseEntity<ApiResponse<BotAppointmentResponse>> firstResp = restTemplate.exchange(
                "/api/v1/bot/appointments",
                HttpMethod.POST,
                new HttpEntity<>(firstRequest, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResp.getBody()).isNotNull();
        BotAppointmentResponse firstData = firstResp.getBody().getData();
        assertThat(firstData.status()).isEqualTo(AppointmentStatus.PENDING);

        AppointmentSummary savedAppointment = appointmentScheduler.findById(firstData.appointmentId()).orElseThrow();
        assertThat(savedAppointment.source()).isEqualTo(AppointmentSource.BOT);

        // Segunda reserva en la misma especialidad
        OffsetDateTime secondSlot = tomorrow.atTime(11, 0).atZone(clinicZone).toOffsetDateTime();
        BotBookRequest secondRequest = new BotBookRequest(patientPhone, patientId, 1L, professionalId, secondSlot);

        ResponseEntity<ApiResponse<BotAppointmentResponse>> secondResp = restTemplate.exchange(
                "/api/v1/bot/appointments",
                HttpMethod.POST,
                new HttpEntity<>(secondRequest, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResp.getBody()).isNotNull();
        assertThat(secondResp.getBody().getCode()).isEqualTo("BOT_LIMIT_REACHED");
    }

    @Test
    void book_phoneNotOwningPatient_returns404() {
        String wrongPhone = "593970000000";
        OffsetDateTime slot = tomorrow.atTime(14, 0).atZone(clinicZone).toOffsetDateTime();
        BotBookRequest req = new BotBookRequest(wrongPhone, patientId, 1L, professionalId, slot);

        ResponseEntity<ApiResponse<BotAppointmentResponse>> resp = restTemplate.exchange(
                "/api/v1/bot/appointments",
                HttpMethod.POST,
                new HttpEntity<>(req, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMessage()).isEqualTo("Paciente no encontrado.");
    }

    @Test
    void cancel_lessThanTwoHoursBefore_returns409BotTooLate() {
        // Se reserva para mañana 10:00
        OffsetDateTime startAt = tomorrow.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        BotBookRequest bookReq = new BotBookRequest(patientPhone, patientId, 1L, professionalId, startAt);

        ResponseEntity<ApiResponse<BotAppointmentResponse>> bookResp = restTemplate.exchange(
                "/api/v1/bot/appointments",
                HttpMethod.POST,
                new HttpEntity<>(bookReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(bookResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = bookResp.getBody().getData().appointmentId();

        // Se fija el reloj a mañana 08:30 (faltan 1h 30m, menos de 2h)
        clock.fix(tomorrow.atTime(8, 30).atZone(clinicZone).toInstant());

        // Cancelar cita con menos de 2 horas de anticipación
        BotPhoneRequest cancelReq = new BotPhoneRequest(patientPhone);
        ResponseEntity<ApiResponse<Void>> cancelResp = restTemplate.exchange(
                "/api/v1/bot/appointments/" + appointmentId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(cancelReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancelResp.getBody()).isNotNull();
        assertThat(cancelResp.getBody().getCode()).isEqualTo("BOT_TOO_LATE");

        // La cita sigue PENDING
        AppointmentSummary currentAppointment = appointmentScheduler.findById(appointmentId).orElseThrow();
        assertThat(currentAppointment.status()).isEqualTo(AppointmentStatus.PENDING);
    }
}
