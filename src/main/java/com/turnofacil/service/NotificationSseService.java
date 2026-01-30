package com.turnofacil.service;

import com.turnofacil.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationSseService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseService.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutos

    // Map de businessId -> lista de emitters (un usuario puede tener varias pestanas abiertas)
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long businessId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.computeIfAbsent(businessId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(businessId, emitter));
        emitter.onTimeout(() -> removeEmitter(businessId, emitter));
        emitter.onError(e -> removeEmitter(businessId, emitter));

        // Enviar evento inicial de conexion
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Conectado a notificaciones en tiempo real"));
        } catch (IOException e) {
            removeEmitter(businessId, emitter);
        }

        return emitter;
    }

    public void sendNotification(Long businessId, NotificationDto notification) {
        CopyOnWriteArrayList<SseEmitter> businessEmitters = emitters.get(businessId);
        if (businessEmitters == null || businessEmitters.isEmpty()) {
            log.debug("No hay clientes SSE conectados para el negocio {}", businessId);
            return;
        }

        log.info("Enviando notificacion SSE al negocio {} ({} conexiones)", businessId, businessEmitters.size());

        for (SseEmitter emitter : businessEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.debug("Error enviando SSE, removiendo emitter");
                removeEmitter(businessId, emitter);
            }
        }
    }

    private void removeEmitter(Long businessId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> businessEmitters = emitters.get(businessId);
        if (businessEmitters != null) {
            businessEmitters.remove(emitter);
            if (businessEmitters.isEmpty()) {
                emitters.remove(businessId);
            }
        }
    }
}
