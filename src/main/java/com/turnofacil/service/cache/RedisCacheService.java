package com.turnofacil.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementacion de cache usando Redis.
 * Usa StringRedisTemplate para simplicidad (suficiente para cache y rate limiting).
 */
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisCacheService(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "turnofacil:";
        log.info("RedisCacheService inicializado con prefix: {}", this.keyPrefix);
    }

    private String prefixKey(String key) {
        return keyPrefix + key;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String prefixedKey = prefixKey(key);
            String stringValue = String.valueOf(value);
            if (ttl != null) {
                redisTemplate.opsForValue().set(prefixedKey, stringValue, ttl);
            } else {
                redisTemplate.opsForValue().set(prefixedKey, stringValue);
            }
        } catch (Exception e) {
            log.error("Error al guardar en Redis cache: {}", e.getMessage());
            throw new CacheException("Error al guardar en cache", e);
        }
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, null);
    }

    @Override
    public Optional<Object> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(prefixKey(key));
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.error("Error al leer de Redis cache: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).map(v -> (T) v);
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(prefixKey(key));
        } catch (Exception e) {
            log.error("Error al eliminar de Redis cache: {}", e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(prefixKey(key));
            return exists != null && exists;
        } catch (Exception e) {
            log.error("Error al verificar existencia en Redis: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public long increment(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(prefixKey(key));
            return value != null ? value : 0;
        } catch (Exception e) {
            log.error("Error al incrementar contador en Redis: {}", e.getMessage());
            throw new CacheException("Error al incrementar contador", e);
        }
    }

    @Override
    public long increment(String key, Duration ttl) {
        try {
            String prefixedKey = prefixKey(key);
            Long value = redisTemplate.opsForValue().increment(prefixedKey);

            // Establecer TTL solo si es la primera vez (valor == 1)
            if (value != null && value == 1 && ttl != null) {
                redisTemplate.expire(prefixedKey, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }

            return value != null ? value : 0;
        } catch (Exception e) {
            log.error("Error al incrementar contador con TTL en Redis: {}", e.getMessage());
            throw new CacheException("Error al incrementar contador", e);
        }
    }

    @Override
    public long getCounter(String key) {
        try {
            String value = redisTemplate.opsForValue().get(prefixKey(key));
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            log.warn("Valor de contador no es numerico para key: {}", key);
            return 0;
        } catch (Exception e) {
            log.error("Error al obtener contador de Redis: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear() {
        log.warn("clear() no implementado para Redis en produccion por seguridad");
        // En produccion, usar SCAN + DELETE con patron para evitar bloquear Redis
    }

    @Override
    public boolean isAvailable() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.debug("Redis no disponible: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Redis";
    }

    /**
     * Excepcion especifica para errores de cache.
     */
    public static class CacheException extends RuntimeException {
        public CacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
