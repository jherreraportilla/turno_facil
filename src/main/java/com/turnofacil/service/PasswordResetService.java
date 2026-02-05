package com.turnofacil.service;

import com.turnofacil.model.PasswordResetToken;
import com.turnofacil.model.User;
import com.turnofacil.repository.PasswordResetTokenRepository;
import com.turnofacil.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRY_HOURS = 1;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                JavaMailSender mailSender,
                                TemplateEngine templateEngine) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Transactional
    public void createPasswordResetToken(String email) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            log.warn("Password reset solicitado para email no registrado: {}", email);
            return; // No revelar si el email existe
        }

        User user = userOpt.get();

        // Eliminar tokens anteriores de este usuario
        tokenRepository.deleteByUser(user);

        // Crear nuevo token
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        resetToken.setUsed(false);

        tokenRepository.save(resetToken);

        // Enviar email
        sendPasswordResetEmail(user, resetToken.getToken());

        log.info("Password reset token creado para usuario: {}", email);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalido"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("El enlace ha expirado o ya fue usado");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Contraseña restablecida para usuario: {}", user.getEmail());
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public User getUserByToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalido"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("El enlace ha expirado o ya fue usado");
        }

        return resetToken.getUser();
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) {
            log.warn("Email de reset no enviado - Email deshabilitado o remitente no configurado");
            return;
        }

        try {
            String resetUrl = baseUrl + "/auth/reset-password?token=" + token;

            Context context = new Context();
            context.setVariable("userName", user.getName());
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("expiryHours", TOKEN_EXPIRY_HOURS);

            String htmlContent = templateEngine.process("email/password-reset", context);

            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("Restablecer tu contraseña - TurnoFácil");
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("Email de reset enviado a: {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Error enviando email de reset: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Tokens de reset expirados eliminados");
    }
}
