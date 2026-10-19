package com.bellidental.bot.api;

import com.bellidental.bot.api.dto.BotPatientResponse;
import com.bellidental.bot.api.dto.ClinicInfoResponse;
import com.bellidental.bot.api.dto.ConsentRequest;
import com.bellidental.bot.api.dto.ConsentResponse;
import com.bellidental.bot.api.dto.VerifyPatientRequest;
import com.bellidental.bot.application.BotConsentService;
import com.bellidental.bot.application.BotIdentityService;
import com.bellidental.bot.application.BotInfoService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bot")
@RequiredArgsConstructor
public class BotController {

    private final BotConsentService botConsentService;
    private final BotInfoService botInfoService;
    private final BotIdentityService botIdentityService;

    @GetMapping("/consents")
    public ResponseEntity<ApiResponse<ConsentResponse>> getConsent(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(botConsentService.getConsent(phone)));
    }

    @PostMapping("/consents")
    public ResponseEntity<ApiResponse<ConsentResponse>> recordConsent(@Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(botConsentService.recordConsent(request)));
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<ClinicInfoResponse>> getClinicInfo() {
        return ResponseEntity.ok(ApiResponse.ok(botInfoService.getClinicInfo()));
    }

    @GetMapping("/patients")
    public ResponseEntity<ApiResponse<List<BotPatientResponse>>> getPatientsByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.ok(botIdentityService.getPatientsByPhone(phone)));
    }

    @PostMapping("/patients/verify")
    public ResponseEntity<ApiResponse<BotPatientResponse>> verifyPatient(@Valid @RequestBody VerifyPatientRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(botIdentityService.verifyPatient(request)));
    }
}
