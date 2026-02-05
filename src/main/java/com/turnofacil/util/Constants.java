package com.turnofacil.util;

public final class Constants {

    private Constants() {} // Evita instanciar

    // ------ AUTH ------
    public static final String AUTH_LOGIN = "auth/login";
    public static final String AUTH_REGISTER = "auth/register";
    public static final String REDIRECT_AUTH_LOGIN = "redirect:/auth/login?registered";

    // ------ ADMIN VIEWS ------
    public static final String ADMIN_DASHBOARD = "admin/dashboard";
    public static final String ADMIN_BASE = "/admin";
    public static final String ADMIN_DASHBOARD_URL = ADMIN_BASE + "/dashboard";

    // ------ PUBLIC CLIENT VIEWS (para el paso 2) ------
    public static final String PUBLIC_BOOKING = "public/booking";
    public static final String PUBLIC_SUCCESS = "public/success";

    // ------ REDIRECTS ------
    public static final String REDIRECT_ADMIN_DASHBOARD = "redirect:/admin/dashboard";

    //CONFIGURACION
    public static final String BUSINESS_CONFIG = "businessConfig";

    //ESTILOS
    public static final String TEXT_WHITE = "text-white";
    public static final String TEXT_BLACK = "text-black";

    //CONSTANTES GENERALES
    public static final String STATUS = "status";
    public static final String SUCCESS = "sucess";
}