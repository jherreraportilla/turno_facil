package com.turnofacil.model.enums;

/**
 * Estados posibles de un dominio personalizado.
 */
public enum DomainStatus {

    /**
     * Dominio registrado pero aún no verificado.
     * El usuario debe configurar los registros DNS.
     */
    PENDING_VERIFICATION("Pendiente de verificación", "warning"),

    /**
     * La verificación DNS falló.
     * El usuario debe revisar su configuración.
     */
    VERIFICATION_FAILED("Verificación fallida", "danger"),

    /**
     * Dominio verificado y activo.
     */
    ACTIVE("Activo", "success"),

    /**
     * Dominio desactivado manualmente.
     */
    INACTIVE("Inactivo", "secondary"),

    /**
     * El certificado SSL expiró o hay problemas.
     */
    SSL_ERROR("Error SSL", "danger");

    private final String displayName;
    private final String badgeClass;

    DomainStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
