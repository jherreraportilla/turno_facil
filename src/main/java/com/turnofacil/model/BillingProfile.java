package com.turnofacil.model;

import com.turnofacil.model.enums.VatRegime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Perfil de facturación del negocio.
 * Contiene los datos fiscales necesarios para emitir facturas legales.
 */
@Entity
@Table(name = "billing_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ==================== DATOS FISCALES ====================

    @Column(name = "tax_id", nullable = false, length = 20)
    private String taxId; // NIF/CIF

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName; // Razón social

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "country", nullable = false, length = 2)
    private String country = "ES";

    // ==================== CONFIGURACIÓN FISCAL ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_regime", nullable = false)
    private VatRegime vatRegime = VatRegime.GENERAL;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("21.00");

    // ==================== CONFIGURACIÓN DE FACTURACIÓN ====================

    @Column(name = "invoice_series", nullable = false, length = 10)
    private String invoiceSeries = "TF"; // Serie de facturación

    @Column(name = "next_invoice_number", nullable = false)
    private Long nextInvoiceNumber = 1L;

    @Column(name = "invoice_notes", length = 1000)
    private String invoiceNotes; // Notas que aparecen en todas las facturas

    @Column(name = "payment_terms", length = 500)
    private String paymentTerms; // Condiciones de pago

    // ==================== DATOS BANCARIOS ====================

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "swift_bic", length = 11)
    private String swiftBic;

    // ==================== AUDITORÍA ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== MÉTODOS ÚTILES ====================

    /**
     * Devuelve la dirección fiscal formateada en una línea
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(addressLine1);
        if (addressLine2 != null && !addressLine2.isBlank()) {
            sb.append(", ").append(addressLine2);
        }
        sb.append(", ").append(postalCode);
        sb.append(" ").append(city);
        sb.append(" (").append(province).append(")");
        return sb.toString();
    }

    /**
     * Genera el siguiente número de factura y lo incrementa
     */
    public String generateNextInvoiceNumber() {
        int year = java.time.Year.now().getValue();
        String number = String.format("%s-%d-%05d", invoiceSeries, year, nextInvoiceNumber);
        nextInvoiceNumber++;
        return number;
    }
}
