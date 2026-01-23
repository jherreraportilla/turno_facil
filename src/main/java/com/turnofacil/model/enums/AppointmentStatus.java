package com.turnofacil.model.enums;

import com.turnofacil.dto.AppointmentStatusDto;
import com.turnofacil.util.Constants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppointmentStatus {

    PENDING("pending",
            "Pendiente",
            "Pending",
            "bg-warning",
            Constants.TEXT_BLACK),

    CONFIRMED("confirmed",
            "Confirmado",
            "Confirmed",
            "bg-success",
            Constants.TEXT_WHITE),

    CANCELLED("cancelled",
            "Cancelado",
            "Cancelled",
            "bg-danger",
            Constants.TEXT_WHITE),

    COMPLETED("completed",
            "Completado",
            "Completed",
            "bg-secondary",
            Constants.TEXT_WHITE),

    NO_SHOW("no_show",
            "No se presentó",
            "No show",
            "bg-dark",
            Constants.TEXT_WHITE);

    private final String code;
    private final String es;
    private final String en;
    private final String badgeClass;
    private final String textClass;

    /**
     * Devuelve el nombre visible según idioma.
     * languageCode = "es", "en", etc.
     */
    public String getDisplayName(String lang) {
        return switch (lang.toLowerCase()) {
            case "en" -> en;
            default -> es;
        };
    }

    public String getColor() {
        return switch (badgeClass) {
            case "bg-success" -> "#28a745";
            case "bg-warning" -> "#ffc107";
            case "bg-secondary" -> "#6c757d";
            case "bg-dark" -> "#343a40";
            case "bg-danger" -> "#dc3545";
            default -> "#6c757d";
        };
    }

    public static AppointmentStatus fromCode(String code) {
        if (code == null) return null;
        for (AppointmentStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AppointmentStatus code: " + code);
    }

    public AppointmentStatusDto toDto(String lang) {
        return new AppointmentStatusDto(
                this.code,
                this.getDisplayName(lang),
                this.getColor()
        );
    }

}
