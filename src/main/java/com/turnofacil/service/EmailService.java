package com.turnofacil.service;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;
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
    private final BusinessConfigService businessConfigService;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        BusinessConfigService businessConfigService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.businessConfigService = businessConfigService;
    }

    @Async
    public void sendBookingConfirmation(Appointment appointment) {
        if (!emailEnabled || fromEmail == null || fromEmail.isBlank()) {
            log.info("Email de confirmacion no enviado - Email deshabilitado o remitente no configurado");
            return;
        }
        if (appointment.getClientEmail() == null || appointment.getClientEmail().isBlank()) {
            log.info("Email de confirmacion no enviado - Cliente sin email");
            return;
        }

        try {
            BusinessConfig config = businessConfigService.getByUserId(appointment.getBusiness().getId());

            Context context = new Context();
            context.setVariable("businessName", config.getBusinessName());
            context.setVariable("clientName", appointment.getClientName());
            context.setVariable("date", appointment.getDate().format(DATE_FORMATTER));
            context.setVariable("time", appointment.getTime().format(TIME_FORMATTER));
            context.setVariable("duration", appointment.getDuration());
            context.setVariable("notes", appointment.getNotes());
            context.setVariable("businessPhone", appointment.getBusiness().getPhone());
            context.setVariable("businessEmail", appointment.getBusiness().getEmail());

            String htmlContent = templateEngine.process("email/booking-confirmation", context);

            sendHtmlEmail(
                    appointment.getClientEmail(),
                    "Confirmacion de tu turno en " + config.getBusinessName(),
                    htmlContent
            );

            log.info("Email de confirmacion enviado a {}", appointment.getClientEmail());

        } catch (Exception e) {
            log.error("Error al enviar email de confirmacion: {}", e.getMessage());
        }
    }

    @Async
    public void sendBusinessNotification(Appointment appointment) {
        if (!emailEnabled) {
            log.info("Email de notificacion no enviado - Email deshabilitado globalmente");
            return;
        }

        try {
            BusinessConfig config = businessConfigService.getByUserId(appointment.getBusiness().getId());

            // Verificar preferencia del negocio
            if (!config.isReceiveEmailNotifications()) {
                log.info("Email de notificacion no enviado - Negocio tiene notificaciones desactivadas");
                return;
            }

            Context context = new Context();
            context.setVariable("businessName", config.getBusinessName());
            context.setVariable("clientName", appointment.getClientName());
            context.setVariable("clientPhone", appointment.getClientPhone());
            context.setVariable("clientEmail", appointment.getClientEmail());
            context.setVariable("date", appointment.getDate().format(DATE_FORMATTER));
            context.setVariable("time", appointment.getTime().format(TIME_FORMATTER));
            context.setVariable("duration", appointment.getDuration());
            context.setVariable("notes", appointment.getNotes());

            String htmlContent = templateEngine.process("email/business-notification", context);

            sendHtmlEmail(
                    appointment.getBusiness().getEmail(),
                    "Nueva reserva: " + appointment.getClientName() + " - " + appointment.getDate(),
                    htmlContent
            );

            log.info("Email de notificacion enviado al negocio {}", appointment.getBusiness().getEmail());

        } catch (Exception e) {
            log.error("Error al enviar email de notificacion al negocio: {}", e.getMessage());
        }
    }

    @Async
    public void sendReminder(Appointment appointment) {
        if (!emailEnabled || appointment.getClientEmail() == null || appointment.getClientEmail().isBlank()) {
            log.info("Recordatorio no enviado - Email deshabilitado o cliente sin email");
            return;
        }

        try {
            BusinessConfig config = businessConfigService.getByUserId(appointment.getBusiness().getId());

            Context context = new Context();
            context.setVariable("businessName", config.getBusinessName());
            context.setVariable("clientName", appointment.getClientName());
            context.setVariable("date", appointment.getDate().format(DATE_FORMATTER));
            context.setVariable("time", appointment.getTime().format(TIME_FORMATTER));
            context.setVariable("duration", appointment.getDuration());
            context.setVariable("businessPhone", appointment.getBusiness().getPhone());
            context.setVariable("businessEmail", appointment.getBusiness().getEmail());

            String htmlContent = templateEngine.process("email/reminder", context);

            sendHtmlEmail(
                    appointment.getClientEmail(),
                    "Recordatorio: Tu turno en " + config.getBusinessName() + " es pronto",
                    htmlContent
            );

            log.info("Recordatorio enviado a {}", appointment.getClientEmail());

        } catch (Exception e) {
            log.error("Error al enviar recordatorio: {}", e.getMessage());
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
