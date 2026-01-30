package com.turnofacil.service;

import com.turnofacil.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio simple de rate limiting basado en memoria.
 * Para producción con múltiples instancias, usar Redis.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    // Almacena: key -> [contador, timestamp de inicio de ventana]
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();

    // Limpieza periódica de entradas antiguas (cada 5 minutos)
    private Instant lastCleanup = Instant.now();
    private static final long CLEANUP_INTERVAL_SECONDS = 300;

    /**
     * Verifica si una acción está permitida bajo el rate limit.
     *
     * @param key Identificador único (ej: IP + endpoint)
     * @param maxRequests Máximo de peticiones permitidas
     * @param windowSeconds Ventana de tiempo en segundos
     * @return true si está permitido, false si excede el límite
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        cleanupIfNeeded();

        long now = Instant.now().getEpochSecond();
        long[] data = requestCounts.compute(key, (k, v) -> {
            if (v == null || (now - v[1]) >= windowSeconds) {
                // Nueva ventana
                return new long[]{1, now};
            } else {
                // Incrementar contador en ventana existente
                v[0]++;
                return v;
            }
        });

        return data[0] <= maxRequests;
    }

    /**
     * Verifica y lanza excepción si se excede el límite.
     */
    public void checkRateLimit(String key, int maxRequests, int windowSeconds) {
        if (!isAllowed(key, maxRequests, windowSeconds)) {
            throw new RateLimitExceededException();
        }
    }

    /**
     * Rate limit para reservas públicas: 5 reservas por IP cada 60 minutos
     */
    public void checkPublicBookingLimit(String clientIp) {
        String key = "booking:" + clientIp;
        checkRateLimit(key, 5, 3600); // 5 por hora
    }

    /**
     * Rate limit para intentos de login: 5 intentos cada 15 minutos
     */
    public void checkLoginLimit(String clientIp) {
        String key = "login:" + clientIp;
        checkRateLimit(key, 5, 900); // 5 cada 15 min
    }

    /**
     * Rate limit para cambio de contraseña: 3 intentos cada 15 minutos
     */
    public void checkPasswordChangeLimit(String userId) {
        String key = "password:" + userId;
        checkRateLimit(key, 3, 900); // 3 cada 15 min
    }

    /**
     * Rate limit general para API: 100 peticiones por minuto
     */
    public void checkApiLimit(String clientIp) {
        String key = "api:" + clientIp;
        checkRateLimit(key, 100, 60);
    }

    /**
     * Limpia entradas antiguas para evitar memory leaks
     */
    private void cleanupIfNeeded() {
        Instant now = Instant.now();
        if (now.getEpochSecond() - lastCleanup.getEpochSecond() > CLEANUP_INTERVAL_SECONDS) {
            lastCleanup = now;
            long threshold = now.getEpochSecond() - 3600; // Eliminar entradas de más de 1 hora
            requestCounts.entrySet().removeIf(entry -> entry.getValue()[1] < threshold);
            log.debug("Rate limiter cleanup: {} entradas restantes", requestCounts.size());
        }
    }

    /**
     * Resetea el contador para una key específica (útil para tests)
     */
    public void reset(String key) {
        requestCounts.remove(key);
    }

    /**
     * Resetea todos los contadores (útil para tests)
     */
    public void resetAll() {
        requestCounts.clear();
    }
}
