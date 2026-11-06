package com.bellidental.admin.api;

import com.bellidental.admin.application.DataExportService;
import com.bellidental.admin.domain.CsvTableExport;
import com.bellidental.shared.time.ClinicClock;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminExportController {

    private final DataExportService dataExportService;
    private final ClinicClock clinicClock;

    // Síncrono a propósito: StreamingResponseBody haría un async dispatch que el filtro JWT no autentica.
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + CsvTableExport.exportFileName(clinicClock.now()) + "\"");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        dataExportService.export(response.getOutputStream());
    }
}
