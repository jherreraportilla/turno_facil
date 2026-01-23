package com.turnofacil.controller;

import com.turnofacil.model.User;
import com.turnofacil.service.UserService;
import com.turnofacil.util.Constants;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return Constants.AUTH_LOGIN; // "auth/login"
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return Constants.AUTH_REGISTER; // "auth/register"
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") User user,
                           BindingResult result,
                           Model model) {

        if (result.hasErrors()) {
            return Constants.AUTH_REGISTER;
        }

        if (userService.existsByEmail(user.getEmail())) {
            model.addAttribute("error", "Este email ya está registrado");
            return Constants.AUTH_REGISTER;
        }

        // Registro + creación automática de BusinessConfig
        User registeredUser = userService.registerNewBusiness(user);

        // LOGIN AUTOMÁTICO DESPUÉS DEL REGISTRO
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                registeredUser.getEmail(),
                null, // la contraseña ya está encriptada, no la necesitamos aquí
                registeredUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Redirige directamente al dashboard
        return Constants.REDIRECT_ADMIN_DASHBOARD; // "redirect:/admin/dashboard"
    }
}