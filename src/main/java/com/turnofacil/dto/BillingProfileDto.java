package com.turnofacil.dto;

import com.turnofacil.model.BillingProfile;
import com.turnofacil.model.enums.VatRegime;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BillingProfileDto(
    Long id,

    @NotBlank(message = "El NIF/CIF es obligatorio")
    @Size(max = 20, message = "El NIF/CIF no puede exceder 20 caracteres")
    String taxId,

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 200, message = "La razón social no puede exceder 200 caracteres")
    String legalName,

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
    String addressLine1,

    @Size(max = 200)
    String addressLine2,

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(max = 100)
    String city,

    @NotBlank(message = "El código postal es obligatorio")
    @Size(max = 10)
    String postalCode,

    @NotBlank(message = "La provincia es obligatoria")
    @Size(max = 100)
    String province,

    @NotBlank(message = "El país es obligatorio")
    @Size(max = 2)
    String country,

    VatRegime vatRegime,

    @NotNull(message = "El tipo de IVA es obligatorio")
    @DecimalMin(value = "0.00", message = "El IVA no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El IVA no puede exceder 100%")
    BigDecimal vatRate,

    @Size(max = 10)
    String invoiceSeries,

    @Size(max = 1000)
    String invoiceNotes,

    @Size(max = 500)
    String paymentTerms,

    @Size(max = 100)
    String bankName,

    @Size(max = 34)
    String iban,

    @Size(max = 11)
    String swiftBic
) {
    /**
     * Convierte la entidad a DTO
     */
    public static BillingProfileDto fromEntity(BillingProfile entity) {
        if (entity == null) return null;

        return new BillingProfileDto(
            entity.getId(),
            entity.getTaxId(),
            entity.getLegalName(),
            entity.getAddressLine1(),
            entity.getAddressLine2(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getProvince(),
            entity.getCountry(),
            entity.getVatRegime(),
            entity.getVatRate(),
            entity.getInvoiceSeries(),
            entity.getInvoiceNotes(),
            entity.getPaymentTerms(),
            entity.getBankName(),
            entity.getIban(),
            entity.getSwiftBic()
        );
    }

    /**
     * Aplica los valores del DTO a una entidad existente
     */
    public void applyTo(BillingProfile entity) {
        entity.setTaxId(this.taxId);
        entity.setLegalName(this.legalName);
        entity.setAddressLine1(this.addressLine1);
        entity.setAddressLine2(this.addressLine2);
        entity.setCity(this.city);
        entity.setPostalCode(this.postalCode);
        entity.setProvince(this.province);
        entity.setCountry(this.country != null ? this.country : "ES");
        entity.setVatRegime(this.vatRegime != null ? this.vatRegime : VatRegime.GENERAL);
        entity.setVatRate(this.vatRate);
        if (this.invoiceSeries != null) {
            entity.setInvoiceSeries(this.invoiceSeries);
        }
        entity.setInvoiceNotes(this.invoiceNotes);
        entity.setPaymentTerms(this.paymentTerms);
        entity.setBankName(this.bankName);
        entity.setIban(this.iban);
        entity.setSwiftBic(this.swiftBic);
    }
}
