package com.turnofacil.repository;

import com.turnofacil.model.InvoiceAuditLog;
import com.turnofacil.model.enums.InvoiceAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceAuditLogRepository extends JpaRepository<InvoiceAuditLog, Long> {

    List<InvoiceAuditLog> findByInvoiceIdOrderByPerformedAtDesc(Long invoiceId);

    List<InvoiceAuditLog> findByInvoiceIdAndAction(Long invoiceId, InvoiceAuditAction action);

    List<InvoiceAuditLog> findByPerformedByIdAndPerformedAtBetween(
        Long userId, LocalDateTime startDate, LocalDateTime endDate
    );
}
