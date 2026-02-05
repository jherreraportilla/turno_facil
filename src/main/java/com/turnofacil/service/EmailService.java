package com.turnofacil.service;

import com.turnofacil.dto.EmailAppointmentDto;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendBookingConfirmation(EmailAppointmentDto dto) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) {
            log.info("Email de confirmacion no enviado - Email deshabilitado o remitente no configurado");
            return;
        }
        if (dto.clientEmail() == null || dto.clientEmail().isBlank()) {
            log.info("Email de confirmacion no enviado - Cliente sin email");
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("businessName", dto.businessName());
            context.setVariable("clientName", dto.clientName());
            context.setVariable("date", dto.date().format(DATE_FORMATTER));
            context.setVariable("time", dto.time().format(TIME_FORMATTER));
            context.setVariable("duration", dto.duration());
            context.setVariable("notes", dto.notes());
            context.setVariable("businessPhone", dto.businessPhone());
            context.setVariable("businessEmail", dto.businessEmail());

            // Link de cancelación
            if (dto.cancellationToken() != null) {
                String cancelUrl = baseUrl + "/public/appointment/" + dto.cancellationToken();
                context.setVariable("manageUrl", cancelUrl);
            }

            String htmlContent = templateEngine.process("email/booking-confirmation", context);

            sendHtmlEmail(
                    dto.clientEmail(),
                    "Confirmacion de tu turno en " + dto.businessName(),
                    htmlContent
            );

            log.info("Email de confirmacion enviado a {}", dto.clientEmail());

        } catch (Exception e) {
            log.error("Error al enviar email de confirmacion: {}", e.getMessage(), e);
        }
    }

    @Async
    public void sendBusinessNotification(EmailAppointmentDto dto, boolean receiveEmailNotifications) {
        if (!emailEnabled) {
            log.info("Email de notificacion no enviado - Email deshabilitado globalmente");
            return;
        }

        if (!receiveEmailNotifications) {
            log.info("Email de notificacion no enviado - Negocio tiene notificaciones desactivadas");
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("businessName", dto.businessName());
            context.setVariable("clientName", dto.clientName());
            context.setVariable("clientPhone", dto.clientPhone());
            context.setVariable("clientEmail", dto.clientEmail());
            context.setVariable("date", dto.date().format(DATE_FORMATTER));
            context.setVariable("time", dto.time().format(TIME_FORMATTER));
            context.setVariable("duration", dto.duration());
            context.setVariable("notes", dto.notes());

            String htmlContent = templateEngine.process("email/business-notification", context);

            sendHtmlEmail(
                    dto.businessEmail(),
                    "Nueva reserva: " + dto.clientName() + " - " + dto.date(),
                    htmlContent
            );

            log.info("Email de notificacion enviado al negocio {}", dto.businessEmail());

        } catch (Exception e) {
            log.error("Error al enviar email de notificacion al negocio: {}", e.getMessage(), e);
        }
    }

    @Async
    public void sendReminder(EmailAppointmentDto dto) {
        if (!emailEnabled || dto.clientEmail() == null || dto.clientEmail().isBlank()) {
            log.info("Recordatorio no enviado - Email deshabilitado o cliente sin email");
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("businessName", dto.businessName());
            context.setVariable("clientName", dto.clientName());
            context.setVariable("date", dto.date().format(DATE_FORMATTER));
            context.setVariable("time", dto.time().format(TIME_FORMATTER));
            context.setVariable("duration", dto.duration());
            context.setVariable("businessPhone", dto.businessPhone());
            context.setVariable("businessEmail", dto.businessEmail());

            String htmlContent = templateEngine.process("email/reminder", context);

            sendHtmlEmail(
                    dto.clientEmail(),
                    "Recordatorio: Tu turno en " + dto.businessName() + " es pronto",
                    htmlContent
            );

            log.info("Recordatorio enviado a {}", dto.clientEmail());

        } catch (Exception e) {
            log.error("Error al enviar recordatorio: {}", e.getMessage(), e);
        }
    }

    // ==================== ONBOARDING EMAILS ====================

    @Async
    public void sendWelcomeEmail(String toEmail, String businessName) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) return;

        try {
            Context context = new Context(new Locale("es", "ES"));
            context.setVariable("businessName", businessName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("billingUrl", baseUrl + "/admin/billing");

            String htmlContent = templateEngine.process("email/welcome", context);
            sendHtmlEmail(toEmail, "Bienvenido a TurnoFácil, " + businessName, htmlContent);
            log.info("Email de bienvenida enviado a {}", toEmail);
        } catch (Exception e) {
            log.error("Error al enviar email de bienvenida: {}", e.getMessage());
        }
    }

    @Async
    public void sendTrialEndingEmail(String toEmail, String businessName, int daysLeft) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) return;

        try {
            Context context = new Context(new Locale("es", "ES"));
            context.setVariable("businessName", businessName);
            context.setVariable("daysLeft", daysLeft);
            context.setVariable("billingUrl", baseUrl + "/admin/billing");

            String htmlContent = templateEngine.process("email/trial-ending", context);
            sendHtmlEmail(toEmail, businessName + ", tu prueba gratuita termina en " + daysLeft + " días", htmlContent);
            log.info("Email trial ending enviado a {} ({} días)", toEmail, daysLeft);
        } catch (Exception e) {
            log.error("Error al enviar email trial ending: {}", e.getMessage());
        }
    }

    @Async
    public void sendTrialExpiredEmail(String toEmail, String businessName) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) return;

        try {
            Context context = new Context(new Locale("es", "ES"));
            context.setVariable("businessName", businessName);
            context.setVariable("billingUrl", baseUrl + "/admin/billing");

            String htmlContent = templateEngine.process("email/trial-expired", context);
            sendHtmlEmail(toEmail, businessName + ", tu prueba gratuita ha terminado", htmlContent);
            log.info("Email trial expired enviado a {}", toEmail);
        } catch (Exception e) {
            log.error("Error al enviar email trial expired: {}", e.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
