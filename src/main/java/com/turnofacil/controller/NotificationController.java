package com.turnofacil.controller;

import com.turnofacil.dto.NotificationDto;
import com.turnofacil.model.User;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.service.NotificationService;
import com.turnofacil.service.NotificationSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final NotificationSseService sseService;
    private final UserRepository userRepo;

    public NotificationController(NotificationService notificationService,
                                  NotificationSseService sseService,
                                  UserRepository userRepo) {
        this.notificationService = notificationService;
        this.sseService = sseService;
        this.userRepo = userRepo;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(Authentication auth) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        log.info("SSE: Nueva conexion para usuario {}", business.getId());
        return sseService.subscribe(business.getId());
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getLatestNotifications(Authentication auth) {
        log.info("GET /api/notifications - Usuario: {}", auth != null ? auth.getName() : "null");

        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<NotificationDto> notifications = notificationService.getLatestNotifications(business.getId());
        log.info("Notificaciones encontradas: {}", notifications.size());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        log.info("GET /api/notifications/unread-count - Usuario: {}", auth != null ? auth.getName() : "null");

        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        long count = notificationService.getUnreadCount(business.getId());
        log.info("Notificaciones no leidas: {}", count);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication auth) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        notificationService.markAsRead(id, business.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(Authentication auth) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        int count = notificationService.markAllAsRead(business.getId());
        return ResponseEntity.ok(Map.of("success", true, "marked", count));
    }
}
