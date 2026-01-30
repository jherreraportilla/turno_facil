package com.turnofacil.scheduler;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final AppointmentRepository appointmentRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final EmailService emailService;

    public ReminderScheduler(AppointmentRepository appointmentRepository,
                             BusinessConfigRepository businessConfigRepository,
                             EmailService emailService) {
        this.appointmentRepository = appointmentRepository;
        this.businessConfigRepository = businessConfigRepository;
        this.emailService = emailService;
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

        int remindersSent = 0;

        for (BusinessConfig config : businessesWithReminders) {
            try {
                int sent = processBusinessReminders(config);
                remindersSent += sent;
            } catch (Exception e) {
                log.error("Error procesando recordatorios para negocio {}: {}",
                        config.getBusinessName(), e.getMessage());
            }
        }

        log.info("Proceso de recordatorios finalizado. Total enviados: {}", remindersSent);
    }

    private int processBusinessReminders(BusinessConfig config) {
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

        int sent = 0;
        for (Appointment appointment : appointmentsToRemind) {
            if (!appointment.isReminderSent()) {
                try {
                    emailService.sendReminder(appointment);
                    appointment.setReminderSent(true);
                    appointmentRepository.save(appointment);
                    sent++;
                    log.info("Recordatorio enviado para turno {} - {} {} {}",
                            appointment.getId(),
                            appointment.getClientName(),
                            appointment.getDate(),
                            appointment.getTime());
                } catch (Exception e) {
                    log.error("Error enviando recordatorio para turno {}: {}",
                            appointment.getId(), e.getMessage());
                }
            }
        }

        return sent;
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
                .filter(a -> !a.isReminderSent())
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING ||
                             a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> {
                    LocalDateTime appointmentDateTime = LocalDateTime.of(a.getDate(), a.getTime());
                    return !appointmentDateTime.isBefore(windowStart) &&
                           !appointmentDateTime.isAfter(windowEnd);
                })
                .toList();
    }
}
