package com.turnofacil.service;

import com.turnofacil.dto.AppointmentDto;
import com.turnofacil.dto.EmailAppointmentDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.ServiceRepository;
import com.turnofacil.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    // Lock por negocio para evitar double-booking por concurrencia
    private final ConcurrentHashMap<Long, ReentrantLock> businessLocks = new ConcurrentHashMap<>();

    private final AppointmentRepository appointmentRepo;
    private final UserService userService;
    private final BlockedSlotService blockedSlotService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ServiceRepository serviceRepo;
    private final BusinessConfigRepository businessConfigRepo;
    private final PlanLimitsService planLimitsService;
    private final WhatsAppService whatsAppService;

    public AppointmentService(AppointmentRepository appointmentRepo,
                              UserService userService,
                              BlockedSlotService blockedSlotService,
                              EmailService emailService,
                              NotificationService notificationService,
                              ServiceRepository serviceRepo,
                              BusinessConfigRepository businessConfigRepo,
                              PlanLimitsService planLimitsService,
                              WhatsAppService whatsAppService) {
        this.appointmentRepo = appointmentRepo;
        this.userService = userService;
        this.blockedSlotService = blockedSlotService;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.serviceRepo = serviceRepo;
        this.businessConfigRepo = businessConfigRepo;
        this.planLimitsService = planLimitsService;
        this.whatsAppService = whatsAppService;
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

        // Validación de límites del plan
        if (!planLimitsService.canCreateAppointment(business.getId())) {
            throw new com.turnofacil.exception.PlanLimitExceededException(
                    "Has alcanzado el límite de 30 citas/mes del plan gratuito. Actualiza tu plan para citas ilimitadas.");
        }

        int appointmentDuration = duration != null ? duration : 30;
        LocalTime endTime = time.plusMinutes(appointmentDuration);

        // Adquirir lock por negocio para evitar double-booking
        ReentrantLock lock = businessLocks.computeIfAbsent(business.getId(), id -> new ReentrantLock());
        lock.lock();
        try {
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
            appointment.setCancellationToken(UUID.randomUUID().toString());

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

            // Construir DTO con todos los datos antes de pasar al hilo async
            BusinessConfig config = businessConfigRepo.findByUserId(business.getId()).orElse(null);
            EmailAppointmentDto emailDto = config != null
                    ? EmailAppointmentDto.from(savedAppointment, config)
                    : EmailAppointmentDto.from(savedAppointment, business.getName());

            // Enviar emails de confirmacion (async - no bloquea la respuesta)
            try {
                emailService.sendBookingConfirmation(emailDto);
                boolean receiveNotifications = config != null && config.isReceiveEmailNotifications();
                emailService.sendBusinessNotification(emailDto, receiveNotifications);
            } catch (Exception e) {
                log.error("Error enviando emails de confirmacion: {}", e.getMessage());
            }

            // Enviar confirmación por WhatsApp si está habilitado
            if (config != null && config.isEnableWhatsappConfirmations()
                    && clientPhone != null && !clientPhone.isBlank()
                    && whatsAppService.isAvailable()) {
                try {
                    String formattedDateTime = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + " a las " + time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    whatsAppService.sendAppointmentConfirmation(
                            clientPhone,
                            config.getBusinessName(),
                            savedAppointment.getServiceName(),
                            formattedDateTime,
                            savedAppointment.getCancellationToken()
                    );
                    log.info("Confirmación WhatsApp enviada al cliente: {}", clientPhone);
                } catch (Exception e) {
                    log.warn("Error enviando WhatsApp de confirmacion: {}", e.getMessage());
                }
            }

            // Crear notificacion para el admin
            try {
                notificationService.createNewBookingNotification(savedAppointment);
            } catch (Exception e) {
                log.error("Error creando notificacion: {}", e.getMessage());
            }

            return savedAppointment;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verifica si hay solapamiento con otros turnos.
     * Turno A (inicio-fin) se solapa con Turno B si: B.inicio < A.fin AND B.fin > A.inicio
     * Considera el buffer time configurado entre citas.
     */
    public boolean hasOverlappingAppointment(Long businessId, LocalDate date,
                                             LocalTime startTime, LocalTime endTime,
                                             Long excludeId) {
        List<Appointment> existingAppointments = appointmentRepo
                .findActiveAppointmentsByDateAndBusiness(businessId, date);

        // Obtener buffer time del negocio
        int bufferMinutes = businessConfigRepo.findByUserId(businessId)
                .map(BusinessConfig::getBufferTimeMinutes)
                .orElse(0);

        return existingAppointments.stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .anyMatch(a -> {
                    LocalTime existingStart = a.getTime();
                    // Fin de cita + buffer time
                    LocalTime existingEnd = existingStart.plusMinutes(a.getDuration() + bufferMinutes);
                    // Solapamiento: B.inicio < A.fin AND B.fin > A.inicio
                    return existingStart.isBefore(endTime) && existingEnd.isAfter(startTime);
                });
    }

    // Método auxiliar
    @Transactional(readOnly = true)
    public boolean isSlotTaken(LocalDate date, LocalTime time, Long businessId) {
        return appointmentRepo.existsByDateAndTimeAndBusinessId(date, time, businessId);
    }

    // Obtener todos los turnos de un negocio
    @Transactional(readOnly = true)
    public List<Appointment> getAllByBusiness(User business) {
        return appointmentRepo.findByBusinessId(business.getId());
    }

    @Transactional(readOnly = true)
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

    // Cancelar turno por token (cliente)
    @Transactional
    public Appointment cancelByToken(String token) {
        Appointment appt = appointmentRepo.findByCancellationToken(token)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Este turno ya fue cancelado");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("No se puede cancelar un turno completado");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepo.save(appt);

        try {
            notificationService.createCancellationNotification(appt);
        } catch (Exception e) {
            log.error("Error creando notificacion de cancelacion: {}", e.getMessage());
        }

        return appt;
    }

    // Obtener turno por token (para vista pública)
    @Transactional(readOnly = true)
    public Appointment getByToken(String token) {
        return appointmentRepo.findByCancellationToken(token)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
    }

    // Reagendar turno por token (cliente)
    @Transactional
    public Appointment rescheduleByToken(String token, LocalDate newDate, LocalTime newTime) {
        Appointment appt = appointmentRepo.findByCancellationToken(token)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("No se puede reagendar un turno cancelado");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("No se puede reagendar un turno completado");
        }

        int duration = appt.getDuration() != null ? appt.getDuration() : 30;
        LocalTime endTime = newTime.plusMinutes(duration);
        Long businessId = appt.getBusiness().getId();

        ReentrantLock lock = businessLocks.computeIfAbsent(businessId, id -> new ReentrantLock());
        lock.lock();
        try {
            if (blockedSlotService.isBlocked(businessId, newDate, newTime)) {
                throw new IllegalStateException("Este horario no esta disponible (bloqueado)");
            }
            if (hasOverlappingAppointment(businessId, newDate, newTime, endTime, appt.getId())) {
                throw new IllegalStateException("Este horario se solapa con otro turno existente");
            }

            appt.setDate(newDate);
            appt.setTime(newTime);
            return appointmentRepo.save(appt);
        } finally {
            lock.unlock();
        }
    }

    // Editar turno (admin)
    @Transactional
    public Appointment updateAppointment(Long id, User business, LocalDate date, LocalTime time,
                                          Long serviceId, String notes, String internalNotes) {
        Appointment appt = appointmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        if (!appt.getBusiness().getId().equals(business.getId())) {
            throw new SecurityException("No tienes permiso");
        }

        // Validar solapamiento si cambió fecha/hora
        if (!appt.getDate().equals(date) || !appt.getTime().equals(time)) {
            int duration = appt.getDuration() != null ? appt.getDuration() : 30;
            LocalTime endTime = time.plusMinutes(duration);

            if (hasOverlappingAppointment(business.getId(), date, time, endTime, appt.getId())) {
                throw new IllegalStateException("Este horario se solapa con otro turno existente");
            }

            appt.setDate(date);
            appt.setTime(time);
        }

        if (serviceId != null && (appt.getService() == null || !appt.getService().getId().equals(serviceId))) {
            serviceRepo.findById(serviceId).ifPresent(service -> {
                appt.setService(service);
                appt.setDuration(service.getDurationMinutes());
            });
        }

        appt.setNotes(notes);
        appt.setInternalNotes(internalNotes);

        return appointmentRepo.save(appt);
    }

    // Historial de cliente (admin)
    @Transactional(readOnly = true)
    public List<Appointment> getClientHistory(User business, String search) {
        return appointmentRepo.findByClientSearch(business.getId(), search);
    }

    /**
     * Cambia el estado de un turno validando permisos y transiciones válidas.
     * Regla de dominio: Solo se permiten transiciones válidas según el estado actual.
     */
    @Transactional
    public Appointment updateStatus(Long appointmentId, User business, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        // Validar permisos
        if (!appointment.getBusiness().getId().equals(business.getId())) {
            throw new SecurityException("No tienes permiso para modificar este turno");
        }

        // Validar transición de estado (regla de dominio)
        AppointmentStatus currentStatus = appointment.getStatus();
        if (currentStatus != null && !currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("No se puede cambiar de '%s' a '%s'",
                            currentStatus.getDisplayName("es"),
                            newStatus.getDisplayName("es")));
        }

        appointment.setStatus(newStatus);
        Appointment saved = appointmentRepo.save(appointment);

        log.info("Estado actualizado - Turno ID: {} | {} -> {}",
                appointmentId,
                currentStatus != null ? currentStatus.name() : "null",
                newStatus.name());

        return saved;
    }

    /**
     * Obtiene un turno por ID validando permisos del negocio.
     */
    @Transactional(readOnly = true)
    public Appointment getByIdAndBusiness(Long appointmentId, User business) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        if (!appointment.getBusiness().getId().equals(business.getId())) {
            throw new SecurityException("No tienes permiso para ver este turno");
        }

        return appointment;
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