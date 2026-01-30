package com.turnofacil.repository;

import com.turnofacil.model.Invoice;
import com.turnofacil.model.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // ==================== BÚSQUEDAS BÁSICAS ====================

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByBusinessIdOrderByIssueDateDesc(Long businessId);

    Page<Invoice> findByBusinessId(Long businessId, Pageable pageable);

    List<Invoice> findByBusinessIdAndStatus(Long businessId, InvoiceStatus status);

    Optional<Invoice> findByAppointmentId(Long appointmentId);

    // ==================== BÚSQUEDAS POR FECHA ====================

    List<Invoice> findByBusinessIdAndIssueDateBetween(Long businessId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT i FROM Invoice i WHERE i.business.id = :businessId " +
           "AND i.issueDate >= :startDate AND i.issueDate <= :endDate " +
           "AND i.status IN :statuses ORDER BY i.issueDate DESC")
    List<Invoice> findByBusinessAndDateRangeAndStatuses(
        @Param("businessId") Long businessId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("statuses") List<InvoiceStatus> statuses
    );

    // ==================== ESTADÍSTICAS ====================

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.business.id = :businessId AND i.status = :status")
    long countByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i " +
           "WHERE i.business.id = :businessId AND i.status = :status")
    BigDecimal sumTotalByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.total), 0) FROM Invoice i " +
           "WHERE i.business.id = :businessId " +
           "AND i.issueDate >= :startDate AND i.issueDate <= :endDate " +
           "AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumTotalByBusinessIdAndDateRange(
        @Param("businessId") Long businessId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(i.vatAmount), 0) FROM Invoice i " +
           "WHERE i.business.id = :businessId " +
           "AND i.issueDate >= :startDate AND i.issueDate <= :endDate " +
           "AND i.status IN ('ISSUED', 'PAID')")
    BigDecimal sumVatByBusinessIdAndDateRange(
        @Param("businessId") Long businessId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ==================== FACTURAS PENDIENTES DE PAGO ====================

    @Query("SELECT i FROM Invoice i WHERE i.business.id = :businessId " +
           "AND i.status = 'ISSUED' AND (i.dueDate IS NULL OR i.dueDate < :date)")
    List<Invoice> findOverdueInvoices(@Param("businessId") Long businessId, @Param("date") LocalDate date);

    // ==================== BÚSQUEDA POR CLIENTE ====================

    @Query("SELECT i FROM Invoice i WHERE i.business.id = :businessId " +
           "AND (LOWER(i.clientName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(i.clientEmail) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR i.invoiceNumber LIKE CONCAT('%', :search, '%'))")
    List<Invoice> searchByBusinessId(@Param("businessId") Long businessId, @Param("search") String search);

    // ==================== ÚLTIMO NÚMERO ====================

    @Query("SELECT MAX(i.invoiceNumber) FROM Invoice i WHERE i.business.id = :businessId " +
           "AND i.invoiceSeries = :series")
    Optional<String> findLastInvoiceNumber(@Param("businessId") Long businessId, @Param("series") String series);
}
