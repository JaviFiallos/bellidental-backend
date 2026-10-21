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
    private static final float WATERMARK_SIZE = 300f;
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
        drawContactBar(writer, document);
    }

    private void drawWatermark(PdfWriter writer, Document document) {
        try {
            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            PdfGState gstate = new PdfGState();
            gstate.setFillOpacity(WATERMARK_OPACITY);
            under.setGState(gstate);

            float x = document.left() + (document.right() - document.left() - WATERMARK_SIZE) / 2f;
            float y = document.bottom() + (document.top() - document.bottom() - WATERMARK_SIZE) / 2f;

            Image watermark = Image.getInstance(images.logo());
            watermark.scaleAbsolute(WATERMARK_SIZE, WATERMARK_SIZE);
            watermark.setAbsolutePosition(x, y);
            under.addImage(watermark);
            under.restoreState();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dibujar la marca de agua en el PDF", e);
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

            PdfPTable table = new PdfPTable(activeChannels.size());
            float margin = 20f;
            table.setTotalWidth(width - (margin * 2));
            table.setLockedWidth(true);

            for (ChannelInfo channel : activeChannels) {
                table.addCell(createChannelCell(channel));
            }

            float tableHeight = table.getTotalHeight();
            float y = (FOOTER_HEIGHT + tableHeight) / 2f;
            table.writeSelectedRows(0, -1, margin, y, cb);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo dibujar la franja de contacto en el PDF", e);
        }
    }

    private PdfPCell createChannelCell(ChannelInfo channel) throws BadElementException, IOException {
        Image icon = Image.getInstance(channel.iconBytes);
        icon.scaleAbsolute(14f, 14f);

        Phrase phrase = new Phrase();
        phrase.add(new Chunk(icon, 0, -4f));
        phrase.add(new Chunk(" " + channel.text, PrescriptionPdfPalette.FONT_FOOTER_CHANNEL));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        cell.setPaddingTop(2f);
        cell.setPaddingBottom(2f);
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
