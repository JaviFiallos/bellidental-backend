package com.bellidental.audit.api;

import com.bellidental.audit.api.dto.AuditEntryResponse;
import com.bellidental.audit.api.dto.AuditLogEntryResponse;
import com.bellidental.audit.application.AuditLogService;
import com.bellidental.audit.application.AuditService;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AuditLogEntryResponse>>> listAudit(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.listAudit(from, to, entity, userId, patientId, page, size)));
    }

    @GetMapping("/encounters/{id}/full")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditEntryResponse>>> getEncounterFullHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.findEncounterFullHistory(id)));
    }

    @GetMapping("/{entity}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditEntryResponse>>> getHistory(@PathVariable String entity, @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.findHistory(entity, id)));
    }
}
