package com.turnofacil.dto;

import com.turnofacil.model.enums.AppointmentStatus;
import java.time.LocalTime;

public record AppointmentDto(
        LocalTime time,
        String clientName,
        String clientPhone,
        String notes,
        AppointmentStatus status
) {}