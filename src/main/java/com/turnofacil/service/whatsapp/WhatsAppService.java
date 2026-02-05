package com.turnofacil.service.whatsapp;

import java.util.concurrent.CompletableFuture;

/**
 * Interface para el servicio de WhatsApp.
 * Permite intercambiar entre implementaciones (real API vs mock).
 */
public interface WhatsAppService {

    /**
     * Envia un mensaje de texto simple.
     *
     * @param phoneNumber Numero de telefono en formato internacional (ej: +34612345678)
     * @param message Contenido del mensaje
     * @return Resultado del envio
     */
    SendResult sendMessage(String phoneNumber, String message);

    /**
     * Envia un mensaje de texto de forma asincrona.
     */
    CompletableFuture<SendResult> sendMessageAsync(String phoneNumber, String message);

    /**
     * Envia un mensaje usando una plantilla predefinida.
     *
     * @param phoneNumber Numero de telefono
     * @param templateName Nombre de la plantilla registrada en WhatsApp Business
     * @param parameters Parametros para la plantilla
     * @return Resultado del envio
     */
    SendResult sendTemplate(String phoneNumber, String templateName, String... parameters);

    /**
     * Envia recordatorio de cita.
     *
     * @param phoneNumber Numero del cliente
     * @param businessName Nombre del negocio
     * @param serviceName Nombre del servicio
     * @param dateTime Fecha y hora formateada
     * @return Resultado del envio
     */
    SendResult sendAppointmentReminder(String phoneNumber, String businessName,
                                       String serviceName, String dateTime);

    /**
     * Envia confirmacion de cita.
     */
    SendResult sendAppointmentConfirmation(String phoneNumber, String businessName,
                                           String serviceName, String dateTime,
                                           String confirmationCode);

    /**
     * Envia notificacion de cancelacion.
     */
    SendResult sendCancellationNotice(String phoneNumber, String businessName,
                                      String serviceName, String dateTime);

    /**
     * Verifica si el servicio esta disponible y configurado.
     */
    boolean isAvailable();

    /**
     * Obtiene el nombre del proveedor.
     */
    String getProviderName();

    /**
     * Resultado del envio de mensaje.
     */
    record SendResult(
            boolean success,
            String messageId,
            String errorMessage
    ) {
        public static SendResult success(String messageId) {
            return new SendResult(true, messageId, null);
        }

        public static SendResult failure(String errorMessage) {
            return new SendResult(false, null, errorMessage);
        }
    }
}
