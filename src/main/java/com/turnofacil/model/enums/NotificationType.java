package com.turnofacil.model.enums;

public enum NotificationType {
    NEW_BOOKING("Nueva reserva", "bi-calendar-plus", "#5A9367"),
    CANCELLED("Cancelacion", "bi-calendar-x", "#C45C4A"),
    REMINDER_SENT("Recordatorio enviado", "bi-bell", "#5B8DB8"),
    NO_SHOW("Cliente no se presento", "bi-person-x", "#C9A227"),
    SYSTEM("Sistema", "bi-info-circle", "#6B7280");

    private final String displayName;
    private final String icon;
    private final String color;

    NotificationType(String displayName, String icon, String color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }
}
