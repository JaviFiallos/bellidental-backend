package com.bellidental.bot.api;

import com.bellidental.bot.api.dto.DueReminderResponse;
import com.bellidental.bot.api.dto.ReminderReplyRequest;
import com.bellidental.bot.api.dto.ReminderReplyResponse;
import com.bellidental.bot.api.dto.ReminderSentRequest;
import com.bellidental.bot.application.BotReminderService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bot/reminders")
@RequiredArgsConstructor
public class BotReminderController {

    private final BotReminderService botReminderService;

    @GetMapping("/due")
    public ResponseEntity<ApiResponse<List<DueReminderResponse>>> getDueReminders() {
        return ResponseEntity.ok(ApiResponse.ok(botReminderService.getDueReminders()));
    }

    @PostMapping("/{id}/sent")
    public ResponseEntity<ApiResponse<Void>> markSent(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderSentRequest request
    ) {
        botReminderService.markSent(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Recordatorio marcado como enviado."));
    }

    @PostMapping("/{id}/failed")
    public ResponseEntity<ApiResponse<Void>> markFailed(@PathVariable UUID id) {
        botReminderService.markFailed(id);
        return ResponseEntity.ok(ApiResponse.ok("Recordatorio marcado como fallido."));
    }

    @PostMapping("/response")
    public ResponseEntity<ApiResponse<ReminderReplyResponse>> handleReply(
            @Valid @RequestBody ReminderReplyRequest request
    ) {
        ReminderReplyResponse response = botReminderService.handleReply(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
