package com.turnofacil.service;

import com.turnofacil.dto.CreateInvoiceRequest;
import com.turnofacil.dto.InvoiceDto;
import com.turnofacil.model.*;
import com.turnofacil.model.enums.InvoiceAuditAction;
import com.turnofacil.model.enums.InvoiceStatus;
import com.turnofacil.repository.*;
import com.turnofacil.exception.AccessDeniedException;
import com.turnofacil.exception.BusinessValidationException;
import com.turnofacil.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepo;
    private final InvoiceAuditLogRepository auditLogRepo;
    private final BillingProfileRepository billingProfileRepo;
    private final AppointmentRepository appointmentRepo;
    private final ServiceRepository serviceRepo;
    private final UserRepository userRepo;

    public InvoiceService(InvoiceRepository invoiceRepo,
                          InvoiceAuditLogRepository auditLogRepo,
                          BillingProfileRepository billingProfileRepo,
                          AppointmentRepository appointmentRepo,
                          ServiceRepository serviceRepo,
                          UserRepository userRepo) {
        this.invoiceRepo = invoiceRepo;
        this.auditLogRepo = auditLogRepo;
        this.billingProfileRepo = billingProfileRepo;
        this.appointmentRepo = appointmentRepo;
        this.serviceRepo = serviceRepo;
        this.userRepo = userRepo;
    }

    // ==================== CREAR FACTURA DESDE CITA ====================

    /**
     * Crea una factura desde una cita completada.
     * Usa los SNAPSHOTS de la cita, no los datos vivos del servicio.
     */
    @Transactional
    public Invoice createFromAppointment(Long appointmentId, User currentUser) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Cita", appointmentId));

        // Validaciones
        if (!appointment.getBusiness().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Cita", appointmentId);
        }

        if (!appointment.canBeInvoiced()) {
            throw new BusinessValidationException("Esta cita no puede ser facturada. " +
                "Debe estar completada y tener un precio asignado.");
        }

        // Verificar que existe el perfil de facturación
        BillingProfile billing = billingProfileRepo.findByUserId(currentUser.getId())
            .orElseThrow(() -> new BusinessValidationException(
                "Debes configurar tu perfil de facturación antes de emitir facturas"));

        if (billing.getTaxId().equals("PENDIENTE")) {
            throw new BusinessValidationException(
                "Completa tu perfil de facturación con datos fiscales válidos");
        }

        // Crear factura
        Invoice invoice = new Invoice();
        invoice.setBusiness(currentUser);
        invoice.setAppointment(appointment);

        // Snapshot del emisor (INMUTABLE)
        invoice.setEmitterTaxId(billing.getTaxId());
        invoice.setEmitterLegalName(billing.getLegalName());
        invoice.setEmitterAddress(billing.getAddressLine1() +
            (billing.getAddressLine2() != null ? ", " + billing.getAddressLine2() : ""));
        invoice.setEmitterCity(billing.getCity());
        invoice.setEmitterPostalCode(billing.getPostalCode());
        invoice.setEmitterProvince(billing.getProvince());
        invoice.setEmitterCountry(billing.getCountry());

        // Snapshot del cliente (desde la cita)
        invoice.setClientName(appointment.getClientName());
        invoice.setClientEmail(appointment.getClientEmail());
        invoice.setClientPhone(appointment.getClientPhone());

        // Fechas
        invoice.setIssueDate(LocalDate.now());
        invoice.setServiceDate(appointment.getDate());

        // Generar número de factura
        invoice.setInvoiceSeries(billing.getInvoiceSeries());
        invoice.setInvoiceNumber(billing.generateNextInvoiceNumber());
        billingProfileRepo.save(billing); // Guardar el incremento del número

        // Configuración fiscal
        invoice.setVatRate(billing.getVatRate());
        invoice.setNotes(billing.getInvoiceNotes());
        invoice.setPaymentTerms(billing.getPaymentTerms());

        // Estado inicial
        invoice.setStatus(InvoiceStatus.DRAFT);

        // Crear línea desde el SNAPSHOT de la cita
        InvoiceLine line = InvoiceLine.fromAppointment(appointment);
        invoice.addLine(line);

        // Calcular totales
        invoice.recalculateTotals();

        // Guardar
        invoice = invoiceRepo.save(invoice);

        // Registrar auditoría
        logAction(invoice, InvoiceAuditAction.CREATED, currentUser);

        log.info("Factura creada: {} para cita ID: {} por usuario: {}",
            invoice.getInvoiceNumber(), appointmentId, currentUser.getEmail());

        return invoice;
    }

    // ==================== CREAR FACTURA MANUAL ====================

    /**
     * Crea una factura manualmente (sin cita asociada)
     */
    @Transactional
    public Invoice createManual(CreateInvoiceRequest request, User currentUser) {
        BillingProfile billing = billingProfileRepo.findByUserId(currentUser.getId())
            .orElseThrow(() -> new BusinessValidationException(
                "Debes configurar tu perfil de facturación antes de emitir facturas"));

        if (billing.getTaxId().equals("PENDIENTE")) {
            throw new BusinessValidationException(
                "Completa tu perfil de facturación con datos fiscales válidos");
        }

        Invoice invoice = new Invoice();
        invoice.setBusiness(currentUser);

        // Snapshot del emisor
        invoice.setEmitterTaxId(billing.getTaxId());
        invoice.setEmitterLegalName(billing.getLegalName());
        invoice.setEmitterAddress(billing.getAddressLine1() +
            (billing.getAddressLine2() != null ? ", " + billing.getAddressLine2() : ""));
        invoice.setEmitterCity(billing.getCity());
        invoice.setEmitterPostalCode(billing.getPostalCode());
        invoice.setEmitterProvince(billing.getProvince());
        invoice.setEmitterCountry(billing.getCountry());

        // Datos del cliente
        invoice.setClientName(request.clientName());
        invoice.setClientEmail(request.clientEmail());
        invoice.setClientPhone(request.clientPhone());
        invoice.setClientTaxId(request.clientTaxId());
        invoice.setClientAddress(request.clientAddress());

        // Fechas
        invoice.setIssueDate(request.issueDate() != null ? request.issueDate() : LocalDate.now());
        invoice.setDueDate(request.dueDate());
        invoice.setServiceDate(request.serviceDate());

        // Número de factura
        invoice.setInvoiceSeries(billing.getInvoiceSeries());
        invoice.setInvoiceNumber(billing.generateNextInvoiceNumber());
        billingProfileRepo.save(billing);

        // Configuración fiscal
        invoice.setVatRate(billing.getVatRate());
        invoice.setNotes(request.notes() != null ? request.notes() : billing.getInvoiceNotes());
        invoice.setPaymentTerms(billing.getPaymentTerms());
        invoice.setDiscountTotal(request.discountTotal() != null ? request.discountTotal() : BigDecimal.ZERO);

        invoice.setStatus(InvoiceStatus.DRAFT);

        // Crear líneas
        for (int i = 0; i < request.lines().size(); i++) {
            CreateInvoiceRequest.LineRequest lineReq = request.lines().get(i);
            InvoiceLine line = new InvoiceLine();
            line.setDescription(lineReq.description());
            line.setQuantity(lineReq.quantity() != null ? lineReq.quantity() : BigDecimal.ONE);
            line.setUnitPrice(lineReq.unitPrice());
            line.setDiscountPercent(lineReq.discountPercent() != null ? lineReq.discountPercent() : BigDecimal.ZERO);
            line.setLineOrder(i + 1);

            if (lineReq.serviceId() != null) {
                serviceRepo.findById(lineReq.serviceId()).ifPresent(line::setService);
            }

            invoice.addLine(line);
        }

        invoice.recalculateTotals();
        invoice = invoiceRepo.save(invoice);

        logAction(invoice, InvoiceAuditAction.CREATED, currentUser);

        log.info("Factura manual creada: {} por usuario: {}",
            invoice.getInvoiceNumber(), currentUser.getEmail());

        return invoice;
    }

    // ==================== EMITIR FACTURA ====================

    /**
     * Emite una factura en borrador. Tras emitir, la factura es INMUTABLE.
     */
    @Transactional
    public Invoice emit(Long invoiceId, User currentUser) {
        Invoice invoice = getInvoiceForUser(invoiceId, currentUser.getId());

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden emitir facturas en borrador");
        }

        String oldStatus = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());

        // Marcar la cita como facturada si existe
        if (invoice.getAppointment() != null) {
            invoice.getAppointment().markAsInvoiced();
            appointmentRepo.save(invoice.getAppointment());
        }

        invoice = invoiceRepo.save(invoice);

        logStatusChange(invoice, InvoiceAuditAction.ISSUED, currentUser, oldStatus, "ISSUED");

        log.info("Factura emitida: {} por usuario: {}",
            invoice.getInvoiceNumber(), currentUser.getEmail());

        return invoice;
    }

    // ==================== MARCAR COMO PAGADA ====================

    @Transactional
    public Invoice markAsPaid(Long invoiceId, User currentUser) {
        Invoice invoice = getInvoiceForUser(invoiceId, currentUser.getId());

        if (!invoice.canBePaid()) {
            throw new IllegalStateException("Esta factura no puede marcarse como pagada");
        }

        String oldStatus = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());

        invoice = invoiceRepo.save(invoice);

        logStatusChange(invoice, InvoiceAuditAction.PAID, currentUser, oldStatus, "PAID");

        log.info("Factura marcada como pagada: {} por usuario: {}",
            invoice.getInvoiceNumber(), currentUser.getEmail());

        return invoice;
    }

    // ==================== ANULAR FACTURA ====================

    /**
     * Anula una factura emitida. Legalmente, requiere crear una factura rectificativa.
     */
    @Transactional
    public Invoice cancel(Long invoiceId, String reason, User currentUser) {
        Invoice invoice = getInvoiceForUser(invoiceId, currentUser.getId());

        if (!invoice.isCancellable()) {
            throw new IllegalStateException("Esta factura no puede ser anulada");
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessValidationException("Debe proporcionar un motivo para la anulación");
        }

        // Crear factura rectificativa
        Invoice rectificativa = createRectificativa(invoice, currentUser);

        String oldStatus = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now());
        invoice.setCancellationReason(reason);

        invoice = invoiceRepo.save(invoice);

        logStatusChange(invoice, InvoiceAuditAction.CANCELLED, currentUser, oldStatus, "CANCELLED");

        log.info("Factura anulada: {} con rectificativa: {} por usuario: {}",
            invoice.getInvoiceNumber(), rectificativa.getInvoiceNumber(), currentUser.getEmail());

        return invoice;
    }

    /**
     * Crea una factura rectificativa (con importes negativos)
     */
    private Invoice createRectificativa(Invoice original, User currentUser) {
        BillingProfile billing = billingProfileRepo.findByUserId(currentUser.getId())
            .orElseThrow();

        Invoice rectificativa = new Invoice();
        rectificativa.setBusiness(currentUser);
        rectificativa.setRectifiesInvoice(original);

        // Copiar datos del emisor
        rectificativa.setEmitterTaxId(original.getEmitterTaxId());
        rectificativa.setEmitterLegalName(original.getEmitterLegalName());
        rectificativa.setEmitterAddress(original.getEmitterAddress());
        rectificativa.setEmitterCity(original.getEmitterCity());
        rectificativa.setEmitterPostalCode(original.getEmitterPostalCode());
        rectificativa.setEmitterProvince(original.getEmitterProvince());
        rectificativa.setEmitterCountry(original.getEmitterCountry());

        // Copiar datos del cliente
        rectificativa.setClientName(original.getClientName());
        rectificativa.setClientEmail(original.getClientEmail());
        rectificativa.setClientPhone(original.getClientPhone());
        rectificativa.setClientTaxId(original.getClientTaxId());
        rectificativa.setClientAddress(original.getClientAddress());

        // Fechas
        rectificativa.setIssueDate(LocalDate.now());
        rectificativa.setServiceDate(original.getServiceDate());

        // Número de factura
        rectificativa.setInvoiceSeries(billing.getInvoiceSeries());
        rectificativa.setInvoiceNumber(billing.generateNextInvoiceNumber());
        billingProfileRepo.save(billing);

        // Totales NEGATIVOS
        rectificativa.setSubtotal(original.getSubtotal().negate());
        rectificativa.setDiscountTotal(original.getDiscountTotal() != null
            ? original.getDiscountTotal().negate() : BigDecimal.ZERO);
        rectificativa.setTaxableBase(original.getTaxableBase().negate());
        rectificativa.setVatRate(original.getVatRate());
        rectificativa.setVatAmount(original.getVatAmount().negate());
        rectificativa.setTotal(original.getTotal().negate());

        rectificativa.setNotes("Factura rectificativa de " + original.getInvoiceNumber());
        rectificativa.setStatus(InvoiceStatus.ISSUED);
        rectificativa.setIssuedAt(LocalDateTime.now());

        // Líneas con importes negativos
        for (InvoiceLine originalLine : original.getLines()) {
            InvoiceLine line = new InvoiceLine();
            line.setDescription("(Rectificación) " + originalLine.getDescription());
            line.setQuantity(originalLine.getQuantity().negate());
            line.setUnitPrice(originalLine.getUnitPrice());
            line.setDiscountPercent(originalLine.getDiscountPercent());
            line.setLineTotal(originalLine.getLineTotal().negate());
            line.setLineOrder(originalLine.getLineOrder());
            rectificativa.addLine(line);
        }

        rectificativa = invoiceRepo.save(rectificativa);

        logAction(rectificativa, InvoiceAuditAction.CREATED, currentUser);
        logAction(rectificativa, InvoiceAuditAction.ISSUED, currentUser);

        return rectificativa;
    }

    // ==================== CONSULTAS ====================

    @Transactional(readOnly = true)
    public Invoice getById(Long invoiceId, Long userId) {
        return getInvoiceForUser(invoiceId, userId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getByBusinessId(Long businessId) {
        return invoiceRepo.findByBusinessIdOrderByIssueDateDesc(businessId)
            .stream()
            .map(InvoiceDto::summary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> getByDateRange(Long businessId, LocalDate startDate, LocalDate endDate) {
        return invoiceRepo.findByBusinessIdAndIssueDateBetween(businessId, startDate, endDate)
            .stream()
            .map(InvoiceDto::summary)
            .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto getInvoiceForAppointment(Long appointmentId, Long userId) {
        return invoiceRepo.findByAppointmentId(appointmentId)
            .filter(inv -> inv.getBusiness().getId().equals(userId))
            .map(InvoiceDto::fromEntity)
            .orElse(null);
    }

    // ==================== ESTADÍSTICAS ====================

    @Transactional(readOnly = true)
    public BigDecimal getTotalInvoiced(Long businessId, LocalDate startDate, LocalDate endDate) {
        return invoiceRepo.sumTotalByBusinessIdAndDateRange(businessId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalVat(Long businessId, LocalDate startDate, LocalDate endDate) {
        return invoiceRepo.sumVatByBusinessIdAndDateRange(businessId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public long countByStatus(Long businessId, InvoiceStatus status) {
        return invoiceRepo.countByBusinessIdAndStatus(businessId, status);
    }

    // ==================== HELPERS ====================

    private Invoice getInvoiceForUser(Long invoiceId, Long userId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Factura", invoiceId));

        if (!invoice.getBusiness().getId().equals(userId)) {
            throw new AccessDeniedException("Factura", invoiceId);
        }

        return invoice;
    }

    private void logAction(Invoice invoice, InvoiceAuditAction action, User user) {
        InvoiceAuditLog log = InvoiceAuditLog.create(invoice, action, user);
        auditLogRepo.save(log);
    }

    private void logStatusChange(Invoice invoice, InvoiceAuditAction action, User user,
                                 String oldStatus, String newStatus) {
        InvoiceAuditLog log = InvoiceAuditLog.createStatusChange(invoice, action, user, oldStatus, newStatus);
        auditLogRepo.save(log);
    }
}
