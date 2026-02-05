package com.turnofacil.controller;

import com.turnofacil.model.User;
import com.turnofacil.service.PasswordResetService;
import com.turnofacil.service.UserService;
import com.turnofacil.util.Constants;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
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

    // ==================== FORGOT / RESET PASSWORD ====================

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        RedirectAttributes redirectAttrs) {
        try {
            passwordResetService.createPasswordResetToken(email);
        } catch (Exception e) {
            log.warn("Error en forgot password para {}: {}", email, e.getMessage());
        }

        // Siempre mostrar el mismo mensaje (no revelar si el email existe)
        redirectAttrs.addFlashAttribute("success",
                "Si existe una cuenta con este email, recibirás un enlace para restablecer tu contraseña.");
        return "redirect:/auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        try {
            if (!passwordResetService.isTokenValid(token)) {
                model.addAttribute("error", "El enlace ha expirado o es invalido");
                return "auth/reset-password";
            }

            User user = passwordResetService.getUserByToken(token);
            model.addAttribute("token", token);
            model.addAttribute("userName", user.getName());
            return "auth/reset-password";

        } catch (Exception e) {
            log.error("Token de reset invalido: {}", token);
            model.addAttribute("error", "El enlace ha expirado o es invalido");
            return "auth/reset-password";
        }
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String password,
                                       @RequestParam String confirmPassword,
                                       RedirectAttributes redirectAttrs,
                                       Model model) {
        try {
            if (!password.equals(confirmPassword)) {
                model.addAttribute("error", "Las contraseñas no coinciden");
                model.addAttribute("token", token);
                return "auth/reset-password";
            }

            if (password.length() < 6) {
                model.addAttribute("error", "La contraseña debe tener al menos 6 caracteres");
                model.addAttribute("token", token);
                return "auth/reset-password";
            }

            passwordResetService.resetPassword(token, password);

            redirectAttrs.addFlashAttribute("success",
                    "Contraseña restablecida exitosamente. Ya puedes iniciar sesión.");
            return "redirect:/auth/login";

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("token", token);
            return "auth/reset-password";
        } catch (Exception e) {
            log.error("Error al restablecer contraseña: {}", e.getMessage());
            model.addAttribute("error", "Error al restablecer la contraseña. Intenta de nuevo.");
            model.addAttribute("token", token);
            return "auth/reset-password";
        }
    }
}