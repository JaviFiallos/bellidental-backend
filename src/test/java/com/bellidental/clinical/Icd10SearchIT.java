package com.bellidental.clinical;

import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class Icd10SearchIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders loginAs(String email) {
        ResponseEntity<ApiResponse<AuthResponse>> login = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, "Cambiar.2026!")),
                new ParameterizedTypeReference<>() {}
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getData().accessToken());
        return headers;
    }

    private ResponseEntity<ApiResponse<List<Icd10Response>>> search(HttpHeaders headers, String query) {
        return restTemplate.exchange(
                "/api/v1/icd10?query=" + query,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    @Test
    void search_codePrefixAsOdontologa_returnsOrderedK02Codes() {
        ResponseEntity<ApiResponse<List<Icd10Response>>> response =
                search(loginAs("odontologa@bellidental.local"), "k02");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Icd10Response> codes = response.getBody().getData();
        assertThat(codes).hasSize(8);
        assertThat(codes).allSatisfy(code -> assertThat(code.code()).startsWith("K02"));
        assertThat(codes).extracting(Icd10Response::code).contains("K02.1", "K02.5");
        assertThat(codes).extracting(Icd10Response::code).isSorted();
    }

    @Test
    void search_textAsOdontologa_returnsCariesDentinaCode() {
        ResponseEntity<ApiResponse<List<Icd10Response>>> response =
                search(loginAs("odontologa@bellidental.local"), "caries");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).extracting(Icd10Response::code).contains("K02.1");
    }

    @Test
    void search_codeAsSecretaria_returnsForbidden() {
        ResponseEntity<ApiResponse<List<Icd10Response>>> response =
                search(loginAs("secretaria@bellidental.local"), "k02");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
