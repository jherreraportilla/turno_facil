package com.turnofacil.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementacion real del servicio de WhatsApp usando la API de WhatsApp Business.
 * Requiere configurar las credenciales de Meta/Facebook.
 *
 * Documentacion: https://developers.facebook.com/docs/whatsapp/cloud-api
 */
public class WhatsAppBusinessService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBusinessService.class);
    private static final String API_URL = "https://graph.facebook.com/v18.0";

    private final String phoneNumberId;
    private final String accessToken;
    private final HttpClient httpClient;
    private final boolean enabled;

    public WhatsAppBusinessService(String phoneNumberId, String accessToken) {
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.enabled = phoneNumberId != null && !phoneNumberId.isBlank()
                && accessToken != null && !accessToken.isBlank();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (enabled) {
            log.info("WhatsAppBusinessService inicializado con phone ID: {}",
                    maskString(phoneNumberId));
        } else {
            log.warn("WhatsAppBusinessService: credenciales no configuradas, servicio deshabilitado");
        }
    }

    @Override
    public SendResult sendMessage(String phoneNumber, String message) {
        if (!enabled) {
            log.debug("WhatsApp deshabilitado, mensaje no enviado a {}", phoneNumber);
            return SendResult.failure("WhatsApp no configurado");
        }

        try {
            String cleanPhone = cleanPhoneNumber(phoneNumber);
            String requestBody = buildTextMessagePayload(cleanPhone, message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/" + phoneNumberId + "/messages"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String messageId = extractMessageId(response.body());
                log.info("Mensaje WhatsApp enviado a {}, ID: {}", cleanPhone, messageId);
                return SendResult.success(messageId);
            } else {
                log.error("Error enviando WhatsApp a {}: {} - {}",
                        cleanPhone, response.statusCode(), response.body());
                return SendResult.failure("Error API: " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Excepcion enviando WhatsApp a {}: {}", phoneNumber, e.getMessage());
            return SendResult.failure(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<SendResult> sendMessageAsync(String phoneNumber, String message) {
        if (!enabled) {
            return CompletableFuture.completedFuture(
                    SendResult.failure("WhatsApp no configurado"));
        }

        return CompletableFuture.supplyAsync(() -> sendMessage(phoneNumber, message));
    }

    @Override
    public SendResult sendTemplate(String phoneNumber, String templateName, String... parameters) {
        if (!enabled) {
            return SendResult.failure("WhatsApp no configurado");
        }

        try {
            String cleanPhone = cleanPhoneNumber(phoneNumber);
            String requestBody = buildTemplatePayload(cleanPhone, templateName, parameters);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/" + phoneNumberId + "/messages"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String messageId = extractMessageId(response.body());
                log.info("Template WhatsApp '{}' enviado a {}, ID: {}",
                        templateName, cleanPhone, messageId);
                return SendResult.success(messageId);
            } else {
                log.error("Error enviando template WhatsApp: {} - {}",
                        response.statusCode(), response.body());
                return SendResult.failure("Error API: " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Excepcion enviando template WhatsApp: {}", e.getMessage());
            return SendResult.failure(e.getMessage());
        }
    }

    @Override
    public SendResult sendAppointmentReminder(String phoneNumber, String businessName,
                                               String serviceName, String dateTime) {
        // Intentar usar template primero (requiere configurar en Meta Business)
        // Fallback a mensaje de texto si no hay template
        String message = String.format(
                "🔔 *Recordatorio de Cita*\n\n" +
                "Tienes una cita programada:\n" +
                "📍 *%s*\n" +
                "💇 %s\n" +
                "📅 %s\n\n" +
                "Si necesitas cancelar o reprogramar, contactanos con anticipacion.",
                businessName, serviceName, dateTime
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public SendResult sendAppointmentConfirmation(String phoneNumber, String businessName,
                                                   String serviceName, String dateTime,
                                                   String confirmationCode) {
        String message = String.format(
                "✅ *Cita Confirmada*\n\n" +
                "Tu cita ha sido reservada:\n" +
                "📍 *%s*\n" +
                "💇 %s\n" +
                "📅 %s\n\n" +
                "🔑 Codigo: *%s*\n\n" +
                "¡Te esperamos!",
                businessName, serviceName, dateTime, confirmationCode
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public SendResult sendCancellationNotice(String phoneNumber, String businessName,
                                              String serviceName, String dateTime) {
        String message = String.format(
                "❌ *Cita Cancelada*\n\n" +
                "Tu cita ha sido cancelada:\n" +
                "📍 *%s*\n" +
                "💇 %s\n" +
                "📅 %s\n\n" +
                "Puedes reservar una nueva cita cuando lo desees.",
                businessName, serviceName, dateTime
        );
        return sendMessage(phoneNumber, message);
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public String getProviderName() {
        return "WhatsApp Business API";
    }

    // === Metodos auxiliares ===

    private String buildTextMessagePayload(String phoneNumber, String message) {
        return String.format("""
            {
                "messaging_product": "whatsapp",
                "recipient_type": "individual",
                "to": "%s",
                "type": "text",
                "text": {
                    "preview_url": false,
                    "body": "%s"
                }
            }
            """,
            phoneNumber,
            escapeJson(message)
        );
    }

    private String buildTemplatePayload(String phoneNumber, String templateName,
                                        String[] parameters) {
        StringBuilder components = new StringBuilder();
        if (parameters.length > 0) {
            components.append("""
                "components": [{
                    "type": "body",
                    "parameters": [
                """);
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) components.append(",");
                components.append(String.format("""
                    {"type": "text", "text": "%s"}
                    """, escapeJson(parameters[i])));
            }
            components.append("]}]");
        }

        return String.format("""
            {
                "messaging_product": "whatsapp",
                "to": "%s",
                "type": "template",
                "template": {
                    "name": "%s",
                    "language": {"code": "es"}
                    %s
                }
            }
            """,
            phoneNumber,
            templateName,
            components.length() > 0 ? "," + components : ""
        );
    }

    private String extractMessageId(String responseBody) {
        // Parseo simple del response JSON
        // En produccion usar una libreria JSON como Jackson
        try {
            int start = responseBody.indexOf("\"id\":\"") + 6;
            int end = responseBody.indexOf("\"", start);
            if (start > 5 && end > start) {
                return responseBody.substring(start, end);
            }
        } catch (Exception e) {
            log.debug("No se pudo extraer message ID del response");
        }
        return "wa_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String cleanPhoneNumber(String phoneNumber) {
        // Remover espacios, guiones y asegurar formato internacional
        String clean = phoneNumber.replaceAll("[\\s\\-()]", "");
        if (!clean.startsWith("+")) {
            // Asumir codigo de pais si no tiene +
            clean = "+" + clean;
        }
        return clean;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String maskString(String str) {
        if (str == null || str.length() < 6) return "***";
        return str.substring(0, 3) + "***" + str.substring(str.length() - 3);
    }
}
