package com.bellidental.prescriptions.application;

import com.bellidental.config.ClinicProperties;
import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionPdfPageDecorator extends PdfPageEventHelper {

    private static final float FOOTER_HEIGHT = 40f;
    private static final float WATERMARK_SIZE = 200f;
    private static final float WATERMARK_OPACITY = 0.06f;

    private final ClinicProperties clinicProperties;
    private final PrescriptionPdfImages images;

    public PrescriptionPdfPageDecorator(ClinicProperties clinicProperties, PrescriptionPdfImages images) {
        this.clinicProperties = clinicProperties;
        this.images = images;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        drawWatermark(writer, document);
        drawCutLine(writer, document);
        drawContactBar(writer, document);
    }

    private void drawWatermark(PdfWriter writer, Document document) {
        try {
            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            PdfGState gstate = new PdfGState();
            gstate.setFillOpacity(WATERMARK_OPACITY);
            under.setGState(gstate);

            float width = document.getPageSize().getWidth();
            float halfWidth = width / 2f;
            float xLeft = (halfWidth - WATERMARK_SIZE) / 2f;
            float xRight = halfWidth + (halfWidth - WATERMARK_SIZE) / 2f;
            float y = document.bottom() + (document.top() - document.bottom() - WATERMARK_SIZE) / 2f;

            Image watermarkLeft = Image.getInstance(images.logo());
            watermarkLeft.scaleAbsolute(WATERMARK_SIZE, WATERMARK_SIZE);
            watermarkLeft.setAbsolutePosition(xLeft, y);
            under.addImage(watermarkLeft);

            Image watermarkRight = Image.getInstance(images.logo());
            watermarkRight.scaleAbsolute(WATERMARK_SIZE, WATERMARK_SIZE);
            watermarkRight.setAbsolutePosition(xRight, y);
            under.addImage(watermarkRight);

            under.restoreState();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dibujar la marca de agua en el PDF", e);
        }
    }

    private void drawCutLine(PdfWriter writer, Document document) {
        try {
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();
            cb.setColorStroke(PrescriptionPdfPalette.BLUE_200);
            cb.setLineWidth(1f);
            cb.setLineDash(4f, 4f, 0f);
            float x = document.getPageSize().getWidth() / 2f;
            cb.moveTo(x, document.top());
            cb.lineTo(x, FOOTER_HEIGHT);
            cb.stroke();
            cb.restoreState();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dibujar la línea de corte en el PDF", e);
        }
    }

    private void drawContactBar(PdfWriter writer, Document document) {
        try {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle pageSize = document.getPageSize();
            float width = pageSize.getWidth();

            cb.saveState();
            cb.setColorFill(PrescriptionPdfPalette.BLUE_900);
            cb.rectangle(0, 0, width, FOOTER_HEIGHT);
            cb.fill();

            cb.setColorStroke(PrescriptionPdfPalette.GOLD_400);
            cb.setLineWidth(1.5f);
            cb.moveTo(0, FOOTER_HEIGHT);
            cb.lineTo(width, FOOTER_HEIGHT);
            cb.stroke();
            cb.restoreState();

            List<ChannelInfo> activeChannels = collectActiveChannels();
            if (activeChannels.isEmpty()) {
                return;
            }

            float halfWidth = width / 2f;
            float margin = 10f;
            float tableWidth = halfWidth - (margin * 2);

            PdfPTable leftTable = createChannelsTable(activeChannels, tableWidth);
            float tableHeight = leftTable.getTotalHeight();
            float y = (FOOTER_HEIGHT + tableHeight) / 2f;
            leftTable.writeSelectedRows(0, -1, margin, y, cb);

            PdfPTable rightTable = createChannelsTable(activeChannels, tableWidth);
            rightTable.writeSelectedRows(0, -1, halfWidth + margin, y, cb);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dibujar la franja de contacto en el PDF", e);
        }
    }

    private PdfPTable createChannelsTable(List<ChannelInfo> channels, float tableWidth) throws BadElementException, IOException {
        float[] relativeWidths = new float[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            relativeWidths[i] = channels.get(i).text.length() + 4f;
        }
        PdfPTable table = new PdfPTable(relativeWidths);
        table.setTotalWidth(tableWidth);
        table.setLockedWidth(true);
        for (ChannelInfo channel : channels) {
            table.addCell(createChannelCell(channel));
        }
        return table;
    }

    private PdfPCell createChannelCell(ChannelInfo channel) throws BadElementException, IOException {
        Image icon = Image.getInstance(channel.iconBytes);
        icon.scaleAbsolute(11f, 11f);

        Phrase phrase = new Phrase();
        phrase.add(new Chunk(icon, 0, -2f));
        phrase.add(new Chunk(" " + channel.text, PrescriptionPdfPalette.FONT_FOOTER_CHANNEL));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setNoWrap(true);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(1f);
        return cell;
    }

    private List<ChannelInfo> collectActiveChannels() {
        List<ChannelInfo> channels = new ArrayList<>();
        ClinicProperties.Social social = clinicProperties.social();

        if (social != null && isNotBlank(social.facebook())) {
            channels.add(new ChannelInfo(images.facebook(), social.facebook().trim()));
        }
        if (social != null && isNotBlank(social.instagram())) {
            channels.add(new ChannelInfo(images.instagram(), social.instagram().trim()));
        }
        if (isNotBlank(clinicProperties.receptionWhatsapp())) {
            channels.add(new ChannelInfo(images.whatsapp(), formatWhatsApp(clinicProperties.receptionWhatsapp())));
        }
        if (social != null && isNotBlank(social.email())) {
            channels.add(new ChannelInfo(images.email(), social.email().trim()));
        }
        if (social != null && isNotBlank(social.x())) {
            channels.add(new ChannelInfo(images.x(), social.x().trim()));
        }
        return channels;
    }

    private String formatWhatsApp(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }
        String clean = rawPhone.replaceAll("\\D", "");
        if (clean.length() == 12 && clean.startsWith("593")) {
            return "+" + clean.substring(0, 3) + " " + clean.substring(3, 5) + " "
                    + clean.substring(5, 8) + " " + clean.substring(8);
        }
        if (clean.length() == 10 && clean.startsWith("0")) {
            return "+593 " + clean.substring(1, 3) + " " + clean.substring(3, 6) + " " + clean.substring(6);
        }
        return rawPhone;
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }

    private record ChannelInfo(byte[] iconBytes, String text) {}
}
