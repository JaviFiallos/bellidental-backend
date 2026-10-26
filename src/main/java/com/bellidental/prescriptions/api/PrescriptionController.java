package com.bellidental.prescriptions.api;

import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.prescriptions.application.PrescriptionPdfService;
import com.bellidental.prescriptions.application.PrescriptionService;
import com.bellidental.security.DocumentLinkResponse;
import com.bellidental.security.DocumentLinkService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionPdfService prescriptionPdfService;
    private final DocumentLinkService documentLinkService;

    @PostMapping("/encounters/{id}/prescriptions")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> createPrescription(@PathVariable UUID id, @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(prescriptionService.create(id, request)));
    }

    @GetMapping("/encounters/{id}/prescriptions")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getEncounterPrescriptions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(prescriptionService.findEncounterPrescriptions(id)));
    }

    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getPrescription(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(prescriptionService.findById(id)));
    }

    @PostMapping("/prescriptions/{id}/pdf-link")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<DocumentLinkResponse>> generatePrescriptionPdfLink(@PathVariable UUID id) {
        prescriptionPdfService.requirePrintable(id);
        DocumentLinkResponse link = documentLinkService.generateLink(DocumentLinkService.DOC_PRESCRIPTION, id, "/api/v1/prescriptions/%s/receta.pdf?t=%s");
        return ResponseEntity.ok(ApiResponse.ok(link));
    }

    @GetMapping("/prescriptions/{id}/receta.pdf")
    public ResponseEntity<?> downloadPrescriptionPdfPublic(
            @PathVariable UUID id,
            @RequestParam(name = "t", required = false) String token
    ) {
        if (!documentLinkService.verify(token, DocumentLinkService.DOC_PRESCRIPTION, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error("El enlace del documento venció o no es válido."));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("receta-" + id + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .body(prescriptionPdfService.generatePdf(id));
    }

    @GetMapping("/prescriptions/{id}/pdf")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<byte[]> downloadPrescriptionPdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("receta-" + id + ".pdf").build().toString())
                .body(prescriptionPdfService.generatePdf(id));
    }

    @PutMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> updatePrescription(@PathVariable UUID id, @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(prescriptionService.update(id, request)));
    }

    @PostMapping("/prescriptions/{id}/issue")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> issuePrescription(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(prescriptionService.issue(id)));
    }

    @PostMapping("/prescriptions/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> voidPrescription(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(prescriptionService.voidPrescription(id)));
    }
}
