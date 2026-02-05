package com.turnofacil.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion de cache en memoria.
 * Usado como fallback cuando Redis no esta disponible.
 * NO es distribuido - solo funciona para una instancia.
 */
public class InMemoryCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCacheService.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Instant> counterExpiry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public InMemoryCacheService() {
        // Limpieza periodica cada 5 minutos
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
        log.info("InMemoryCacheService inicializado (fallback - no distribuido)");
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        Instant expiry = ttl != null ? Instant.now().plus(ttl) : null;
        cache.put(key, new CacheEntry(value, expiry));
    }

    @Override
    public void set(String key, Object value) {
        set(key, value, null);
    }

    @Override
    public Optional<Object> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).map(v -> (T) v);
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
        counters.remove(key);
        counterExpiry.remove(key);
    }

    @Override
    public boolean exists(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return entry != null || counters.containsKey(key);
    }

    @Override
    public long increment(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    @Override
    public long increment(String key, Duration ttl) {
        AtomicLong counter = counters.computeIfAbsent(key, k -> {
            counterExpiry.put(k, Instant.now().plus(ttl));
            return new AtomicLong(0);
        });

        // Verificar si expiro
        Instant expiry = counterExpiry.get(key);
        if (expiry != null && Instant.now().isAfter(expiry)) {
            counter.set(0);
            counterExpiry.put(key, Instant.now().plus(ttl));
        }

        return counter.incrementAndGet();
    }

    @Override
    public long getCounter(String key) {
        AtomicLong counter = counters.get(key);
        if (counter == null) {
            return 0;
        }

        // Verificar si expiro
        Instant expiry = counterExpiry.get(key);
        if (expiry != null && Instant.now().isAfter(expiry)) {
            counters.remove(key);
            counterExpiry.remove(key);
            return 0;
        }

        return counter.get();
    }

    @Override
    public void clear() {
        cache.clear();
        counters.clear();
        counterExpiry.clear();
        log.info("Cache en memoria limpiado");
    }

    @Override
    public boolean isAvailable() {
        return true; // Siempre disponible
    }

    @Override
    public String getProviderName() {
        return "InMemory";
    }

    private void cleanup() {
        Instant now = Instant.now();

        // Limpiar cache entries expirados
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Limpiar counters expirados
        counterExpiry.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                counters.remove(entry.getKey());
                return true;
            }
            return false;
        });

        log.debug("Cache cleanup: {} entries, {} counters", cache.size(), counters.size());
    }

    private static class CacheEntry {
        final Object value;
        final Instant expiry;

        CacheEntry(Object value, Instant expiry) {
            this.value = value;
            this.expiry = expiry;
        }

        boolean isExpired() {
            return expiry != null && Instant.now().isAfter(expiry);
        }
    }
}
