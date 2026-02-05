package com.turnofacil.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Plan {

    FREE("Gratis", 0, 30, 3),
    PRO("Pro", 1200, -1, -1),       // 12.00 EUR, límites ilimitados (-1)
    BUSINESS("Business", 2900, -1, -1); // 29.00 EUR, límites ilimitados (-1)

    private final String displayName;
    private final int priceCents;
    private final int maxAppointmentsPerMonth; // -1 = ilimitado
    private final int maxServices;             // -1 = ilimitado

    /**
     * Verifica si el plan tiene límite de citas por mes.
     */
    public boolean hasAppointmentLimit() {
        return maxAppointmentsPerMonth > 0;
    }

    /**
     * Verifica si el plan tiene límite de servicios.
     */
    public boolean hasServiceLimit() {
        return maxServices > 0;
    }

    /**
     * Verifica si se puede crear una cita más dado el conteo actual.
     */
    public boolean canCreateAppointment(long currentCount) {
        if (!hasAppointmentLimit()) return true;
        return currentCount < maxAppointmentsPerMonth;
    }

    /**
     * Verifica si se puede crear un servicio más dado el conteo actual.
     */
    public boolean canCreateService(int currentCount) {
        if (!hasServiceLimit()) return true;
        return currentCount < maxServices;
    }

    /**
     * Calcula citas restantes este mes.
     * @return restantes, o -1 si es ilimitado
     */
    public int appointmentsRemaining(long currentCount) {
        if (!hasAppointmentLimit()) return -1;
        return Math.max(0, (int) (maxAppointmentsPerMonth - currentCount));
    }

    /**
     * Calcula servicios restantes.
     * @return restantes, o -1 si es ilimitado
     */
    public int servicesRemaining(int currentCount) {
        if (!hasServiceLimit()) return -1;
        return Math.max(0, maxServices - currentCount);
    }
}
