package com.turnofacil.dto;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO que encapsula todos los datos necesarios para enviar emails relacionados con turnos.
 * Evita pasar entidades JPA a hilos @Async, previniendo problemas de sesión Hibernate.
 */
public record EmailAppointmentDto(
        String clientName,
        String clientPhone,
        String clientEmail,
        LocalDate date,
        LocalTime time,
        Integer duration,
        String notes,
        String cancellationToken,
        String businessName,
        String businessPhone,
        String businessEmail
) {

    public static EmailAppointmentDto from(Appointment appointment, BusinessConfig config) {
        return new EmailAppointmentDto(
                appointment.getClientName(),
                appointment.getClientPhone(),
                appointment.getClientEmail(),
                appointment.getDate(),
                appointment.getTime(),
                appointment.getDuration(),
                appointment.getNotes(),
                appointment.getCancellationToken(),
                config.getBusinessName(),
                appointment.getBusiness().getPhone(),
                appointment.getBusiness().getEmail()
        );
    }

    public static EmailAppointmentDto from(Appointment appointment, String businessName) {
        return new EmailAppointmentDto(
                appointment.getClientName(),
                appointment.getClientPhone(),
                appointment.getClientEmail(),
                appointment.getDate(),
                appointment.getTime(),
                appointment.getDuration(),
                appointment.getNotes(),
                appointment.getCancellationToken(),
                businessName,
                appointment.getBusiness().getPhone(),
                appointment.getBusiness().getEmail()
        );
    }
}
