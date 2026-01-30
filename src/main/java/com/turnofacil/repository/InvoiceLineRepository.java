package com.turnofacil.repository;

import com.turnofacil.model.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceIdOrderByLineOrderAsc(Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);
}
