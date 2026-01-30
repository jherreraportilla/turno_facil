package com.turnofacil.model.enums;

public enum InvoiceAuditAction {
    CREATED("Factura creada"),
    UPDATED("Factura modificada"),
    ISSUED("Factura emitida"),
    PAID("Factura pagada"),
    CANCELLED("Factura anulada"),
    RECTIFIED("Factura rectificada"),
    VIEWED("Factura visualizada"),
    DOWNLOADED("Factura descargada"),
    SENT_EMAIL("Factura enviada por email");

    private final String description;

    InvoiceAuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
