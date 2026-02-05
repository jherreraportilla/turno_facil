package com.turnofacil.model.enums;

/**
 * Roles de usuario en el sistema.
 */
public enum Role {

    /**
     * Usuario normal (dueño de negocio).
     */
    ADMIN("ROLE_ADMIN", "Administrador de Negocio"),

    /**
     * Super administrador de la plataforma.
     * Tiene acceso a todas las funcionalidades y puede ver métricas globales.
     */
    SUPER_ADMIN("ROLE_SUPER_ADMIN", "Super Administrador");

    private final String authority;
    private final String displayName;

    Role(String authority, String displayName) {
        this.authority = authority;
        this.displayName = displayName;
    }

    public String getAuthority() {
        return authority;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Verifica si este rol tiene permisos de super admin.
     */
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
