package com.turnofacil.model.enums;

import java.util.Set;

public enum Feature {

    PORTFOLIO(Plan.PRO, Plan.BUSINESS),
    REMINDERS(Plan.PRO, Plan.BUSINESS),
    INVOICING(Plan.PRO, Plan.BUSINESS),
    WHATSAPP(Plan.BUSINESS),
    CUSTOM_LOGO(Plan.PRO, Plan.BUSINESS),
    EXPORT_EXCEL(Plan.PRO, Plan.BUSINESS),
    UNLIMITED_APPOINTMENTS(Plan.PRO, Plan.BUSINESS),
    UNLIMITED_SERVICES(Plan.PRO, Plan.BUSINESS);

    private final Set<Plan> availablePlans;

    Feature(Plan... plans) {
        this.availablePlans = Set.of(plans);
    }

    public boolean isAvailableIn(Plan plan) {
        return availablePlans.contains(plan);
    }
}
