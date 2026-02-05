package com.turnofacil.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/home"})
    public String home(Authentication auth) {
        // Si el usuario ya esta autenticado, ir al dashboard
        if (auth != null && auth.isAuthenticated()) {
            return "redirect:/admin/dashboard";
        }
        // Si no, mostrar la pagina de precios (marketing)
        return "redirect:/pricing";
    }
}
