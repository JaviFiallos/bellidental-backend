package com.bellidental.prescriptions.application;

import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.config.ClinicProperties;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.prescriptions.api.dto.PrescriptionItemResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.PrescriptionStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrescriptionPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font TEXT_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);

    private final PrescriptionService prescriptionService;
    private final ClinicalDirectory clinicalDirectory;
    private final PatientDirectory patientDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final ClinicProperties clinicProperties;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID prescriptionId) {
        PrescriptionResponse prescription = prescriptionService.findById(prescriptionId);
        requireIssued(prescription);

        EncounterSummary encounter = clinicalDirectory.findEncounter(prescription.encounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));
        PatientSummary patient = patientDirectory.findById(encounter.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
        ProfessionalSummary professional = professionalDirectory.findById(encounter.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado"));

        return render(prescription, patient, professional);
    }

    private void requireIssued(PrescriptionResponse prescription) {
        if (prescription.status() != PrescriptionStatus.ISSUED || prescription.voided()) {
            throw new BusinessException("Solo se puede imprimir una receta emitida.");
        }
    }

    private byte[] render(PrescriptionResponse prescription, PatientSummary patient, ProfessionalSummary professional) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, output);
            document.open();
            addHeader(document);
            addPatient(document, patient, prescription.prescribedAt());
            addDiagnosis(document, prescription);
            addItems(document, prescription.items());
            addGeneralInstructions(document, prescription.generalInstructions());
            addFooter(document, professional);
            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la receta", e);
        }
    }

    private void addHeader(Document document) throws DocumentException {
        document.add(centered(clinicProperties.name(), TITLE_FONT));
        document.add(centered(clinicProperties.address(), TEXT_FONT));
        document.add(centered("Tel: " + clinicProperties.phone(), TEXT_FONT));
        document.add(centered("CONFIDENCIAL", BOLD_FONT));
        document.add(new Paragraph(" "));
    }

    private void addPatient(Document document, PatientSummary patient, OffsetDateTime prescribedAt) throws DocumentException {
        LocalDate issueDate = prescribedAt.atZoneSameInstant(clinicClock.zone()).toLocalDate();
        int age = Period.between(patient.birthDate(), issueDate).getYears();
        document.add(line("Paciente: ", patient.firstName() + " " + patient.lastName()));
        String documentLabel = patient.documentType() == DocumentType.CI ? "Cédula: " : "Documento: ";
        document.add(line(documentLabel, patient.documentNumber()));
        document.add(line("Edad: ", age + " años"));
        document.add(line("Fecha de emisión: ", issueDate.format(DATE_FORMAT)));
        document.add(new Paragraph(" "));
    }

    private void addDiagnosis(Document document, PrescriptionResponse prescription) throws DocumentException {
        if (prescription.icd10Code() == null) {
            return;
        }
        document.add(line("Diagnóstico: ", prescription.icd10Code() + " - " + prescription.icd10Description()));
        document.add(new Paragraph(" "));
    }

    private void addItems(Document document, List<PrescriptionItemResponse> items) throws DocumentException {
        int number = 1;
        for (PrescriptionItemResponse item : items) {
            document.add(new Paragraph(number + ". " + item.medicationName() + " " + item.concentration()
                    + " (" + item.presentation() + ")", BOLD_FONT));
            document.add(indented("Dosis: " + item.dose()));
            document.add(indented("Frecuencia: " + item.frequency()));
            document.add(indented("Duración: " + item.duration()));
            if (isPresent(item.quantity())) {
                document.add(indented("Cantidad: " + item.quantity()));
            }
            if (isPresent(item.instructions())) {
                document.add(indented("Indicaciones: " + item.instructions()));
            }
            document.add(new Paragraph(" "));
            number++;
        }
    }

    private void addGeneralInstructions(Document document, String generalInstructions) throws DocumentException {
        if (!isPresent(generalInstructions)) {
            return;
        }
        document.add(line("Indicaciones generales: ", generalInstructions));
        document.add(new Paragraph(" "));
    }

    private void addFooter(Document document, ProfessionalSummary professional) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        document.add(rightAligned("_______________________________", TEXT_FONT));
        document.add(rightAligned(professional.firstName() + " " + professional.lastName(), BOLD_FONT));
        if (isPresent(professional.licenseNumber())) {
            document.add(rightAligned("Reg. " + professional.licenseNumber(), TEXT_FONT));
        }
        document.add(rightAligned("Firma y sello", TEXT_FONT));
    }

    private Paragraph centered(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private Paragraph rightAligned(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private Paragraph line(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label, BOLD_FONT));
        paragraph.add(new Chunk(value, TEXT_FONT));
        return paragraph;
    }

    private Paragraph indented(String text) {
        Paragraph paragraph = new Paragraph(text, TEXT_FONT);
        paragraph.setIndentationLeft(15);
        return paragraph;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
