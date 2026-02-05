package com.turnofacil.service;

import com.turnofacil.exception.RateLimitExceededException;
import com.turnofacil.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Servicio de rate limiting usando el cache service.
 * Funciona tanto con Redis (distribuido) como con InMemory (single instance).
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final CacheService cacheService;

    public RateLimiterService(CacheService cacheService) {
        this.cacheService = cacheService;
        log.info("RateLimiterService inicializado con provider: {}", cacheService.getProviderName());
    }

    /**
     * Verifica si una acción está permitida bajo el rate limit.
     *
     * @param key Identificador único (ej: IP + endpoint)
     * @param maxRequests Máximo de peticiones permitidas
     * @param windowSeconds Ventana de tiempo en segundos
     * @return true si está permitido, false si excede el límite
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        String rateLimitKey = "ratelimit:" + key;

        try {
            long currentCount = cacheService.increment(rateLimitKey, Duration.ofSeconds(windowSeconds));

            if (currentCount > maxRequests) {
                log.warn("Rate limit excedido para key: {} (count: {}, max: {})",
                        key, currentCount, maxRequests);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error en rate limiting, permitiendo por defecto: {}", e.getMessage());
            // En caso de error, permitimos la operación para no bloquear usuarios
            return true;
        }
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
     * Rate limit para recuperación de password: 3 intentos por hora por email
     */
    public void checkPasswordResetLimit(String email) {
        String key = "pwreset:" + email.toLowerCase();
        checkRateLimit(key, 3, 3600); // 3 por hora
    }

    /**
     * Rate limit para envío de emails: 10 por minuto por negocio
     */
    public void checkEmailLimit(Long businessId) {
        String key = "email:" + businessId;
        checkRateLimit(key, 10, 60);
    }

    /**
     * Obtiene el conteo actual de requests para una key.
     */
    public long getCurrentCount(String key) {
        return cacheService.getCounter("ratelimit:" + key);
    }

    /**
     * Obtiene información sobre el rate limit actual.
     */
    public RateLimitInfo getRateLimitInfo(String key, int maxRequests) {
        long currentCount = getCurrentCount(key);
        long remaining = Math.max(0, maxRequests - currentCount);
        boolean exceeded = currentCount > maxRequests;

        return new RateLimitInfo(currentCount, remaining, maxRequests, exceeded);
    }

    /**
     * Resetea el contador para una key específica (útil para tests)
     */
    public void reset(String key) {
        cacheService.delete("ratelimit:" + key);
    }

    /**
     * Resetea todos los contadores (útil para tests)
     */
    public void resetAll() {
        cacheService.clear();
    }

    /**
     * Información del estado de rate limit.
     */
    public record RateLimitInfo(
            long currentCount,
            long remaining,
            int limit,
            boolean exceeded
    ) {}
}
