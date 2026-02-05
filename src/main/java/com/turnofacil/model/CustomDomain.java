package com.turnofacil.model;

import com.turnofacil.model.enums.DomainStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa un dominio personalizado configurado por un negocio.
 * Permite que los negocios usen su propio dominio para la página de reservas.
 */
@Entity
@Table(name = "CUSTOM_DOMAINS")
@Data
@NoArgsConstructor
public class CustomDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User business;

    @Column(name = "DOMAIN", nullable = false, unique = true, length = 255)
    private String domain;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DomainStatus status = DomainStatus.PENDING_VERIFICATION;

    @Column(name = "VERIFICATION_TOKEN", length = 64)
    private String verificationToken;

    @Column(name = "SSL_ENABLED", nullable = false)
    private boolean sslEnabled = false;

    @Column(name = "SSL_CERTIFICATE_EXPIRES")
    private LocalDateTime sslCertificateExpires;

    @Column(name = "LAST_VERIFICATION_ATTEMPT")
    private LocalDateTime lastVerificationAttempt;

    @Column(name = "VERIFICATION_ERROR", length = 500)
    private String verificationError;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // === Factory method ===

    /**
     * Crea un nuevo dominio personalizado pendiente de verificación.
     *
     * @param business el negocio dueño del dominio
     * @param domain el nombre de dominio normalizado
     * @param verificationToken token único para verificación DNS
     * @return nuevo CustomDomain en estado PENDING_VERIFICATION
     */
    public static CustomDomain createPending(User business, String domain, String verificationToken) {
        CustomDomain customDomain = new CustomDomain();
        customDomain.business = business;
        customDomain.domain = domain;
        customDomain.verificationToken = verificationToken;
        customDomain.status = DomainStatus.PENDING_VERIFICATION;
        return customDomain;
    }

    // === Métodos de consulta ===

    /**
     * Verifica si el dominio está activo y puede usarse.
     */
    public boolean isActive() {
        return status == DomainStatus.ACTIVE;
    }

    /**
     * Verifica si el dominio está pendiente de verificación.
     */
    public boolean isPendingVerification() {
        return status == DomainStatus.PENDING_VERIFICATION;
    }

    /**
     * Verifica si la verificación ha fallado.
     */
    public boolean isVerificationFailed() {
        return status == DomainStatus.VERIFICATION_FAILED;
    }

    // === Métodos de comportamiento de dominio ===

    /**
     * Registra un intento de verificación.
     * Debe llamarse antes de intentar verificar el dominio.
     */
    public void recordVerificationAttempt() {
        this.lastVerificationAttempt = LocalDateTime.now();
    }

    /**
     * Marca el dominio como verificado exitosamente.
     * Transición: PENDING_VERIFICATION | VERIFICATION_FAILED → ACTIVE
     */
    public void markVerified() {
        this.status = DomainStatus.ACTIVE;
        this.verifiedAt = LocalDateTime.now();
        this.verificationError = null;
    }

    /**
     * Marca la verificación como fallida con un mensaje de error.
     * Transición: PENDING_VERIFICATION | ACTIVE → VERIFICATION_FAILED
     */
    public void markVerificationFailed(String errorMessage) {
        this.status = DomainStatus.VERIFICATION_FAILED;
        this.verificationError = errorMessage;
    }

    /**
     * Habilita SSL para este dominio.
     * Solo válido si el dominio está activo.
     *
     * @param certificateExpires fecha de expiración del certificado
     * @throws IllegalStateException si el dominio no está activo
     */
    public void enableSsl(LocalDateTime certificateExpires) {
        if (!isActive()) {
            throw new IllegalStateException("No se puede habilitar SSL en un dominio no activo");
        }
        this.sslEnabled = true;
        this.sslCertificateExpires = certificateExpires;
    }

    /**
     * Deshabilita SSL para este dominio.
     */
    public void disableSsl() {
        this.sslEnabled = false;
        this.sslCertificateExpires = null;
    }
}
