package com.turnofacil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request para crear una factura manualmente (sin cita asociada)
 */
public record CreateInvoiceRequest(
    // Cliente
    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 100)
    String clientName,

    @Size(max = 120)
    String clientEmail,

    @Size(max = 20)
    String clientPhone,

    @Size(max = 20)
    String clientTaxId,

    @Size(max = 500)
    String clientAddress,

    // Fechas
    LocalDate issueDate,
    LocalDate dueDate,

    @NotNull(message = "La fecha del servicio es obligatoria")
    LocalDate serviceDate,

    // Líneas
    @NotNull(message = "Debe incluir al menos una línea")
    @Size(min = 1, message = "Debe incluir al menos una línea")
    List<LineRequest> lines,

    // Descuento global
    BigDecimal discountTotal,

    // Notas
    @Size(max = 1000)
    String notes
) {
    public record LineRequest(
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 500)
        String description,

        BigDecimal quantity,

        @NotNull(message = "El precio unitario es obligatorio")
        BigDecimal unitPrice,

        BigDecimal discountPercent,

        Long serviceId
    ) {}
}
