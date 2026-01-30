package com.turnofacil.dto;

import com.turnofacil.model.BlockedSlot;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO para exponer bloqueos de horario al público.
 * Solo expone información necesaria para mostrar en calendario.
 */
public record PublicBlockedSlotDto(
        String id,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean allDay,
        String color
) {
    /**
     * Crea un PublicBlockedSlotDto desde un BlockedSlot.
     * No expone título ni notas para mantener privacidad del negocio.
     */
    public static PublicBlockedSlotDto fromBlockedSlot(BlockedSlot block) {
        return new PublicBlockedSlotDto(
                "blocked-" + block.getId(),
                block.getStartDate(),
                block.getEndDate(),
                block.getStartTime(),
                block.getEndTime(),
                block.isAllDay(),
                block.getType().getColor()
        );
    }
}
