package com.bellidental.prescriptions.application;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PrescriptionPdfImages {

    private final byte[] logo;
    private final byte[] facebook;
    private final byte[] instagram;
    private final byte[] whatsapp;
    private final byte[] x;
    private final byte[] email;

    public PrescriptionPdfImages() {
        this.logo = load("pdf/bellidental-logo.png");
        this.facebook = load("pdf/facebook.png");
        this.instagram = load("pdf/instagram.png");
        this.whatsapp = load("pdf/whatsapp.png");
        this.x = load("pdf/x.png");
        this.email = load("pdf/email.png");
    }

    public byte[] logo() {
        return logo;
    }

    public byte[] facebook() {
        return facebook;
    }

    public byte[] instagram() {
        return instagram;
    }

    public byte[] whatsapp() {
        return whatsapp;
    }

    public byte[] x() {
        return x;
    }

    public byte[] email() {
        return email;
    }

    private byte[] load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el recurso PDF: " + path, e);
        }
    }
}
