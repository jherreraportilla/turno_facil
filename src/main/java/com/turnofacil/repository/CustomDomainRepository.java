package com.turnofacil.repository;

import com.turnofacil.model.CustomDomain;
import com.turnofacil.model.enums.DomainStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

    Optional<CustomDomain> findByDomain(String domain);

    Optional<CustomDomain> findByDomainIgnoreCase(String domain);

    List<CustomDomain> findByBusinessId(Long businessId);

    Optional<CustomDomain> findByBusinessIdAndStatus(Long businessId, DomainStatus status);

    boolean existsByDomain(String domain);

    boolean existsByDomainIgnoreCase(String domain);

    List<CustomDomain> findByStatus(DomainStatus status);

    /**
     * Busca dominios que necesitan reverificación (para cron job).
     */
    List<CustomDomain> findByStatusIn(List<DomainStatus> statuses);
}
