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
import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
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
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrescriptionPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private static final float PAGE_MARGIN = 28f;
    private static final float GUTTER_WIDTH = 28f;
    private static final float BOTTOM_MARGIN = 50f;

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
        Rectangle pageSize = PageSize.A4.rotate();
        pageSize.setRotation(0);
        Document document = new Document(pageSize, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, BOTTOM_MARGIN);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PrescriptionPdfPageDecorator(clinicProperties, images));
            document.open();

            LocalDate issueDate = prescription.prescribedAt().atZoneSameInstant(clinicClock.zone()).toLocalDate();

            addMainTable(document, prescription, patient, professional, issueDate);
            addClosingBlock(writer, document, professional);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la receta", e);
        }
    }

    private void addMainTable(Document document, PrescriptionResponse prescription, PatientSummary patient,
                              ProfessionalSummary professional, LocalDate issueDate) throws DocumentException, IOException {
        float totalWidth = document.right() - document.left();
        float half = halfWidth(document);

        PdfPTable mainTable = new PdfPTable(new float[]{half, GUTTER_WIDTH, half});
        mainTable.setTotalWidth(totalWidth);
        mainTable.setLockedWidth(true);
        mainTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // Fila 0: Cabeceras (con título según la mitad)
        mainTable.addCell(wrapInCell(createHalfHeader(professional, issueDate, "RECETA", half)));
        mainTable.addCell(createGutterCell());
        mainTable.addCell(wrapInCell(createHalfHeader(professional, issueDate, "INDICACIONES", half)));

        // Fila 1: Línea dorada
        mainTable.addCell(wrapInCell(createAccentLine(half)));
        mainTable.addCell(createGutterCell());
        mainTable.addCell(wrapInCell(createAccentLine(half)));

        // Fila 2: Bloque del paciente
        mainTable.addCell(wrapInCell(createPatientBlock(patient, issueDate, prescription, half)));
        mainTable.addCell(createGutterCell());
        mainTable.addCell(wrapInCell(createPatientBlock(patient, issueDate, prescription, half)));

        // Cabeceras fijas que se repiten en cada página
        mainTable.setHeaderRows(3);

        // Fila 3: Encabezados de sección ("Rp." e "Indicaciones")
        PdfPCell leftRp = new PdfPCell();
        leftRp.setBorder(Rectangle.NO_BORDER);
        leftRp.setPaddingTop(5f);
        leftRp.setPaddingBottom(4f);
        Paragraph rp = new Paragraph("Rp.", PrescriptionPdfPalette.FONT_RP);
        leftRp.addElement(rp);
        mainTable.addCell(leftRp);

        mainTable.addCell(createGutterCell());

        PdfPCell rightInd = new PdfPCell();
        rightInd.setBorder(Rectangle.NO_BORDER);
        rightInd.setPaddingTop(5f);
        rightInd.setPaddingBottom(4f);
        Paragraph indicaciones = new Paragraph("Indicaciones", PrescriptionPdfPalette.FONT_INDICACIONES_SECTION);
        rightInd.addElement(indicaciones);
        mainTable.addCell(rightInd);

        // Filas por cada ítem: alineadas en ambas mitades, no se parten entre páginas
        int number = 1;
        for (PrescriptionItemResponse item : prescription.items()) {
            // Izquierda: N. medicamento concentración (presentación) + Cant.: quantity si hay
            PdfPCell itemLeft = new PdfPCell();
            itemLeft.setBorder(Rectangle.NO_BORDER);
            itemLeft.setPaddingTop(2f);
            itemLeft.setPaddingBottom(3f);

            Paragraph titleLeft = new Paragraph(number + ". " + item.medicationName() + " " + item.concentration()
                    + " (" + item.presentation() + ")", PrescriptionPdfPalette.FONT_MEDICATION_TITLE);
            titleLeft.setSpacingBefore(1f);
            itemLeft.addElement(titleLeft);

            if (isPresent(item.quantity())) {
                Paragraph quantity = new Paragraph("Cant.: " + item.quantity(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
                quantity.setIndentationLeft(12f);
                itemLeft.addElement(quantity);
            }
            mainTable.addCell(itemLeft);

            mainTable.addCell(createGutterCell());

            // Derecha: N. medicamento concentración + Dosis · Frecuencia · Duración + Indicaciones si hay
            PdfPCell itemRight = new PdfPCell();
            itemRight.setBorder(Rectangle.NO_BORDER);
            itemRight.setPaddingTop(2f);
            itemRight.setPaddingBottom(3f);

            Paragraph titleRight = new Paragraph(number + ". " + item.medicationName() + " " + item.concentration(),
                    PrescriptionPdfPalette.FONT_MEDICATION_TITLE);
            titleRight.setSpacingBefore(1f);
            itemRight.addElement(titleRight);

            Paragraph details = new Paragraph("Dosis: " + item.dose() + " · Frecuencia: " + item.frequency()
                    + " · Duración: " + item.duration(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
            details.setIndentationLeft(12f);
            itemRight.addElement(details);

            if (isPresent(item.instructions())) {
                Paragraph instructions = new Paragraph("Indicaciones: " + item.instructions(), PrescriptionPdfPalette.FONT_MEDICATION_BODY);
                instructions.setIndentationLeft(12f);
                itemRight.addElement(instructions);
            }
            mainTable.addCell(itemRight);

            number++;
        }

        // Fila posterior a los ítems: Indicaciones generales y recordatorio (solo a la derecha)
        PdfPCell afterItemsLeft = new PdfPCell();
        afterItemsLeft.setBorder(Rectangle.NO_BORDER);
        mainTable.addCell(afterItemsLeft);

        mainTable.addCell(createGutterCell());

        PdfPCell afterItemsRight = new PdfPCell();
        afterItemsRight.setBorder(Rectangle.NO_BORDER);
        afterItemsRight.setPaddingTop(4f);

        if (isPresent(prescription.generalInstructions())) {
            Paragraph instructionsTitle = new Paragraph("Indicaciones generales", PrescriptionPdfPalette.FONT_INSTRUCTIONS_TITLE);
            instructionsTitle.setSpacingBefore(4f);
            instructionsTitle.setSpacingAfter(2f);
            afterItemsRight.addElement(instructionsTitle);

            Paragraph instructionsBody = new Paragraph(prescription.generalInstructions(), PrescriptionPdfPalette.FONT_INSTRUCTIONS_BODY);
            afterItemsRight.addElement(instructionsBody);
        }

        Paragraph reminder = new Paragraph("Presente esta receta en su próxima consulta.", PrescriptionPdfPalette.FONT_REMINDER);
        reminder.setAlignment(Element.ALIGN_CENTER);
        reminder.setSpacingBefore(6f);
        reminder.setSpacingAfter(4f);
        afterItemsRight.addElement(reminder);

        mainTable.addCell(afterItemsRight);

        document.add(mainTable);
    }

    private PdfPTable createHalfHeader(ProfessionalSummary professional, LocalDate issueDate, String title, float halfWidth)
            throws BadElementException, IOException {
        PdfPTable headerTable = new PdfPTable(new float[]{52f, 227f, 100f});
        headerTable.setTotalWidth(halfWidth);
        headerTable.setLockedWidth(true);
        headerTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // 1. Logo (≤ 48 pt)
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        Image logo = Image.getInstance(images.logo());
        logo.scaleToFit(48f, 48f);
        logoCell.addElement(logo);
        headerTable.addCell(logoCell);

        // 2. Clínica y profesional
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
            tagline.setSpacingAfter(2f);
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

        String phone = clinicProperties.phone() != null ? clinicProperties.phone().replace(' ', '\u00A0') : "";
        String contact = clinicProperties.address() + "\nTel.\u00A0" + phone;
        Paragraph clinicContact = new Paragraph(contact, PrescriptionPdfPalette.FONT_CLINIC_CONTACT);
        clinicContact.setAlignment(Element.ALIGN_CENTER);
        clinicContact.setSpacingBefore(1f);
        clinicCell.addElement(clinicContact);

        headerTable.addCell(clinicCell);

        // 3. Columna derecha: Recuadro D/M/A, Título ("RECETA" / "INDICACIONES") y CONFIDENCIAL
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPTable dateBox = createDateBox(issueDate);
        dateBox.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateBox);

        Font titleFont = "RECETA".equals(title)
                ? PrescriptionPdfPalette.FONT_RECETA_TITLE
                : PrescriptionPdfPalette.FONT_INDICACIONES_TITLE;
        Paragraph titleParagraph = new Paragraph(title, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_RIGHT);
        titleParagraph.setSpacingBefore(3f);
        rightCell.addElement(titleParagraph);

        Paragraph confidential = new Paragraph("CONFIDENCIAL", PrescriptionPdfPalette.FONT_CONFIDENTIAL);
        confidential.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(confidential);

        headerTable.addCell(rightCell);
        return headerTable;
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

    private PdfPTable createAccentLine(float width) {
        PdfPTable line = new PdfPTable(1);
        line.setTotalWidth(width);
        line.setLockedWidth(true);
        line.setSpacingBefore(4f);
        line.setSpacingAfter(4f);

        PdfPCell lineCell = new PdfPCell();
        lineCell.setFixedHeight(2f);
        lineCell.setBackgroundColor(PrescriptionPdfPalette.GOLD_400);
        lineCell.setBorder(Rectangle.NO_BORDER);
        line.addCell(lineCell);
        return line;
    }

    private PdfPTable createPatientBlock(PatientSummary patient, LocalDate issueDate, PrescriptionResponse prescription, float width) {
        PdfPTable table = new PdfPTable(2);
        table.setTotalWidth(width);
        table.setLockedWidth(true);

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

        return table;
    }

    private PdfPCell createPatientCell(String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label, PrescriptionPdfPalette.FONT_FIELD_LABEL));
        phrase.add(new Chunk(value != null ? value : "", PrescriptionPdfPalette.FONT_FIELD_VALUE));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(PrescriptionPdfPalette.BLUE_200);
        cell.setBorderWidthBottom(1f);
        cell.setPaddingBottom(2f);
        cell.setPaddingTop(2f);
        return cell;
    }

    private void addClosingBlock(PdfWriter writer, Document document, ProfessionalSummary professional) throws DocumentException {
        float totalWidth = document.right() - document.left();
        float half = halfWidth(document);

        PdfPTable closingTable = createClosingTable(professional, totalWidth, half);

        float height = closingTable.getTotalHeight();
        float available = writer.getVerticalPosition(true) - document.bottom();
        if (available < height + 16f) {
            document.newPage();
            writer.setPageEmpty(false);
        }
        closingTable.writeSelectedRows(0, -1, document.left(), document.bottom() + height, writer.getDirectContent());
    }

    private PdfPTable createClosingTable(ProfessionalSummary professional, float totalWidth, float half) {
        PdfPTable closingTable = new PdfPTable(new float[]{half, GUTTER_WIDTH, half});
        closingTable.setTotalWidth(totalWidth);
        closingTable.setLockedWidth(true);
        closingTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // Fila 1: Sello y firma en ambas mitades (exactamente a la misma altura)
        closingTable.addCell(wrapInCell(createHalfClosing(professional, half)));
        closingTable.addCell(createGutterCell());
        closingTable.addCell(wrapInCell(createHalfClosing(professional, half)));

        // Fila 2: [vacía | gutter | servicios] (solo bajo la mitad derecha)
        if (hasServices()) {
            PdfPCell emptyLeft = new PdfPCell();
            emptyLeft.setBorder(Rectangle.NO_BORDER);
            closingTable.addCell(emptyLeft);

            closingTable.addCell(createGutterCell());

            closingTable.addCell(servicesCell());
        }

        return closingTable;
    }

    private PdfPTable createHalfClosing(ProfessionalSummary professional, float width) {
        PdfPTable table = new PdfPTable(new float[]{1f, 1f});
        table.setTotalWidth(width);
        table.setLockedWidth(true);
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        table.addCell(sealCell());
        table.addCell(signatureCell(professional));
        return table;
    }

    private PdfPCell wrapInCell(PdfPTable table) {
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell createGutterCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell sealCell() {
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(75f);
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
        space.setSpacingBefore(15f);
        cell.addElement(space);

        Paragraph line = new Paragraph("______________________________", PrescriptionPdfPalette.FONT_SIGNATURE_LABEL);
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
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(6f);

        Paragraph title = new Paragraph("Los mejores tratamientos para ti y tu familia.", PrescriptionPdfPalette.FONT_SERVICES_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(2f);
        cell.addElement(title);

        Paragraph list = new Paragraph(String.join(" · ", clinicProperties.services()), PrescriptionPdfPalette.FONT_SERVICES_LIST);
        list.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(list);
        return cell;
    }

    private float halfWidth(Document document) {
        float totalWidth = document.right() - document.left();
        return (totalWidth - GUTTER_WIDTH) / 2f;
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
