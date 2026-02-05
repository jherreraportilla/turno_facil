package com.turnofacil.security;

/**
 * ThreadLocal que almacena el businessId del usuario autenticado.
 * Se setea en TenantFilter y se puede usar en servicios/repos para validar aislamiento.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
