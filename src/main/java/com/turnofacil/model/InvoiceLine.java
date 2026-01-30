package com.turnofacil.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Línea de factura - INMUTABLE tras emisión de la factura.
 *
 * Contiene el snapshot del servicio/producto en el momento de la facturación.
 */
@Entity
@Table(name = "invoice_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // ==================== SNAPSHOT DEL CONCEPTO (INMUTABLE) ====================

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 10, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_amount", updatable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    // ==================== REFERENCIA OPCIONAL ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service; // Referencia informativa, no se usa para cálculos

    // ==================== ORDEN ====================

    @Column(name = "line_order", nullable = false)
    private Integer lineOrder = 1;

    // ==================== MÉTODOS ====================

    /**
     * Calcula el total de la línea antes de guardar
     */
    @PrePersist
    @PreUpdate
    protected void calculateTotal() {
        if (quantity == null) quantity = BigDecimal.ONE;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (discountPercent == null) discountPercent = BigDecimal.ZERO;

        BigDecimal grossTotal = quantity.multiply(unitPrice);

        if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = grossTotal.multiply(discountPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            discountAmount = BigDecimal.ZERO;
        }

        lineTotal = grossTotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Crea una línea desde un servicio
     */
    public static InvoiceLine fromService(Service service, BigDecimal quantity) {
        InvoiceLine line = new InvoiceLine();
        line.setService(service);
        line.setDescription(service.getName() +
            (service.getDescription() != null ? " - " + service.getDescription() : ""));
        line.setQuantity(quantity != null ? quantity : BigDecimal.ONE);
        line.setUnitPrice(service.getPrice() != null ? service.getPrice() : BigDecimal.ZERO);
        line.setDiscountPercent(BigDecimal.ZERO);
        line.calculateTotal();
        return line;
    }

    /**
     * Crea una línea desde los datos de una cita (usando snapshot)
     */
    public static InvoiceLine fromAppointment(Appointment appointment) {
        InvoiceLine line = new InvoiceLine();
        line.setDescription(appointment.getServiceName() != null
            ? appointment.getServiceName()
            : "Servicio");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(appointment.getServicePrice() != null
            ? appointment.getServicePrice()
            : BigDecimal.ZERO);
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setService(appointment.getService()); // Referencia informativa
        line.calculateTotal();
        return line;
    }
}
