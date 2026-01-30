package com.turnofacil.service;

import com.turnofacil.dto.AppointmentDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepo;
    private final UserService userService;
    private final BlockedSlotService blockedSlotService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ServiceRepository serviceRepo;
    private final BusinessConfigRepository businessConfigRepo;

    public AppointmentService(AppointmentRepository appointmentRepo,
                              UserService userService,
                              BlockedSlotService blockedSlotService,
                              EmailService emailService,
                              NotificationService notificationService,
                              ServiceRepository serviceRepo,
                              BusinessConfigRepository businessConfigRepo) {
        this.appointmentRepo = appointmentRepo;
        this.userService = userService;
        this.blockedSlotService = blockedSlotService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.serviceRepo = serviceRepo;
        this.businessConfigRepo = businessConfigRepo;
    }

    // CREAR TURNO DESDE PAGINA PUBLICA
    @Transactional
    public Appointment createAppointment(User business,
                                         LocalDate date,
                                         LocalTime time,
                                         Integer duration,
                                         String clientName,
                                         String clientPhone,
                                         String clientEmail,
                                         String notes) {
        return createAppointment(business, date, time, duration, null, clientName, clientPhone, clientEmail, notes);
    }

    @Transactional
    public Appointment createAppointment(User business,
                                         LocalDate date,
                                         LocalTime time,
                                         Integer duration,
                                         Long serviceId,
                                         String clientName,
                                         String clientPhone,
                                         String clientEmail,
                                         String notes) {

        int appointmentDuration = duration != null ? duration : 30;
        LocalTime endTime = time.plusMinutes(appointmentDuration);

        // Validacion 1: Verificar si el horario esta bloqueado
        if (blockedSlotService.isBlocked(business.getId(), date, time)) {
            throw new IllegalStateException("Este horario no esta disponible (bloqueado)");
        }

        // Validacion 2: Verificar solapamientos con otros turnos
        if (hasOverlappingAppointment(business.getId(), date, time, endTime, null)) {
            throw new IllegalStateException("Este horario se solapa con otro turno existente");
        }

        Appointment appointment = new Appointment();
        appointment.setBusiness(business);
        appointment.setDate(date);
        appointment.setTime(time);
        appointment.setDuration(appointmentDuration);
        appointment.setClientName(clientName);
        appointment.setClientPhone(clientPhone);
        appointment.setClientEmail(clientEmail);
        appointment.setNotes(notes);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setReminderSent(false);

        // ============ LLENAR SNAPSHOTS (INMUTABLES) ============
        // Snapshot del servicio
        if (serviceId != null) {
            serviceRepo.findById(serviceId).ifPresent(service -> {
                appointment.setService(service);
                appointment.setServiceName(service.getName());
                appointment.setServicePrice(service.getPrice());
                appointment.setServiceDuration(service.getDurationMinutes());
            });
        }

        // Snapshot del negocio
        businessConfigRepo.findByUserId(business.getId()).ifPresent(config -> {
            appointment.setBusinessName(config.getBusinessName());
        });
        // ========================================================

        Appointment savedAppointment = appointmentRepo.save(appointment);

        // Enviar emails de confirmacion
        try {
            emailService.sendBookingConfirmation(savedAppointment);
            emailService.sendBusinessNotification(savedAppointment);
        } catch (Exception e) {
            log.error("Error enviando emails de confirmacion: {}", e.getMessage());
        }

        // Crear notificacion para el admin
        try {
            notificationService.createNewBookingNotification(savedAppointment);
        } catch (Exception e) {
            log.error("Error creando notificacion: {}", e.getMessage());
        }

        return savedAppointment;
    }

    /**
     * Verifica si hay solapamiento con otros turnos.
     * Turno A (inicio-fin) se solapa con Turno B si: B.inicio < A.fin AND B.fin > A.inicio
     */
    public boolean hasOverlappingAppointment(Long businessId, LocalDate date,
                                             LocalTime startTime, LocalTime endTime,
                                             Long excludeId) {
        List<Appointment> existingAppointments = appointmentRepo
                .findActiveAppointmentsByDateAndBusiness(businessId, date);

        return existingAppointments.stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .anyMatch(a -> {
                    LocalTime existingStart = a.getTime();
                    LocalTime existingEnd = existingStart.plusMinutes(a.getDuration());
                    // Solapamiento: B.inicio < A.fin AND B.fin > A.inicio
                    return existingStart.isBefore(endTime) && existingEnd.isAfter(startTime);
                });
    }

    // Método auxiliar
    public boolean isSlotTaken(LocalDate date, LocalTime time, Long businessId) {
        return appointmentRepo.existsByDateAndTimeAndBusinessId(date, time, businessId);
    }

    // Obtener todos los turnos de un negocio
    public List<Appointment> getAllByBusiness(User business) {
        return appointmentRepo.findByBusinessId(business.getId());
    }

    public List<Appointment> getByDateAndBusiness(LocalDate date, User business) {
        return appointmentRepo.findByDateAndBusinessIdOrderByTimeAsc(date, business.getId());
    }

    // Cancelar turno
    @Transactional
    public void cancelAppointment(Long id, User business) {
        Appointment appt = appointmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
        if (!appt.getBusiness().getId().equals(business.getId())) {
            throw new SecurityException("No tienes permiso");
        }
        appt.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepo.save(appt);

        // Crear notificacion de cancelacion
        try {
            notificationService.createCancellationNotification(appt);
        } catch (Exception e) {
            log.error("Error creando notificacion de cancelacion: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getTodayAppointmentsForCurrentUser(Authentication authentication) {
        User business = userService.getCurrentBusinessByEmail(authentication.getName());
        LocalDate today = LocalDate.now();

        return appointmentRepo.findByDateAndBusinessIdOrderByTimeAsc(today, business.getId())
                .stream()
                .map(appt -> new AppointmentDto(
                        appt.getTime(),
                        appt.getClientName(),
                        appt.getClientPhone(),
                        appt.getNotes(),
                        appt.getStatus()
                ))
                .collect(Collectors.toList());
    }
}