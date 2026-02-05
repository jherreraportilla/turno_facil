package com.turnofacil.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementacion mock del servicio de WhatsApp.
 * Usado para desarrollo y testing sin API key real.
 * Solo logea los mensajes que se enviarian.
 */
public class MockWhatsAppService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppService.class);

    public MockWhatsAppService() {
        log.info("MockWhatsAppService inicializado (modo desarrollo - mensajes solo se logean)");
    }

    @Override
    public SendResult sendMessage(String phoneNumber, String message) {
        String messageId = generateMockMessageId();
        log.info("[MOCK WhatsApp] Enviando mensaje a {}: {}", phoneNumber, truncate(message, 100));
        log.debug("[MOCK WhatsApp] Mensaje completo: {}", message);
        return SendResult.success(messageId);
    }

    @Override
    public CompletableFuture<SendResult> sendMessageAsync(String phoneNumber, String message) {
        return CompletableFuture.supplyAsync(() -> sendMessage(phoneNumber, message));
    }

    @Override
    public SendResult sendTemplate(String phoneNumber, String templateName, String... parameters) {
        String messageId = generateMockMessageId();
        log.info("[MOCK WhatsApp] Enviando template '{}' a {} con {} parametros",
                templateName, phoneNumber, parameters.length);
        return SendResult.success(messageId);
    }

    @Override
    public SendResult sendAppointmentReminder(String phoneNumber, String businessName,
                                               String serviceName, String dateTime) {
        String message = String.format(
                "Recordatorio: Tienes una cita en %s para %s el %s. " +
                "Si necesitas cancelar o reprogramar, contactanos.",
                businessName, serviceName, dateTime
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public SendResult sendAppointmentConfirmation(String phoneNumber, String businessName,
                                                   String serviceName, String dateTime,
                                                   String confirmationCode) {
        String message = String.format(
                "¡Cita confirmada! %s en %s el %s. " +
                "Tu codigo de confirmacion es: %s",
                serviceName, businessName, dateTime, confirmationCode
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public SendResult sendCancellationNotice(String phoneNumber, String businessName,
                                              String serviceName, String dateTime) {
        String message = String.format(
                "Tu cita en %s para %s el %s ha sido cancelada. " +
                "Puedes reservar una nueva cita cuando gustes.",
                businessName, serviceName, dateTime
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public boolean isAvailable() {
        return true; // Mock siempre disponible
    }

    @Override
    public String getProviderName() {
        return "Mock";
    }

    private String generateMockMessageId() {
        return "mock_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
