package com.turnofacil.dto;

import com.turnofacil.model.enums.BlockedSlotType;

import java.time.LocalDate;
import java.time.LocalTime;

public record BlockedSlotDto(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean allDay,
        BlockedSlotType type,
        String notes
) {
}
