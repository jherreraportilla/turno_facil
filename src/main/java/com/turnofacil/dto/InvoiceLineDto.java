package com.turnofacil.dto;

import com.turnofacil.model.InvoiceLine;

import java.math.BigDecimal;

public record InvoiceLineDto(
    Long id,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountPercent,
    BigDecimal discountAmount,
    BigDecimal lineTotal,
    Long serviceId,
    Integer lineOrder
) {
    public static InvoiceLineDto fromEntity(InvoiceLine entity) {
        if (entity == null) return null;

        return new InvoiceLineDto(
            entity.getId(),
            entity.getDescription(),
            entity.getQuantity(),
            entity.getUnitPrice(),
            entity.getDiscountPercent(),
            entity.getDiscountAmount(),
            entity.getLineTotal(),
            entity.getService() != null ? entity.getService().getId() : null,
            entity.getLineOrder()
        );
    }

    /**
     * Crea una entidad desde el DTO
     */
    public InvoiceLine toEntity() {
        InvoiceLine line = new InvoiceLine();
        line.setDescription(this.description);
        line.setQuantity(this.quantity != null ? this.quantity : BigDecimal.ONE);
        line.setUnitPrice(this.unitPrice != null ? this.unitPrice : BigDecimal.ZERO);
        line.setDiscountPercent(this.discountPercent != null ? this.discountPercent : BigDecimal.ZERO);
        line.setLineOrder(this.lineOrder != null ? this.lineOrder : 1);
        return line;
    }
}
