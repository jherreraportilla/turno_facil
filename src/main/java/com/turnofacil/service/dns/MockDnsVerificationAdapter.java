package com.turnofacil.service.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación mock de verificación DNS para desarrollo y testing.
 *
 * Simula registros DNS configurados programáticamente.
 * En desarrollo, permite verificar dominios de prueba sin necesidad de DNS real.
 */
@Component
@ConditionalOnProperty(name = "app.dns.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockDnsVerificationAdapter implements DnsVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(MockDnsVerificationAdapter.class);

    // Registros TXT simulados: domain -> token
    private final Map<String, String> mockTxtRecords = new ConcurrentHashMap<>();

    // Registros CNAME simulados: domain -> target
    private final Map<String, String> mockCnameRecords = new ConcurrentHashMap<>();

    public MockDnsVerificationAdapter() {
        log.info("DNS Verification: Usando adaptador MOCK (desarrollo)");
    }

    @Override
    public VerificationResult verifyTxtRecord(String domain, String recordName, String expectedValue) {
        log.debug("Mock DNS: Verificando TXT {} = {}", recordName, expectedValue);

        // Permitir tokens especiales para testing
        if (expectedValue.contains("test-verified")) {
            log.debug("Mock DNS: Token de test detectado, verificación exitosa");
            return VerificationResult.success();
        }

        // Buscar en registros mock configurados
        String configuredValue = mockTxtRecords.get(recordName.toLowerCase());
        if (configuredValue != null && configuredValue.equals(expectedValue)) {
            return VerificationResult.success();
        }

        // En modo desarrollo, no verificar (simular que falla hasta que se configure)
        return VerificationResult.failed("Registro TXT no encontrado. " +
                "En desarrollo, usa configureMockTxtRecord() para simular.");
    }

    @Override
    public VerificationResult verifyCnameRecord(String domain, String expectedTarget) {
        log.debug("Mock DNS: Verificando CNAME {} -> {}", domain, expectedTarget);

        // Buscar en registros mock configurados
        String configuredTarget = mockCnameRecords.get(domain.toLowerCase());
        if (configuredTarget != null && configuredTarget.equalsIgnoreCase(expectedTarget)) {
            return VerificationResult.success();
        }

        // En desarrollo, simular que CNAME está configurado si hay TXT válido
        // (asumimos que si configuraron TXT, también configurarán CNAME)
        if (mockTxtRecords.keySet().stream().anyMatch(k -> k.endsWith("." + domain.toLowerCase()))) {
            return VerificationResult.success();
        }

        return VerificationResult.failed("Registro CNAME no encontrado apuntando a " + expectedTarget);
    }

    /**
     * Configura un registro TXT mock para testing.
     */
    public void configureMockTxtRecord(String recordName, String value) {
        mockTxtRecords.put(recordName.toLowerCase(), value);
        log.info("Mock DNS: Configurado TXT {} = {}", recordName, value);
    }

    /**
     * Configura un registro CNAME mock para testing.
     */
    public void configureMockCnameRecord(String domain, String target) {
        mockCnameRecords.put(domain.toLowerCase(), target.toLowerCase());
        log.info("Mock DNS: Configurado CNAME {} -> {}", domain, target);
    }

    /**
     * Limpia todos los registros mock (útil para tests).
     */
    public void clearMockRecords() {
        mockTxtRecords.clear();
        mockCnameRecords.clear();
    }

    /**
     * Configura automáticamente los registros para un dominio.
     * Útil para simular que el usuario ya configuró su DNS.
     */
    public void simulateDnsConfigured(String domain, String verificationToken, String cnameTarget) {
        String txtRecordName = "_turnofacil-verify." + domain.toLowerCase();
        configureMockTxtRecord(txtRecordName, verificationToken);
        configureMockCnameRecord(domain, cnameTarget);
    }
}
