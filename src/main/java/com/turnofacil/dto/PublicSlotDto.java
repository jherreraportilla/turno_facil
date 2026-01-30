package com.turnofacil.dto;

import com.turnofacil.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO para exponer información de slots al público.
 * NO incluye datos del cliente para proteger la privacidad.
 */
public record PublicSlotDto(
        LocalDate date,
        LocalTime time,
        LocalTime endTime,
        int duration,
        boolean occupied
) {
    /**
     * Crea un PublicSlotDto desde un Appointment.
     * Solo expone fecha, hora y duración - NUNCA datos del cliente.
     */
    public static PublicSlotDto fromAppointment(Appointment appointment) {
        LocalTime endTime = appointment.getTime().plusMinutes(appointment.getDuration());
        return new PublicSlotDto(
                appointment.getDate(),
                appointment.getTime(),
                endTime,
                appointment.getDuration(),
                true // siempre ocupado si viene de un appointment
        );
    }

    /**
     * Formato ISO para el calendario
     */
    public String getStartIso() {
        return date.toString() + "T" + time.toString();
    }

    public String getEndIso() {
        return date.toString() + "T" + endTime.toString();
    }
}
