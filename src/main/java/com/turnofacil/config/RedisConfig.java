package com.turnofacil.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuracion de Redis para cache y rate limiting distribuido.
 * Si Redis no esta disponible, los servicios usan fallback a memoria.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.enabled:false}")
    private boolean redisEnabled;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);

        log.info("Redis configurado: {}:{} (enabled: {})", redisHost, redisPort, redisEnabled);

        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Indica si Redis esta habilitado y configurado.
     */
    @Bean
    public Boolean redisEnabled() {
        return redisEnabled;
    }
}
