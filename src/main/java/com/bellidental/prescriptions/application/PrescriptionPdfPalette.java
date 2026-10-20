package com.bellidental.prescriptions.application;

import com.lowagie.text.Font;

import java.awt.Color;

public final class PrescriptionPdfPalette {

    private PrescriptionPdfPalette() {}

    // Colors
    public static final Color BLUE_900 = new Color(0x0F, 0x2A, 0x44);
    public static final Color BLUE_800 = new Color(0x1C, 0x3C, 0x5C);
    public static final Color BLUE_600 = new Color(0x2C, 0x5F, 0x8A);
    public static final Color BLUE_400 = new Color(0x6E, 0x93, 0xB8);
    public static final Color BLUE_200 = new Color(0xD3, 0xE4, 0xF0);
    public static final Color BLUE_50  = new Color(0xEF, 0xF6, 0xFB);
    public static final Color GOLD_400 = new Color(0xE3, 0xA8, 0x57);
    public static final Color CORAL_700 = new Color(0xB0, 0x4A, 0x31);
    public static final Color SLATE_800 = new Color(0x2E, 0x3A, 0x46);
    public static final Color SLATE_600 = new Color(0x4F, 0x5D, 0x6B);
    public static final Color SLATE_500 = new Color(0x6B, 0x76, 0x84);
    public static final Color WHITE     = new Color(0xFF, 0xFF, 0xFF);

    // Fonts - Header
    public static final Font FONT_CLINIC_NAME = new Font(Font.HELVETICA, 18, Font.BOLD, BLUE_900);
    public static final Font FONT_TAGLINE = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_600);
    public static final Font FONT_DOCTOR_HEADER = new Font(Font.HELVETICA, 11, Font.BOLD, BLUE_800);
    public static final Font FONT_DOCTOR_LICENSE = new Font(Font.HELVETICA, 9, Font.NORMAL, SLATE_600);
    public static final Font FONT_CLINIC_CONTACT = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_600);

    // Fonts - Header Right
    public static final Font FONT_DATE_HEADER = new Font(Font.HELVETICA, 7, Font.BOLD, WHITE);
    public static final Font FONT_DATE_VALUE = new Font(Font.HELVETICA, 9, Font.BOLD, SLATE_800);
    public static final Font FONT_RECETA_TITLE = new Font(Font.HELVETICA, 14, Font.BOLD, BLUE_900);
    public static final Font FONT_CONFIDENTIAL = new Font(Font.HELVETICA, 8, Font.BOLD, CORAL_700);

    // Fonts - Patient
    public static final Font FONT_FIELD_LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, SLATE_600);
    public static final Font FONT_FIELD_VALUE = new Font(Font.HELVETICA, 10, Font.NORMAL, SLATE_800);
    public static final Font FONT_REMINDER = new Font(Font.HELVETICA, 9, Font.ITALIC, SLATE_500);

    // Fonts - Body
    public static final Font FONT_RP = new Font(Font.HELVETICA, 16, Font.BOLD, BLUE_800);
    public static final Font FONT_MEDICATION_TITLE = new Font(Font.HELVETICA, 11, Font.BOLD, BLUE_900);
    public static final Font FONT_MEDICATION_BODY = new Font(Font.HELVETICA, 10, Font.NORMAL, SLATE_800);
    public static final Font FONT_INSTRUCTIONS_TITLE = new Font(Font.HELVETICA, 9, Font.BOLD, BLUE_800);
    public static final Font FONT_INSTRUCTIONS_BODY = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_800);

    // Fonts - Seal & Signature
    public static final Font FONT_SEAL_LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_500);
    public static final Font FONT_SIGNATURE_NAME = new Font(Font.HELVETICA, 10, Font.BOLD, BLUE_900);
    public static final Font FONT_SIGNATURE_LICENSE = new Font(Font.HELVETICA, 9, Font.NORMAL, SLATE_600);
    public static final Font FONT_SIGNATURE_LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE_500);

    // Fonts - Services
    public static final Font FONT_SERVICES_TITLE = new Font(Font.HELVETICA, 10, Font.ITALIC, BLUE_800);
    public static final Font FONT_SERVICES_LIST = new Font(Font.HELVETICA, 9, Font.NORMAL, SLATE_600);

    // Fonts - Footer Contact
    public static final Font FONT_FOOTER_CHANNEL = new Font(Font.HELVETICA, 8, Font.NORMAL, WHITE);
}
