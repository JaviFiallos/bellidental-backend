package com.bellidental.bot;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.bot.api.dto.ConsentRequest;
import com.bellidental.bot.api.dto.ConsentResponse;
import com.bellidental.bot.api.dto.DueReminderResponse;
import com.bellidental.bot.api.dto.ReminderReplyRequest;
import com.bellidental.bot.api.dto.ReminderReplyResponse;
import com.bellidental.bot.api.dto.ReminderSentRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientResponse;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.User;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.ReminderResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BotReminderIT {

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
    private UUID appointmentId;

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

        // Horario de odontóloga para pasado mañana
        createSchedule(dayAfterTomorrow);

        // Crear paciente con teléfono conocido
        patientPhone = "098" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        CreatePatientRequest p = new CreatePatientRequest(
                DocumentType.PASAPORTE, "PASS-" + UUID.randomUUID().toString().substring(0, 8),
                "Paciente", "Reminder", LocalDate.of(1994, 5, 20), "M", null,
                patientPhone, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> patientResp = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(p, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(patientResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        patientId = patientResp.getBody().getData().id();

        // Consentimiento aceptado por POST /api/v1/bot/consents
        ConsentRequest consentReq = new ConsentRequest(patientPhone, null);
        ResponseEntity<ApiResponse<ConsentResponse>> consentResp = restTemplate.exchange(
                "/api/v1/bot/consents",
                HttpMethod.POST,
                new HttpEntity<>(consentReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(consentResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Cita para pasado mañana 10:00 creada por la API de personal (recepción)
        OffsetDateTime startAt = dayAfterTomorrow.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest apptReq = new CreateAppointmentRequest(
                patientId, professionalId, 1L, startAt, null, "Consulta general", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> apptResp = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(apptReq, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(apptResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        appointmentId = apptResp.getBody().getData().id();
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
    void due_dayBeforeAt0905_thenSentAndConfirmed_appointmentConfirmed() {
        // Con el reloj en mañana 09:05, due contiene la cita
        clock.fix(tomorrow.atTime(9, 5).atZone(clinicZone).toInstant());

        ResponseEntity<ApiResponse<List<DueReminderResponse>>> dueResp = restTemplate.exchange(
                "/api/v1/bot/reminders/due",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(dueResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dueResp.getBody()).isNotNull();

        DueReminderResponse dueItem = dueResp.getBody().getData().stream()
                .filter(d -> d.appointmentId().equals(appointmentId))
                .findFirst()
                .orElseThrow();
        UUID reminderId = dueItem.reminderId();

        // sent con wamid.test1
        ReminderSentRequest sentReq = new ReminderSentRequest("wamid.test1");
        ResponseEntity<ApiResponse<Void>> sentResp = restTemplate.exchange(
                "/api/v1/bot/reminders/" + reminderId + "/sent",
                HttpMethod.POST,
                new HttpEntity<>(sentReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(sentResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // response CONFIRMED -> outcome=CONFIRMED
        ReminderReplyRequest replyReq = new ReminderReplyRequest("wamid.test1", patientPhone, ReminderResponse.CONFIRMED);
        ResponseEntity<ApiResponse<ReminderReplyResponse>> replyResp = restTemplate.exchange(
                "/api/v1/bot/reminders/response",
                HttpMethod.POST,
                new HttpEntity<>(replyReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(replyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replyResp.getBody()).isNotNull();
        assertThat(replyResp.getBody().getData().outcome()).isEqualTo("CONFIRMED");

        // La cita está CONFIRMED (API de personal / scheduler)
        AppointmentSummary currentAppointment = appointmentScheduler.findById(appointmentId).orElseThrow();
        assertThat(currentAppointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);

        // due ya no la lista
        ResponseEntity<ApiResponse<List<DueReminderResponse>>> dueAfterResp = restTemplate.exchange(
                "/api/v1/bot/reminders/due",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(dueAfterResp.getBody().getData().stream().noneMatch(d -> d.appointmentId().equals(appointmentId))).isTrue();
    }

    @Test
    void cancelAppointment_beforeDue_reminderNotListed() {
        // Se cancela la cita por la API de personal
        ResponseEntity<ApiResponse<Void>> cancelResp = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Con el reloj en mañana 09:05, due no la contiene
        clock.fix(tomorrow.atTime(9, 5).atZone(clinicZone).toInstant());

        ResponseEntity<ApiResponse<List<DueReminderResponse>>> dueResp = restTemplate.exchange(
                "/api/v1/bot/reminders/due",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(dueResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dueResp.getBody().getData().stream().noneMatch(d -> d.appointmentId().equals(appointmentId))).isTrue();
    }

    @Test
    void response_cancelLessThanTwoHoursBefore_tooLateAndAppointmentUnchanged() {
        // Reloj a mañana 09:05 para encontrar el recordatorio vencido
        clock.fix(tomorrow.atTime(9, 5).atZone(clinicZone).toInstant());
        ResponseEntity<ApiResponse<List<DueReminderResponse>>> dueResp = restTemplate.exchange(
                "/api/v1/bot/reminders/due",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        DueReminderResponse dueItem = dueResp.getBody().getData().stream()
                .filter(d -> d.appointmentId().equals(appointmentId))
                .findFirst()
                .orElseThrow();

        // Se marca como enviado
        ReminderSentRequest sentReq = new ReminderSentRequest("wamid.test3");
        ResponseEntity<ApiResponse<Void>> sentResp = restTemplate.exchange(
                "/api/v1/bot/reminders/" + dueItem.reminderId() + "/sent",
                HttpMethod.POST,
                new HttpEntity<>(sentReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(sentResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Se fija el reloj a pasado mañana 08:30 (faltan 1h 30m para las 10:00)
        clock.fix(dayAfterTomorrow.atTime(8, 30).atZone(clinicZone).toInstant());

        // CANCELLED -> outcome=TOO_LATE y la cita sigue PENDING
        ReminderReplyRequest replyReq = new ReminderReplyRequest("wamid.test3", patientPhone, ReminderResponse.CANCELLED);
        ResponseEntity<ApiResponse<ReminderReplyResponse>> replyResp = restTemplate.exchange(
                "/api/v1/bot/reminders/response",
                HttpMethod.POST,
                new HttpEntity<>(replyReq, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(replyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replyResp.getBody().getData().outcome()).isEqualTo("TOO_LATE");

        AppointmentSummary currentAppointment = appointmentScheduler.findById(appointmentId).orElseThrow();
        assertThat(currentAppointment.status()).isEqualTo(AppointmentStatus.PENDING);
    }
}
