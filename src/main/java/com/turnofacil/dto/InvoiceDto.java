package com.turnofacil.dto;

import com.turnofacil.model.Invoice;
import com.turnofacil.model.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceDto(
    Long id,
    String invoiceNumber,
    String invoiceSeries,
    InvoiceStatus status,

    // Emisor
    String emitterTaxId,
    String emitterLegalName,
    String emitterAddress,
    String emitterCity,
    String emitterPostalCode,
    String emitterProvince,
    String emitterCountry,

    // Receptor
    String clientName,
    String clientEmail,
    String clientPhone,
    String clientTaxId,
    String clientAddress,

    // Totales
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxableBase,
    BigDecimal vatRate,
    BigDecimal vatAmount,
    BigDecimal total,

    // Fechas
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate serviceDate,
    LocalDateTime paidAt,
    LocalDateTime cancelledAt,
    String cancellationReason,

    // Rectificativa
    Long rectifiesInvoiceId,
    String rectifiesInvoiceNumber,

    // Notas
    String notes,
    String paymentTerms,

    // Líneas
    List<InvoiceLineDto> lines,

    // Referencias
    Long appointmentId,
    Long businessId,

    // Metadata
    LocalDateTime createdAt,
    LocalDateTime issuedAt
) {
    public static InvoiceDto fromEntity(Invoice entity) {
        if (entity == null) return null;

        List<InvoiceLineDto> lineDtos = entity.getLines() != null
            ? entity.getLines().stream().map(InvoiceLineDto::fromEntity).toList()
            : List.of();

        return new InvoiceDto(
            entity.getId(),
            entity.getInvoiceNumber(),
            entity.getInvoiceSeries(),
            entity.getStatus(),

            entity.getEmitterTaxId(),
            entity.getEmitterLegalName(),
            entity.getEmitterAddress(),
            entity.getEmitterCity(),
            entity.getEmitterPostalCode(),
            entity.getEmitterProvince(),
            entity.getEmitterCountry(),

            entity.getClientName(),
            entity.getClientEmail(),
            entity.getClientPhone(),
            entity.getClientTaxId(),
            entity.getClientAddress(),

            entity.getSubtotal(),
            entity.getDiscountTotal(),
            entity.getTaxableBase(),
            entity.getVatRate(),
            entity.getVatAmount(),
            entity.getTotal(),

            entity.getIssueDate(),
            entity.getDueDate(),
            entity.getServiceDate(),
            entity.getPaidAt(),
            entity.getCancelledAt(),
            entity.getCancellationReason(),

            entity.getRectifiesInvoice() != null ? entity.getRectifiesInvoice().getId() : null,
            entity.getRectifiesInvoice() != null ? entity.getRectifiesInvoice().getInvoiceNumber() : null,

            entity.getNotes(),
            entity.getPaymentTerms(),

            lineDtos,

            entity.getAppointment() != null ? entity.getAppointment().getId() : null,
            entity.getBusiness() != null ? entity.getBusiness().getId() : null,

            entity.getCreatedAt(),
            entity.getIssuedAt()
        );
    }

    /**
     * DTO simplificado para listados
     */
    public static InvoiceDto summary(Invoice entity) {
        if (entity == null) return null;

        return new InvoiceDto(
            entity.getId(),
            entity.getInvoiceNumber(),
            entity.getInvoiceSeries(),
            entity.getStatus(),
            null, null, null, null, null, null, null,
            entity.getClientName(),
            entity.getClientEmail(),
            null, null, null,
            null, null, null, null, null,
            entity.getTotal(),
            entity.getIssueDate(),
            entity.getDueDate(),
            null, entity.getPaidAt(), null, null,
            null, null, null, null, null,
            entity.getAppointment() != null ? entity.getAppointment().getId() : null,
            null,
            entity.getCreatedAt(),
            entity.getIssuedAt()
        );
    }
}
