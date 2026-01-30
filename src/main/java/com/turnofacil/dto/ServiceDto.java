package com.turnofacil.dto;

import java.math.BigDecimal;

public record ServiceDto(
        Long id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String color,
        String icon,
        boolean active,
        Integer displayOrder
) {
    public ServiceDto(String name, String description, Integer durationMinutes, BigDecimal price, String icon) {
        this(null, name, description, durationMinutes, price, "#6366F1", icon, true, 0);
    }
}
