package com.turnofacil.scheduler;

import com.turnofacil.dto.EmailAppointmentDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.service.EmailService;
import com.turnofacil.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "ES"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    public ReminderScheduler(AppointmentRepository appointmentRepository,
                             BusinessConfigRepository businessConfigRepository,
                             EmailService emailService,
                             WhatsAppService whatsAppService) {
        this.appointmentRepository = appointmentRepository;
        this.businessConfigRepository = businessConfigRepository;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        log.info("ReminderScheduler inicializado con WhatsApp provider: {}", whatsAppService.getProviderName());
    }

    /**
     * Ejecuta cada hora para enviar recordatorios de turnos.
     * Busca turnos pendientes/confirmados dentro de la ventana de tiempo configurada.
     */
    @Scheduled(cron = "0 0 * * * *") // Cada hora en punto
    @Transactional
    public void sendReminders() {
        log.info("Iniciando proceso de envio de recordatorios...");

        // Obtener negocios con recordatorios habilitados
        List<BusinessConfig> businessesWithReminders = businessConfigRepository.findByEnableRemindersTrue();

        if (businessesWithReminders.isEmpty()) {
            log.info("No hay negocios con recordatorios habilitados");
            return;
        }

        int emailsSent = 0;
        int whatsappSent = 0;

        for (BusinessConfig config : businessesWithReminders) {
            try {
                ReminderStats stats = processBusinessReminders(config);
                emailsSent += stats.emailsSent();
                whatsappSent += stats.whatsappSent();
            } catch (Exception e) {
                log.error("Error procesando recordatorios para negocio {}: {}",
                        config.getBusinessName(), e.getMessage());
            }
        }

        log.info("Proceso de recordatorios finalizado. Emails: {}, WhatsApp: {}", emailsSent, whatsappSent);
    }

    private ReminderStats processBusinessReminders(BusinessConfig config) {
        int hoursBeforeMin = config.getReminderHoursBefore();
        int hoursBeforeMax = hoursBeforeMin + 1; // Ventana de 1 hora

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(hoursBeforeMin);
        LocalDateTime windowEnd = now.plusHours(hoursBeforeMax);

        log.debug("Buscando turnos para {} entre {} y {}",
                config.getBusinessName(), windowStart, windowEnd);

        // Buscar turnos en la ventana de tiempo
        List<Appointment> appointmentsToRemind = findAppointmentsInWindow(
                config.getUser().getId(), windowStart, windowEnd);

        int emailsSent = 0;
        int whatsappSent = 0;

        for (Appointment appointment : appointmentsToRemind) {
            boolean needsEmail = !appointment.isReminderSent() && appointment.getClientEmail() != null;
            boolean needsWhatsapp = !appointment.isWhatsappReminderSent()
                    && config.isEnableWhatsappReminders()
                    && appointment.getClientPhone() != null
                    && whatsAppService.isAvailable();

            // Enviar email
            if (needsEmail) {
                if (sendEmailReminder(appointment, config)) {
                    appointment.setReminderSent(true);
                    emailsSent++;
                }
            }

            // Enviar WhatsApp
            if (needsWhatsapp) {
                if (sendWhatsAppReminder(appointment, config)) {
                    appointment.setWhatsappReminderSent(true);
                    whatsappSent++;
                }
            }

            // Guardar cambios si hubo algún envío
            if (needsEmail || needsWhatsapp) {
                appointmentRepository.save(appointment);
            }
        }

        return new ReminderStats(emailsSent, whatsappSent);
    }

    private boolean sendEmailReminder(Appointment appointment, BusinessConfig config) {
        try {
            EmailAppointmentDto emailDto = EmailAppointmentDto.from(appointment, config);
            emailService.sendReminder(emailDto);
            log.info("Recordatorio EMAIL enviado para turno {} - {} {} {}",
                    appointment.getId(),
                    appointment.getClientName(),
                    appointment.getDate(),
                    appointment.getTime());
            return true;
        } catch (Exception e) {
            log.error("Error enviando recordatorio EMAIL para turno {}: {}",
                    appointment.getId(), e.getMessage());
            return false;
        }
    }

    private boolean sendWhatsAppReminder(Appointment appointment, BusinessConfig config) {
        try {
            String dateTime = formatDateTime(appointment.getDate(), appointment.getTime());
            String serviceName = appointment.getServiceName() != null
                    ? appointment.getServiceName()
                    : "tu cita";

            WhatsAppService.SendResult result = whatsAppService.sendAppointmentReminder(
                    appointment.getClientPhone(),
                    config.getBusinessName(),
                    serviceName,
                    dateTime
            );

            if (result.success()) {
                log.info("Recordatorio WHATSAPP enviado para turno {} - {} {} {} (msgId: {})",
                        appointment.getId(),
                        appointment.getClientName(),
                        appointment.getDate(),
                        appointment.getTime(),
                        result.messageId());
                return true;
            } else {
                log.warn("WhatsApp no pudo enviar recordatorio para turno {}: {}",
                        appointment.getId(), result.errorMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Error enviando recordatorio WHATSAPP para turno {}: {}",
                    appointment.getId(), e.getMessage());
            return false;
        }
    }

    private List<Appointment> findAppointmentsInWindow(Long businessId,
                                                       LocalDateTime windowStart,
                                                       LocalDateTime windowEnd) {
        LocalDate startDate = windowStart.toLocalDate();
        LocalDate endDate = windowEnd.toLocalDate();

        // Obtener turnos en el rango de fechas
        List<Appointment> appointments = appointmentRepository
                .findByDateBetweenAndBusinessId(startDate, endDate, businessId);

        // Filtrar por hora y estado
        return appointments.stream()
                .filter(a -> !a.isReminderSent() || !a.isWhatsappReminderSent())
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING ||
                             a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> {
                    LocalDateTime appointmentDateTime = LocalDateTime.of(a.getDate(), a.getTime());
                    return !appointmentDateTime.isBefore(windowStart) &&
                           !appointmentDateTime.isAfter(windowEnd);
                })
                .toList();
    }

    private String formatDateTime(LocalDate date, java.time.LocalTime time) {
        String formattedDate = date.format(DATE_FORMATTER);
        String formattedTime = time.format(TIME_FORMATTER);
        return formattedDate + " a las " + formattedTime;
    }

    private record ReminderStats(int emailsSent, int whatsappSent) {}
}
