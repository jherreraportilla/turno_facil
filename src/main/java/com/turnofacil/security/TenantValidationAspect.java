package com.turnofacil.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspecto que loguea warnings cuando se accede a repositorios con métodos
 * que reciben businessId y este no coincide con el TenantContext.
 * Actúa como red de seguridad contra data leaks entre tenants.
 */
@Aspect
@Component
public class TenantValidationAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantValidationAspect.class);

    @Before("execution(* com.turnofacil.repository.*.findByBusinessId(..)) || " +
            "execution(* com.turnofacil.repository.*.findByDateAndBusinessIdOrderByTimeAsc(..)) || " +
            "execution(* com.turnofacil.repository.*.findByDateBetweenAndBusinessId(..)) || " +
            "execution(* com.turnofacil.repository.*.findActiveAppointmentsByDateAndBusiness(..)) || " +
            "execution(* com.turnofacil.repository.*.findByClientSearch(..))")
    public void validateTenantOnBusinessIdQuery(JoinPoint joinPoint) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // No hay tenant en contexto (puede ser scheduler, etc.)
            return;
        }

        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Long businessId && !businessId.equals(tenantId)) {
                log.warn("TENANT MISMATCH: método {} llamado con businessId={} pero TenantContext={}",
                        joinPoint.getSignature().getName(), businessId, tenantId);
            }
        }
    }
}
