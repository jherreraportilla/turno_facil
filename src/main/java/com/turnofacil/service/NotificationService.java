package com.turnofacil.service;

import com.turnofacil.dto.NotificationDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.Notification;
import com.turnofacil.model.enums.NotificationType;
import com.turnofacil.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES"));

    private final NotificationRepository notificationRepo;
    private final NotificationSseService sseService;

    public NotificationService(NotificationRepository notificationRepo,
                               NotificationSseService sseService) {
        this.notificationRepo = notificationRepo;
        this.sseService = sseService;
    }

    @Transactional
    public Notification createNewBookingNotification(Appointment appointment) {
        log.info("Creando notificacion para reserva de: {}", appointment.getClientName());

        Notification notification = new Notification();
        notification.setBusiness(appointment.getBusiness());
        notification.setType(NotificationType.NEW_BOOKING);
        notification.setTitle("Nueva reserva");
        notification.setMessage(String.format("%s reservo para el %s a las %s hs",
                appointment.getClientName(),
                appointment.getDate().format(DATE_FORMATTER),
                appointment.getTime().toString()));
        notification.setLink("/admin/dashboard");
        notification.setAppointment(appointment);

        Notification saved = notificationRepo.save(notification);
        log.info("Notificacion guardada con ID: {}", saved.getId());

        // Enviar notificacion en tiempo real via SSE
        try {
            sseService.sendNotification(appointment.getBusiness().getId(), toDto(saved));
        } catch (Exception e) {
            log.error("Error enviando notificacion SSE: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Notification createCancellationNotification(Appointment appointment) {
        Notification notification = new Notification();
        notification.setBusiness(appointment.getBusiness());
        notification.setType(NotificationType.CANCELLED);
        notification.setTitle("Reserva cancelada");
        notification.setMessage(String.format("La reserva de %s para el %s ha sido cancelada",
                appointment.getClientName(),
                appointment.getDate().format(DATE_FORMATTER)));
        notification.setLink("/admin/dashboard");
        notification.setAppointment(appointment);

        return notificationRepo.save(notification);
    }

    @Transactional
    public Notification createReminderSentNotification(Appointment appointment) {
        Notification notification = new Notification();
        notification.setBusiness(appointment.getBusiness());
        notification.setType(NotificationType.REMINDER_SENT);
        notification.setTitle("Recordatorio enviado");
        notification.setMessage(String.format("Se ha enviado recordatorio a %s para su turno del %s",
                appointment.getClientName(),
                appointment.getDate().format(DATE_FORMATTER)));
        notification.setAppointment(appointment);

        return notificationRepo.save(notification);
    }

    @Transactional
    public Notification createNoShowNotification(Appointment appointment) {
        Notification notification = new Notification();
        notification.setBusiness(appointment.getBusiness());
        notification.setType(NotificationType.NO_SHOW);
        notification.setTitle("Cliente no se presento");
        notification.setMessage(String.format("%s no se presento a su turno del %s",
                appointment.getClientName(),
                appointment.getDate().format(DATE_FORMATTER)));
        notification.setLink("/admin/dashboard");
        notification.setAppointment(appointment);

        return notificationRepo.save(notification);
    }

    public List<NotificationDto> getLatestNotifications(Long businessId) {
        return notificationRepo.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long businessId) {
        return notificationRepo.countByBusinessIdAndReadFalse(businessId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long businessId) {
        Notification notification = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notificacion no encontrada"));

        if (!notification.getBusiness().getId().equals(businessId)) {
            throw new SecurityException("No tienes permiso para esta accion");
        }

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepo.save(notification);
    }

    @Transactional
    public int markAllAsRead(Long businessId) {
        return notificationRepo.markAllAsReadByBusinessId(businessId);
    }

    private NotificationDto toDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setLink(notification.getLink());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setTimeAgo(formatTimeAgo(notification.getCreatedAt()));
        dto.setTypeIcon(notification.getType().getIcon());
        dto.setTypeColor(notification.getType().getColor());
        return dto;
    }

    private String formatTimeAgo(LocalDateTime dateTime) {
        Duration duration = Duration.between(dateTime, LocalDateTime.now());

        if (duration.toMinutes() < 1) {
            return "ahora";
        } else if (duration.toMinutes() < 60) {
            return "hace " + duration.toMinutes() + " min";
        } else if (duration.toHours() < 24) {
            return "hace " + duration.toHours() + "h";
        } else if (duration.toDays() < 7) {
            return "hace " + duration.toDays() + " dias";
        } else {
            return dateTime.toLocalDate().toString();
        }
    }
}
