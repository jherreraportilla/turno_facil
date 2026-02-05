package com.turnofacil.config;

import com.turnofacil.service.whatsapp.MockWhatsAppService;
import com.turnofacil.service.whatsapp.WhatsAppBusinessService;
import com.turnofacil.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuracion del servicio de WhatsApp.
 * Selecciona automaticamente entre la API real o el mock segun configuracion.
 */
@Configuration
public class WhatsAppConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfiguration.class);

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    /**
     * Bean de WhatsApp usando la API real de Meta.
     * Solo se crea si WhatsApp esta habilitado en configuracion.
     */
    @Bean
    @ConditionalOnProperty(name = "whatsapp.enabled", havingValue = "true")
    public WhatsAppService whatsAppBusinessService() {
        log.info("Creando WhatsAppBusinessService (WhatsApp habilitado)");

        WhatsAppBusinessService service = new WhatsAppBusinessService(
                phoneNumberId,
                accessToken
        );

        if (service.isAvailable()) {
            log.info("WhatsApp Business API conectado y disponible");
        } else {
            log.warn("WhatsApp habilitado pero credenciales no configuradas");
        }

        return service;
    }

    /**
     * Bean mock de WhatsApp para desarrollo.
     * Se usa cuando WhatsApp no esta habilitado.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "whatsapp.enabled", havingValue = "false", matchIfMissing = true)
    public WhatsAppService mockWhatsAppService() {
        log.info("Creando MockWhatsAppService (WhatsApp no habilitado - modo desarrollo)");
        return new MockWhatsAppService();
    }
}
