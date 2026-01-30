package com.turnofacil.model.enums;

public enum VatRegime {
    GENERAL("Regimen General", 21.00),
    REDUCED("Regimen Reducido", 10.00),
    SUPER_REDUCED("Regimen Superreducido", 4.00),
    EXEMPT("Exento de IVA", 0.00);

    private final String displayName;
    private final double defaultRate;

    VatRegime(String displayName, double defaultRate) {
        this.displayName = displayName;
        this.defaultRate = defaultRate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultRate() {
        return defaultRate;
    }
}
