package com.turnofacil.service.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Implementación real de verificación DNS para producción.
 * Usa javax.naming para consultar registros DNS.
 */
@Component
@ConditionalOnProperty(name = "app.dns.mock-enabled", havingValue = "false")
public class RealDnsVerificationAdapter implements DnsVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(RealDnsVerificationAdapter.class);
    private static final int DNS_TIMEOUT_MS = 5000;

    public RealDnsVerificationAdapter() {
        log.info("DNS Verification: Usando adaptador REAL (producción)");
    }

    @Override
    public VerificationResult verifyTxtRecord(String domain, String recordName, String expectedValue) {
        log.debug("Real DNS: Verificando TXT {} = {}", recordName, expectedValue);

        try {
            String[] txtRecords = lookupTxtRecords(recordName);

            for (String record : txtRecords) {
                // Los registros TXT vienen con comillas, las removemos
                String cleanRecord = record.replace("\"", "").trim();
                if (cleanRecord.equals(expectedValue)) {
                    log.info("TXT record verificado para {}", domain);
                    return VerificationResult.success();
                }
            }

            log.debug("TXT record no coincide. Esperado: {}, Encontrados: {}",
                    expectedValue, String.join(", ", txtRecords));
            return VerificationResult.failed("Registro TXT no encontrado o no coincide");

        } catch (NamingException e) {
            log.warn("Error al consultar TXT record para {}: {}", recordName, e.getMessage());
            return VerificationResult.failed("No se pudo consultar el registro TXT: " + e.getMessage());
        }
    }

    @Override
    public VerificationResult verifyCnameRecord(String domain, String expectedTarget) {
        log.debug("Real DNS: Verificando CNAME {} -> {}", domain, expectedTarget);

        try {
            String cnameTarget = lookupCnameRecord(domain);

            if (cnameTarget != null) {
                // Normalizar: remover punto final si existe
                String normalizedTarget = cnameTarget.endsWith(".")
                        ? cnameTarget.substring(0, cnameTarget.length() - 1)
                        : cnameTarget;
                String normalizedExpected = expectedTarget.endsWith(".")
                        ? expectedTarget.substring(0, expectedTarget.length() - 1)
                        : expectedTarget;

                if (normalizedTarget.equalsIgnoreCase(normalizedExpected)) {
                    log.info("CNAME record verificado para {}", domain);
                    return VerificationResult.success();
                }

                log.debug("CNAME target no coincide. Esperado: {}, Encontrado: {}",
                        expectedTarget, cnameTarget);
                return VerificationResult.failed("CNAME apunta a " + cnameTarget +
                        " en lugar de " + expectedTarget);
            }

            return VerificationResult.failed("No se encontró registro CNAME");

        } catch (NamingException e) {
            log.warn("Error al consultar CNAME record para {}: {}", domain, e.getMessage());
            return VerificationResult.failed("No se pudo consultar el registro CNAME: " + e.getMessage());
        }
    }

    private String[] lookupTxtRecords(String domain) throws NamingException {
        DirContext ctx = createDnsContext();
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{"TXT"});
            Attribute txtAttr = attrs.get("TXT");

            if (txtAttr == null) {
                return new String[0];
            }

            String[] records = new String[txtAttr.size()];
            NamingEnumeration<?> enumeration = txtAttr.getAll();
            int i = 0;
            while (enumeration.hasMore()) {
                records[i++] = enumeration.next().toString();
            }
            return records;
        } finally {
            ctx.close();
        }
    }

    private String lookupCnameRecord(String domain) throws NamingException {
        DirContext ctx = createDnsContext();
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{"CNAME"});
            Attribute cnameAttr = attrs.get("CNAME");

            if (cnameAttr != null && cnameAttr.size() > 0) {
                return cnameAttr.get().toString();
            }
            return null;
        } finally {
            ctx.close();
        }
    }

    private DirContext createDnsContext() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(DNS_TIMEOUT_MS));
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        return new InitialDirContext(env);
    }
}
