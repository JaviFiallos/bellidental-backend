package com.bellidental.audit.api;

import com.bellidental.audit.api.dto.AuditEntryResponse;
import com.bellidental.audit.application.AuditService;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/{entity}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditEntryResponse>>> getHistory(@PathVariable String entity, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.findHistory(entity, id)));
    }
}
