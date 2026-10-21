package com.bellidental.billing.domain;

/** Número de comprobante interno: C-000001. */
public final class ReceiptNumber {

    private ReceiptNumber() {}

    public static String format(long sequenceValue) {
        return String.format("C-%06d", sequenceValue);
    }
}
