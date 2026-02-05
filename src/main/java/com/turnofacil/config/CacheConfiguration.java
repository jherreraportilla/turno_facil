package com.turnofacil.config;

import com.turnofacil.service.cache.CacheService;
import com.turnofacil.service.cache.InMemoryCacheService;
import com.turnofacil.service.cache.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuracion del servicio de cache.
 * Selecciona automaticamente entre Redis (si esta habilitado y disponible)
 * o InMemory (fallback).
 */
@Configuration
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Value("${spring.data.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${app.cache.key-prefix:turnofacil:}")
    private String cacheKeyPrefix;

    /**
     * Bean de cache usando Redis.
     * Solo se crea si Redis esta habilitado en configuracion.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
    public CacheService redisCacheService(StringRedisTemplate stringRedisTemplate) {
        log.info("Creando RedisCacheService (Redis habilitado en configuracion)");
        RedisCacheService redisCacheService = new RedisCacheService(
                stringRedisTemplate,
                cacheKeyPrefix
        );

        // Verificar que Redis este realmente disponible
        if (redisCacheService.isAvailable()) {
            log.info("Redis conectado y disponible");
            return redisCacheService;
        } else {
            log.warn("Redis habilitado pero no disponible, usando fallback InMemory");
            return new InMemoryCacheService();
        }
    }

    /**
     * Bean de cache usando InMemory.
     * Se usa como fallback cuando Redis no esta habilitado.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheService inMemoryCacheService() {
        log.info("Creando InMemoryCacheService (Redis no habilitado)");
        return new InMemoryCacheService();
    }
}
