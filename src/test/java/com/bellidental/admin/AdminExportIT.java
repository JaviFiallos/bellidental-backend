package com.bellidental.admin;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AdminExportIT {

    private static final String SEED_PASSWORD = "Cambiar.2026!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void export_asAdmin_returnsZipWithAllTablesAndNoPasswordHashes() throws IOException {
        HttpHeaders headers = bearerHeaders("director@bellidental.local");

        ResponseEntity<byte[]> response = restTemplate.exchange("/api/v1/admin/export",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.valueOf("application/zip"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("bellidental-export-");

        Map<String, String> entries = readZip(response.getBody());
        assertThat(entries.keySet().iterator().next()).isEqualTo("README.txt");
        assertThat(entries).containsKeys("patients.csv", "users.csv");
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith("_aud.csv"));
        assertThat(entries).doesNotContainKey("flyway_schema_history.csv");

        String usersCsv = entries.get("users.csv");
        assertThat(usersCsv.lines().findFirst().orElseThrow()).contains("password_hash");
        assertThat(usersCsv).doesNotContain("$2");
    }

    @Test
    void export_asSecretaria_returnsForbidden() {
        HttpHeaders headers = bearerHeaders("secretaria@bellidental.local");

        ResponseEntity<byte[]> response = restTemplate.exchange("/api/v1/admin/export",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders bearerHeaders(String email) {
        ResponseEntity<ApiResponse<AuthResponse>> login = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, SEED_PASSWORD)),
                new ParameterizedTypeReference<>() {});
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getData().accessToken());
        return headers;
    }

    private Map<String, String> readZip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                zip.transferTo(content);
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
