package com.bellidental.identity;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.ChangePasswordRequest;
import com.bellidental.identity.api.dto.CreateUserRequest;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UserManagementIT {

    private static final String DIRECTOR_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SEED_PASSWORD = "Cambiar.2026!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createUser_asAdmin_canLoginAndChangeOwnPassword() {
        HttpHeaders directorHeaders = headersFor("director@bellidental.local", SEED_PASSWORD);
        String email = randomEmail();
        String temporaryPassword = "Temporal.2026!";

        ResponseEntity<ApiResponse<UserResponse>> created = exchange(HttpMethod.POST, "/api/v1/users",
                new CreateUserRequest(email, null, temporaryPassword, Set.of("SECRETARIA")), directorHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getData().roles()).containsExactly("SECRETARIA");

        assertThat(login(email, temporaryPassword).getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders userHeaders = headersFor(email, temporaryPassword);

        ResponseEntity<ApiResponse<Void>> changed = exchange(HttpMethod.PUT, "/api/v1/auth/password",
                new ChangePasswordRequest(temporaryPassword, "Nueva.2026!Segura"), userHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, "Nueva.2026!Segura").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Void>> wrongCurrent = exchange(HttpMethod.PUT, "/api/v1/auth/password",
                new ChangePasswordRequest("Incorrecta.2026!", "Otra.2026!Segura"), userHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(wrongCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongCurrent.getBody().getMessage()).contains("La contraseña actual no es correcta.");
    }

    @Test
    void createUser_withAdminRole_returnsBadRequest() {
        HttpHeaders directorHeaders = headersFor("director@bellidental.local", SEED_PASSWORD);
        HttpHeaders secretariaHeaders = headersFor("secretaria@bellidental.local", SEED_PASSWORD);

        ResponseEntity<ApiResponse<UserResponse>> adminRole = exchange(HttpMethod.POST, "/api/v1/users",
                new CreateUserRequest(randomEmail(), null, "Temporal.2026!", Set.of("ADMIN")), directorHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(adminRole.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiResponse<UserResponse>> bySecretaria = exchange(HttpMethod.POST, "/api/v1/users",
                new CreateUserRequest(randomEmail(), null, "Temporal.2026!", Set.of("SECRETARIA")), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(bySecretaria.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deactivateUser_blocksLoginAndSelfDeactivationIsRejected() {
        HttpHeaders directorHeaders = headersFor("director@bellidental.local", SEED_PASSWORD);
        String email = randomEmail();
        String temporaryPassword = "Temporal.2026!";

        ResponseEntity<ApiResponse<UserResponse>> created = exchange(HttpMethod.POST, "/api/v1/users",
                new CreateUserRequest(email, null, temporaryPassword, Set.of("SECRETARIA")), directorHeaders,
                new ParameterizedTypeReference<>() {});
        UUID userId = created.getBody().getData().id();

        ResponseEntity<ApiResponse<UserResponse>> deactivated = exchange(HttpMethod.POST,
                "/api/v1/users/" + userId + "/deactivate", null, directorHeaders, new ParameterizedTypeReference<>() {});
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, temporaryPassword).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ApiResponse<UserResponse>> activated = exchange(HttpMethod.POST,
                "/api/v1/users/" + userId + "/activate", null, directorHeaders, new ParameterizedTypeReference<>() {});
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, temporaryPassword).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<UserResponse>> selfDeactivation = exchange(HttpMethod.POST,
                "/api/v1/users/" + DIRECTOR_ID + "/deactivate", null, directorHeaders, new ParameterizedTypeReference<>() {});
        assertThat(selfDeactivation.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private String randomEmail() {
        return "sec-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    private ResponseEntity<ApiResponse<AuthResponse>> login(String email, String password) {
        return restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders headersFor(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login(email, password).getBody().getData().accessToken());
        return headers;
    }

    private <T> ResponseEntity<ApiResponse<T>> exchange(HttpMethod method, String url, Object body, HttpHeaders headers,
                                                        ParameterizedTypeReference<ApiResponse<T>> type) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
