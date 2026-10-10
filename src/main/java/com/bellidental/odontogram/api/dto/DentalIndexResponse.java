package com.bellidental.odontogram.api.dto;

public record DentalIndexResponse(
        int cpoC, int cpoP, int cpoO, int cpoTotal,
        int ceoC, int ceoE, int ceoO, int ceoTotal
) {
}
