package com.turnofacil.service.dns;

/**
 * Puerto para verificación de registros DNS.
 * Permite abstraer la infraestructura de DNS del dominio de negocio.
 *
 * Implementaciones:
 * - MockDnsVerificationAdapter: Para desarrollo/testing
 * - RealDnsVerificationAdapter: Para producción (usa javax.naming)
 */
public interface DnsVerificationPort {

    /**
     * Verifica que exista un registro TXT con el valor esperado.
     *
     * @param domain      El dominio a verificar (ej: "mi-negocio.com")
     * @param recordName  Nombre del registro TXT (ej: "_turnofacil-verify.mi-negocio.com")
     * @param expectedValue Valor esperado del registro
     * @return Resultado de la verificación
     */
    VerificationResult verifyTxtRecord(String domain, String recordName, String expectedValue);

    /**
     * Verifica que exista un registro CNAME apuntando al target esperado.
     *
     * @param domain       El dominio a verificar
     * @param expectedTarget El target esperado del CNAME (ej: "custom.turnofacil.com")
     * @return Resultado de la verificación
     */
    VerificationResult verifyCnameRecord(String domain, String expectedTarget);

    /**
     * Resultado de una verificación DNS.
     */
    record VerificationResult(
            boolean verified,
            String message
    ) {
        public static VerificationResult success() {
            return new VerificationResult(true, "Verificación exitosa");
        }

        public static VerificationResult failed(String reason) {
            return new VerificationResult(false, reason);
        }
    }
}
