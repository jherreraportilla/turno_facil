package com.turnofacil.model.enums;

public enum InvoiceStatus {
    DRAFT("Borrador", "bg-secondary"),
    ISSUED("Emitida", "bg-primary"),
    PAID("Pagada", "bg-success"),
    CANCELLED("Anulada", "bg-danger"),
    RECTIFIED("Rectificada", "bg-warning");

    private final String displayName;
    private final String badgeClass;

    InvoiceStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /**
     * Valida si una transición de estado es permitida
     */
    public boolean canTransitionTo(InvoiceStatus newStatus) {
        return switch (this) {
            case DRAFT -> newStatus == ISSUED;
            case ISSUED -> newStatus == PAID || newStatus == CANCELLED;
            case PAID -> newStatus == RECTIFIED;
            case CANCELLED, RECTIFIED -> false; // Estados finales
        };
    }
}
