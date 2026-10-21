package com.bellidental.clinical.api;

import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.application.Icd10Service;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/icd10")
@RequiredArgsConstructor
public class Icd10Controller {

    private final Icd10Service icd10Service;

    @GetMapping
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<Icd10Response>>> searchIcd10(@RequestParam(defaultValue = "") String query) {
        return ResponseEntity.ok(ApiResponse.ok(icd10Service.search(query)));
    }
}
