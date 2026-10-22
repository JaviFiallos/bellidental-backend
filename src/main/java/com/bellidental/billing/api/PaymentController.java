package com.bellidental.billing.api;

import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.CreatePaymentRequest;
import com.bellidental.billing.api.dto.PaymentListItemResponse;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.billing.api.dto.VoidPaymentRequest;
import com.bellidental.billing.application.AccountService;
import com.bellidental.billing.application.PaymentService;
import com.bellidental.billing.application.ReceiptPdfService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AccountService accountService;
    private final ReceiptPdfService receiptPdfService;

    @PostMapping("/patients/{patientId}/payments")
    @PreAuthorize("hasAuthority('BILLING_CREATE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> registerPayment(@PathVariable UUID patientId, @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.register(patientId, request)));
    }

    @GetMapping("/patients/{patientId}/payments")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<ApiResponse<List<PaymentListItemResponse>>> listPayments(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.listByPatient(patientId)));
    }

    @GetMapping("/patients/{patientId}/account")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.findAccount(patientId)));
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("hasAuthority('BILLING_CREATE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> voidPayment(@PathVariable UUID id, @Valid @RequestBody VoidPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.voidPayment(id, request)));
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("hasAuthority('BILLING_READ')")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.findById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(payment.receiptNumber() + ".pdf").build().toString())
                .body(receiptPdfService.generateReceipt(id));
    }
}
