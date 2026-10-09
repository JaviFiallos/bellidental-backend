package com.bellidental.identity;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.identity.api.dto.MeResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void login_secretaria_returns200WithTokenAndRole() {
        LoginRequest request = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        
        AuthResponse authResponse = response.getBody().getData();
        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.roles()).contains("SECRETARIA");
    }

    @Test
    void login_wrongPassword_returns401Json() {
        LoginRequest request = new LoginRequest("secretaria@bellidental.local", "wrongpassword");

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Credenciales inválidas");
    }

    @Test
    void login_botWhatsapp_returns401Json() {
        LoginRequest request = new LoginRequest("bot@bellidental.local", "!");

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Credenciales inválidas");
    }

    @Test
    void me_withoutToken_returns401Json() {
        ResponseEntity<ApiResponse<MeResponse>> response = restTemplate.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Credenciales inválidas");
    }

    @Test
    void me_withOdontologaToken_returns200WithProfessionalId() {
        // 1. Login to get token
        LoginRequest request = new LoginRequest("odontologa@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        String token = loginResponse.getBody().getData().accessToken();

        // 2. Call /me
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<MeResponse>> response = restTemplate.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        
        MeResponse meData = response.getBody().getData();
        assertThat(meData.email()).isEqualTo("odontologa@bellidental.local");
        assertThat(meData.professionalId()).isNotNull();
        assertThat(meData.roles()).contains("ODONTOLOGO");
    }

    @Test
    void register_endpointRemoved_returns401Or404() {
        LoginRequest request = new LoginRequest("newuser@bellidental.local", "password"); // Dummy payload

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                request,
                String.class
        );

        // Since it requires authentication and doesn't exist, it could be 401 or 404.
        // Actually, since it's not in PUBLIC_ENDPOINTS, it should be 401.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
