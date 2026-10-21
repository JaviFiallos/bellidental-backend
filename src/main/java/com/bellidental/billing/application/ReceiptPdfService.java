package com.bellidental.billing.application;

import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.config.ClinicProperties;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptPdfService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color SLATE_800 = new Color(0x2E, 0x3A, 0x46);
    private static final Color SLATE_600 = new Color(0x4F, 0x5D, 0x6B);
    private static final Color CORAL_700 = new Color(0xB0, 0x4A, 0x31);

    private static final Font FONT_CLINIC_NAME = new Font(Font.HELVETICA, 16, Font.BOLD, SLATE_800);
    private static final Font FONT_CLINIC_CONTACT = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_600);
    private static final Font FONT_TITLE = new Font(Font.HELVETICA, 12, Font.BOLD, SLATE_800);
    private static final Font FONT_NOTICE = new Font(Font.HELVETICA, 8, Font.ITALIC, SLATE_600);
    private static final Font FONT_CONFIDENTIAL = new Font(Font.HELVETICA, 8, Font.BOLD, CORAL_700);
    private static final Font FONT_LABEL = new Font(Font.HELVETICA, 9, Font.BOLD, SLATE_600);
    private static final Font FONT_VALUE = new Font(Font.HELVETICA, 10, Font.NORMAL, SLATE_800);
    private static final Font FONT_VOIDED = new Font(Font.HELVETICA, 10, Font.BOLD, CORAL_700);
    private static final Font FONT_SIGNATURE = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_600);

    private final PaymentService paymentService;
    private final AccountService accountService;
    private final PatientDirectory patientDirectory;
    private final ClinicProperties clinicProperties;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public byte[] generateReceipt(UUID paymentId) {
        PaymentResponse payment = paymentService.findById(paymentId);
        PatientSummary patient = patientDirectory.findById(payment.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
        AccountResponse account = accountService.findAccount(payment.patientId());
        return render(payment, patient, account.balance());
    }

    private byte[] render(PaymentResponse payment, PatientSummary patient, BigDecimal currentBalance) {
        Document document = new Document(PageSize.A5, 36f, 36f, 36f, 36f);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, output);
            document.open();

            addClinicHeader(document);
            addTitle(document, payment);
            addFields(document, payment, patient, currentBalance);
            if (payment.voided()) {
                addVoidedNotice(document, payment);
            }
            addSignature(document);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el comprobante PDF", e);
        }
    }

    private void addClinicHeader(Document document) throws DocumentException {
        Paragraph name = new Paragraph(clinicProperties.name(), FONT_CLINIC_NAME);
        name.setAlignment(Element.ALIGN_CENTER);
        document.add(name);

        Paragraph contact = new Paragraph(clinicProperties.address() + " · Tel. " + clinicProperties.phone(), FONT_CLINIC_CONTACT);
        contact.setAlignment(Element.ALIGN_CENTER);
        contact.setSpacingAfter(12f);
        document.add(contact);
    }

    private void addTitle(Document document, PaymentResponse payment) throws DocumentException {
        Paragraph title = new Paragraph("Comprobante de pago " + payment.receiptNumber(), FONT_TITLE);
        title.setAlignment(Element.ALIGN_LEFT);
        document.add(title);

        Paragraph notice = new Paragraph("Comprobante interno — no válido como factura", FONT_NOTICE);
        document.add(notice);

        Paragraph confidential = new Paragraph("CONFIDENCIAL", FONT_CONFIDENTIAL);
        confidential.setSpacingAfter(10f);
        document.add(confidential);
    }

    private void addFields(Document document, PaymentResponse payment, PatientSummary patient, BigDecimal currentBalance)
            throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 1.6f});
        table.setWidthPercentage(100f);

        String documentLabel = patient.documentType() == DocumentType.CI ? "Cédula: " : "Documento: ";
        table.addCell(fieldCell("Número: ", payment.receiptNumber()));
        table.addCell(fieldCell("Fecha de pago: ", formatPaidAt(payment)));
        table.addCell(fieldCell("Paciente: ", patient.firstName() + " " + patient.lastName()));
        table.addCell(fieldCell(documentLabel, patient.documentNumber()));
        table.addCell(fieldCell("Método: ", payment.method().name()));
        table.addCell(fieldCell("Monto: ", "$ " + payment.amount().toPlainString()));
        table.addCell(fieldCell("Saldo pendiente actual: ", "$ " + currentBalance.toPlainString()));

        document.add(table);
    }

    private void addVoidedNotice(Document document, PaymentResponse payment) throws DocumentException {
        Paragraph voided = new Paragraph("ANULADO — " + payment.voidReason(), FONT_VOIDED);
        voided.setSpacingBefore(10f);
        document.add(voided);
    }

    private void addSignature(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(60f);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingBefore(40f);

        PdfPCell signatureCell = new PdfPCell(new Phrase("Firma del paciente", FONT_SIGNATURE));
        signatureCell.setBorder(Rectangle.TOP);
        signatureCell.setBorderColorTop(SLATE_600);
        signatureCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        signatureCell.setPaddingTop(4f);
        table.addCell(signatureCell);

        document.add(table);
    }

    private PdfPCell fieldCell(String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label, FONT_LABEL));
        phrase.add(new Chunk(value != null ? value : "", FONT_VALUE));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(4f);
        return cell;
    }

    private String formatPaidAt(PaymentResponse payment) {
        return payment.paidAt().atZoneSameInstant(clinicClock.zone()).format(DATE_TIME_FORMAT);
    }
}
