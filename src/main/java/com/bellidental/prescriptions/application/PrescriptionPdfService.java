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
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
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
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final PrescriptionService prescriptionService;
    private final ClinicalDirectory clinicalDirectory;
    private final PatientDirectory patientDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final ClinicProperties clinicProperties;
    private final ClinicClock clinicClock;
    private final PrescriptionPdfImages images;

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

    public void requirePrintable(UUID prescriptionId) {
        requireIssued(prescriptionService.findById(prescriptionId));
    }

    private void requireIssued(PrescriptionResponse prescription) {
        if (prescription.status() != PrescriptionStatus.ISSUED || prescription.voided()) {
            throw new BusinessException("Solo se puede imprimir una receta emitida.");
        }
    }

    private byte[] render(PrescriptionResponse prescription, PatientSummary patient, ProfessionalSummary professional) {
        Document document = new Document(PageSize.A4, 36f, 36f, 36f, 70f);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PrescriptionPdfPageDecorator(clinicProperties, images));
            document.open();

            LocalDate issueDate = prescription.prescribedAt().atZoneSameInstant(clinicClock.zone()).toLocalDate();

            addHeader(document, professional, issueDate);
            addAccentLine(document);
            addPatient(document, patient, issueDate, prescription);
            addBody(document, prescription);
            addClosingBlock(writer, document, professional);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la receta", e);
        }
    }

    private void addHeader(Document document, ProfessionalSummary professional, LocalDate issueDate) throws DocumentException, IOException {
        PdfPTable headerTable = new PdfPTable(new float[]{78f, 325f, 120f});
        headerTable.setWidthPercentage(100f);
        headerTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // 1. Columna izquierda: Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        Image logo = Image.getInstance(images.logo());
        logo.scaleToFit(72f, 72f);
        logoCell.addElement(logo);
        headerTable.addCell(logoCell);

        // 2. Columna centro: Datos de la clínica y profesional
        PdfPCell clinicCell = new PdfPCell();
        clinicCell.setBorder(Rectangle.NO_BORDER);
        clinicCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        clinicCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph clinicName = new Paragraph(clinicProperties.name(), PrescriptionPdfPalette.FONT_CLINIC_NAME);
        clinicName.setAlignment(Element.ALIGN_CENTER);
        clinicCell.addElement(clinicName);

        if (isPresent(clinicProperties.tagline())) {
            Paragraph tagline = new Paragraph(spacedText(clinicProperties.tagline().toUpperCase()), PrescriptionPdfPalette.FONT_TAGLINE);
            tagline.setAlignment(Element.ALIGN_CENTER);
            tagline.setSpacingAfter(4f);
            clinicCell.addElement(tagline);
        }

        String doctorTitle = "Od. " + professional.firstName() + " " + professional.lastName();
        Paragraph doctor = new Paragraph(doctorTitle, PrescriptionPdfPalette.FONT_DOCTOR_HEADER);
        doctor.setAlignment(Element.ALIGN_CENTER);
        clinicCell.addElement(doctor);

        if (isPresent(professional.licenseNumber())) {
            Paragraph license = new Paragraph("Reg. " + professional.licenseNumber(), PrescriptionPdfPalette.FONT_DOCTOR_LICENSE);
            license.setAlignment(Element.ALIGN_CENTER);
            clinicCell.addElement(license);
        }

        String contact = clinicProperties.address() + " · Tel. " + clinicProperties.phone();
        Paragraph clinicContact = new Paragraph(contact, PrescriptionPdfPalette.FONT_CLINIC_CONTACT);
        clinicContact.setAlignment(Element.ALIGN_CENTER);
        clinicContact.setSpacingBefore(2f);
        clinicCell.addElement(clinicContact);

        headerTable.addCell(clinicCell);

        // 3. Columna derecha: Recuadro D/M/A, RECETA y CONFIDENCIAL
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPTable dateBox = createDateBox(issueDate);
        dateBox.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateBox);

        Paragraph recetaTitle = new Paragraph("RECETA", PrescriptionPdfPalette.FONT_RECETA_TITLE);
        recetaTitle.setAlignment(Element.ALIGN_RIGHT);
        recetaTitle.setSpacingBefore(4f);
        rightCell.addElement(recetaTitle);

        Paragraph confidential = new Paragraph("CONFIDENCIAL", PrescriptionPdfPalette.FONT_CONFIDENTIAL);
        confidential.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(confidential);

        headerTable.addCell(rightCell);

        document.add(headerTable);
    }

    private PdfPTable createDateBox(LocalDate issueDate) {
        PdfPTable table = new PdfPTable(3);
        table.setTotalWidth(96f);
        table.setLockedWidth(true);

        String[] headers = {"D", "M", "A"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, PrescriptionPdfPalette.FONT_DATE_HEADER));
            cell.setBackgroundColor(PrescriptionPdfPalette.BLUE_800);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(2f);
            cell.setBorderColor(PrescriptionPdfPalette.BLUE_800);
            table.addCell(cell);
        }

        String[] values = {
                issueDate.format(DAY_FORMAT),
                issueDate.format(MONTH_FORMAT),
                issueDate.format(YEAR_FORMAT)
        };
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, PrescriptionPdfPalette.FONT_DATE_VALUE));
            cell.setBackgroundColor(PrescriptionPdfPalette.WHITE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(3f);
            cell.setBorderColor(PrescriptionPdfPalette.BLUE_800);
            table.addCell(cell);
        }

        return table;
    }

    private void addAccentLine(Document document) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100f);
        line.setSpacingBefore(6f);
        line.setSpacingAfter(6f);

        PdfPCell lineCell = new PdfPCell();
        lineCell.setFixedHeight(2f);
        lineCell.setBackgroundColor(PrescriptionPdfPalette.GOLD_400);
        lineCell.setBorder(Rectangle.NO_BORDER);
        line.addCell(lineCell);

        document.add(line);
    }

    private void addPatient(Document document, PatientSummary patient, LocalDate issueDate, PrescriptionResponse prescription) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);

        int age = Period.between(patient.birthDate(), issueDate).getYears();
        String docLabel = patient.documentType() == DocumentType.CI ? "Cédula: " : "Documento: ";

        table.addCell(createPatientCell("Paciente: ", patient.firstName() + " " + patient.lastName()));
        table.addCell(createPatientCell(docLabel, patient.documentNumber()));
        table.addCell(createPatientCell("Edad: ", age + " años"));
        table.addCell(createPatientCell("Fecha de emisión: ", issueDate.format(DATE_FORMAT)));

        if (isPresent(prescription.icd10Code())) {
            String diagText = prescription.icd10Code() + " - " + (prescription.icd10Description() != null ? prescription.icd10Description() : "");
            PdfPCell diagCell = createPatientCell("Diagnóstico: ", diagText);
            diagCell.setColspan(2);
            table.addCell(diagCell);
        }

        document.add(table);

        Paragraph reminder = new Paragraph("Presente esta receta en su próxima consulta.", PrescriptionPdfPalette.FONT_REMINDER);
        reminder.setAlignment(Element.ALIGN_CENTER);
        reminder.setSpacingBefore(6f);
        reminder.setSpacingAfter(8f);
        document.add(reminder);
    }

    private PdfPCell createPatientCell(String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label, PrescriptionPdfPalette.FONT_FIELD_LABEL));
        phrase.add(new Chunk(value != null ? value : "", PrescriptionPdfPalette.FONT_FIELD_VALUE));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(PrescriptionPdfPalette.BLUE_200);
        cell.setBorderWidthBottom(1f);
        cell.setPaddingBottom(3f);
        cell.setPaddingTop(3f);
        return cell;
    }

    private void addBody(Document document, PrescriptionResponse prescription) throws DocumentException {
        Paragraph rp = new Paragraph("Rp.", PrescriptionPdfPalette.FONT_RP);
        rp.setSpacingAfter(6f);
        document.add(rp);

        int number = 1;
        for (PrescriptionItemResponse item : prescription.items()) {
            Paragraph title = new Paragraph(number + ". " + item.medicationName() + " " + item.concentration()
                    + " (" + item.presentation() + ")", PrescriptionPdfPalette.FONT_MEDICATION_TITLE);
            title.setSpacingBefore(2f);
            document.add(title);

            Paragraph details = new Paragraph("Dosis: " + item.dose() + " · Frecuencia: " + item.frequency()
                    + " · Duración: " + item.duration(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
            details.setIndentationLeft(12f);
            document.add(details);

            if (isPresent(item.quantity())) {
                Paragraph quantity = new Paragraph("Cantidad: " + item.quantity(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
                quantity.setIndentationLeft(12f);
                document.add(quantity);
            }

            if (isPresent(item.instructions())) {
                Paragraph instructions = new Paragraph("Indicaciones: " + item.instructions(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
                instructions.setIndentationLeft(12f);
                document.add(instructions);
            }

            number++;
        }

        if (isPresent(prescription.generalInstructions())) {
            Paragraph instructionsTitle = new Paragraph("Indicaciones generales", PrescriptionPdfPalette.FONT_INSTRUCTIONS_TITLE);
            instructionsTitle.setSpacingBefore(10f);
            instructionsTitle.setSpacingAfter(4f);
            document.add(instructionsTitle);

            document.add(new Paragraph(prescription.generalInstructions(), PrescriptionPdfPalette.FONT_INSTRUCTIONS_BODY));
        }
    }

    private void addClosingBlock(PdfWriter writer, Document document, ProfessionalSummary professional) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 1f});
        table.setTotalWidth(document.right() - document.left());
        table.setLockedWidth(true);
        table.addCell(sealCell());
        table.addCell(signatureCell(professional));
        if (hasServices()) {
            table.addCell(servicesCell());
        }

        float height = table.getTotalHeight();
        float available = writer.getVerticalPosition(true) - document.bottom();
        if (available < height + 16f) {
            document.newPage();
            writer.setPageEmpty(false);
        }
        table.writeSelectedRows(0, -1, document.left(), document.bottom() + height, writer.getDirectContent());
    }

    private PdfPCell sealCell() {
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(90f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingBottom(10f);

        Paragraph sealLabel = new Paragraph("Sello", PrescriptionPdfPalette.FONT_SEAL_LABEL);
        sealLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(sealLabel);
        return cell;
    }

    private PdfPCell signatureCell(ProfessionalSummary professional) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph space = new Paragraph(" ");
        space.setSpacingBefore(20f);
        cell.addElement(space);

        Paragraph line = new Paragraph("________________________________________", PrescriptionPdfPalette.FONT_SIGNATURE_LABEL);
        line.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(line);

        Paragraph doctorName = new Paragraph("Od. " + professional.firstName() + " " + professional.lastName(),
                PrescriptionPdfPalette.FONT_SIGNATURE_NAME);
        doctorName.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(doctorName);

        if (isPresent(professional.licenseNumber())) {
            Paragraph license = new Paragraph("Reg. " + professional.licenseNumber(), PrescriptionPdfPalette.FONT_SIGNATURE_LICENSE);
            license.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(license);
        }

        Paragraph signLabel = new Paragraph("Firma", PrescriptionPdfPalette.FONT_SIGNATURE_LABEL);
        signLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(signLabel);
        return cell;
    }

    private PdfPCell servicesCell() {
        PdfPCell cell = new PdfPCell();
        cell.setColspan(2);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(14f);

        Paragraph title = new Paragraph("Los mejores tratamientos para ti y tu familia.", PrescriptionPdfPalette.FONT_SERVICES_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4f);
        cell.addElement(title);

        Paragraph list = new Paragraph(String.join(" · ", clinicProperties.services()), PrescriptionPdfPalette.FONT_SERVICES_LIST);
        list.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(list);
        return cell;
    }

    private boolean hasServices() {
        return clinicProperties.services() != null && !clinicProperties.services().isEmpty();
    }

    private String spacedText(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (i < text.length() - 1 && text.charAt(i) != ' ') {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
