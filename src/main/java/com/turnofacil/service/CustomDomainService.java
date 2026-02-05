package com.turnofacil.service;

import com.turnofacil.model.CustomDomain;
import com.turnofacil.model.User;
import com.turnofacil.repository.CustomDomainRepository;
import com.turnofacil.service.dns.DnsVerificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para gestión de dominios personalizados.
 * Permite a los negocios configurar su propio dominio para la página de reservas.
 */
@Service
public class CustomDomainService {

    private static final Logger log = LoggerFactory.getLogger(CustomDomainService.class);

    private final CustomDomainRepository domainRepository;
    private final DnsVerificationPort dnsVerifier;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.custom-domain.verification-record:_turnofacil-verify}")
    private String verificationRecordName;

    @Value("${app.custom-domain.cname-target:custom.turnofacil.com}")
    private String cnameTarget;

    public CustomDomainService(CustomDomainRepository domainRepository,
                               DnsVerificationPort dnsVerifier) {
        this.domainRepository = domainRepository;
        this.dnsVerifier = dnsVerifier;
    }

    /**
     * Registra un nuevo dominio personalizado.
     */
    @Transactional
    public CustomDomain registerDomain(User business, String domain) {
        String normalizedDomain = normalizeDomain(domain);

        if (domainRepository.existsByDomainIgnoreCase(normalizedDomain)) {
            throw new DomainAlreadyExistsException("El dominio ya está registrado");
        }

        CustomDomain customDomain = CustomDomain.createPending(
                business,
                normalizedDomain,
                generateVerificationToken()
        );

        CustomDomain saved = domainRepository.save(customDomain);
        log.info("Dominio {} registrado para negocio {} (ID: {})",
                normalizedDomain, business.getName(), business.getId());

        return saved;
    }

    /**
     * Obtiene los dominios de un negocio.
     */
    @Transactional(readOnly = true)
    public List<CustomDomain> getDomainsForBusiness(Long businessId) {
        return domainRepository.findByBusinessId(businessId);
    }

    /**
     * Obtiene un dominio por su ID, verificando pertenencia al negocio.
     */
    @Transactional(readOnly = true)
    public Optional<CustomDomain> getDomainForBusiness(Long domainId, Long businessId) {
        return domainRepository.findById(domainId)
                .filter(d -> d.getBusiness().getId().equals(businessId));
    }

    /**
     * Obtiene las instrucciones de configuración DNS.
     */
    public DnsInstructions getDnsInstructions(CustomDomain domain) {
        String txtRecordName = verificationRecordName + "." + domain.getDomain();
        String txtRecordValue = domain.getVerificationToken();
        String cnameRecordName = domain.getDomain();

        return new DnsInstructions(
                txtRecordName,
                txtRecordValue,
                cnameRecordName,
                cnameTarget
        );
    }

    /**
     * Intenta verificar un dominio.
     * Delega la verificación DNS real al puerto DnsVerificationPort.
     */
    @Transactional
    public VerificationResult verifyDomain(Long domainId, Long businessId) {
        CustomDomain domain = domainRepository.findById(domainId)
                .filter(d -> d.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado"));

        // Registrar intento de verificación
        domain.recordVerificationAttempt();

        // Verificar registro TXT usando el puerto
        String txtRecordName = verificationRecordName + "." + domain.getDomain();
        DnsVerificationPort.VerificationResult txtResult = dnsVerifier.verifyTxtRecord(
                domain.getDomain(),
                txtRecordName,
                domain.getVerificationToken()
        );

        if (!txtResult.verified()) {
            domain.markVerificationFailed("No se encontró el registro TXT de verificación");
            domainRepository.save(domain);
            return new VerificationResult(false, "Registro TXT no encontrado. " +
                    "Asegúrate de haber configurado el registro DNS correctamente y espera unos minutos para la propagación.");
        }

        // Verificar CNAME usando el puerto
        DnsVerificationPort.VerificationResult cnameResult = dnsVerifier.verifyCnameRecord(
                domain.getDomain(),
                cnameTarget
        );

        if (!cnameResult.verified()) {
            domain.markVerificationFailed("No se encontró el registro CNAME apuntando a " + cnameTarget);
            domainRepository.save(domain);
            return new VerificationResult(false, "Registro CNAME no encontrado. " +
                    "Configura un registro CNAME apuntando a " + cnameTarget);
        }

        // Dominio verificado exitosamente
        domain.markVerified();
        domainRepository.save(domain);

        log.info("Dominio {} verificado exitosamente", domain.getDomain());

        return new VerificationResult(true, "Dominio verificado correctamente");
    }

    /**
     * Elimina un dominio.
     */
    @Transactional
    public void deleteDomain(Long domainId, Long businessId) {
        CustomDomain domain = domainRepository.findById(domainId)
                .filter(d -> d.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado"));

        domainRepository.delete(domain);
        log.info("Dominio {} eliminado", domain.getDomain());
    }

    /**
     * Busca un negocio por su dominio personalizado.
     */
    @Transactional(readOnly = true)
    public Optional<User> findBusinessByDomain(String domain) {
        return domainRepository.findByDomainIgnoreCase(normalizeDomain(domain))
                .filter(CustomDomain::isActive)
                .map(CustomDomain::getBusiness);
    }

    // === Métodos auxiliares ===

    private String normalizeDomain(String domain) {
        String normalized = domain.toLowerCase().trim();
        // Remover protocolo si existe
        normalized = normalized.replaceAll("^https?://", "");
        // Remover trailing slash y path
        normalized = normalized.split("/")[0];
        // Remover www. si existe
        normalized = normalized.replaceAll("^www\\.", "");
        return normalized;
    }

    private String generateVerificationToken() {
        return "turnofacil-verify-" + UUID.randomUUID().toString().substring(0, 16);
    }

    // === Records para respuestas ===

    public record DnsInstructions(
            String txtRecordName,
            String txtRecordValue,
            String cnameRecordName,
            String cnameRecordTarget
    ) {}

    public record VerificationResult(
            boolean success,
            String message
    ) {}

    public static class DomainAlreadyExistsException extends RuntimeException {
        public DomainAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class DomainNotFoundException extends RuntimeException {
        public DomainNotFoundException(String message) {
            super(message);
        }
    }
}
