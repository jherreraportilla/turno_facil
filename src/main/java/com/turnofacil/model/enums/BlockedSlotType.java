package com.turnofacil.model.enums;

public enum BlockedSlotType {
    VACATION("Vacaciones", "#10B981"),
    BREAK("Descanso", "#F59E0B"),
    MEETING("Reunión", "#3B82F6"),
    HOLIDAY("Festivo", "#EF4444"),
    CUSTOM("Personalizado", "#6B7280");

    private final String displayName;
    private final String color;

    BlockedSlotType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public static BlockedSlotType fromCode(String code) {
        for (BlockedSlotType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return CUSTOM;
    }
}
