package com.turnofacil.service.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Interface para servicio de cache.
 * Permite intercambiar entre implementaciones (Redis, memoria, etc.)
 */
public interface CacheService {

    /**
     * Guarda un valor en cache con TTL.
     */
    void set(String key, Object value, Duration ttl);

    /**
     * Guarda un valor en cache sin TTL (no expira).
     */
    void set(String key, Object value);

    /**
     * Obtiene un valor del cache.
     */
    Optional<Object> get(String key);

    /**
     * Obtiene un valor del cache con tipo especifico.
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Elimina un valor del cache.
     */
    void delete(String key);

    /**
     * Verifica si una key existe en cache.
     */
    boolean exists(String key);

    /**
     * Incrementa un contador atomicamente.
     * Retorna el nuevo valor.
     */
    long increment(String key);

    /**
     * Incrementa un contador atomicamente con TTL.
     * Si la key no existe, se crea con valor 1 y el TTL especificado.
     */
    long increment(String key, Duration ttl);

    /**
     * Obtiene el valor actual de un contador.
     */
    long getCounter(String key);

    /**
     * Limpia todo el cache (usar con cuidado).
     */
    void clear();

    /**
     * Indica si el servicio de cache esta disponible.
     */
    boolean isAvailable();

    /**
     * Retorna el nombre del proveedor de cache (Redis, Memory, etc.)
     */
    String getProviderName();
}
