package com.turnofacil.controller;

import com.turnofacil.dto.BillingProfileDto;
import com.turnofacil.dto.CreateInvoiceRequest;
import com.turnofacil.dto.InvoiceDto;
import com.turnofacil.model.BillingProfile;
import com.turnofacil.model.Invoice;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.InvoiceStatus;
import com.turnofacil.model.enums.VatRegime;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.service.BillingProfileService;
import com.turnofacil.service.BusinessConfigService;
import com.turnofacil.service.InvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final BillingProfileService billingProfileService;
    private final BusinessConfigService businessConfigService;
    private final UserRepository userRepo;
    private final AppointmentRepository appointmentRepo;

    public InvoiceController(InvoiceService invoiceService,
                             BillingProfileService billingProfileService,
                             BusinessConfigService businessConfigService,
                             UserRepository userRepo,
                             AppointmentRepository appointmentRepo) {
        this.invoiceService = invoiceService;
        this.billingProfileService = billingProfileService;
        this.businessConfigService = businessConfigService;
        this.userRepo = userRepo;
        this.appointmentRepo = appointmentRepo;
    }

    // ===================== LISTADO DE FACTURAS =====================

    @GetMapping
    public String listInvoices(Model model, Authentication auth, HttpServletRequest request) {
        User user = getCurrentUser(auth);
        var config = businessConfigService.getByUserId(user.getId());

        List<InvoiceDto> invoices = invoiceService.getByBusinessId(user.getId());
        boolean hasProfile = billingProfileService.isProfileComplete(user.getId());

        model.addAttribute("invoices", invoices);
        model.addAttribute("hasProfile", hasProfile);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        model.addAttribute("statuses", InvoiceStatus.values());

        return "admin/invoices";
    }

    // ===================== VER FACTURA =====================

    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable Long id, Model model,
                              Authentication auth, HttpServletRequest request) {
        User user = getCurrentUser(auth);
        var config = businessConfigService.getByUserId(user.getId());

        Invoice invoice = invoiceService.getById(id, user.getId());
        InvoiceDto invoiceDto = InvoiceDto.fromEntity(invoice);

        model.addAttribute("invoice", invoiceDto);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());

        return "admin/invoice-detail";
    }

    // ===================== CREAR DESDE CITA =====================

    @PostMapping("/from-appointment/{appointmentId}")
    public String createFromAppointment(@PathVariable Long appointmentId,
                                        RedirectAttributes redirectAttrs,
                                        Authentication auth) {
        User user = getCurrentUser(auth);

        try {
            Invoice invoice = invoiceService.createFromAppointment(appointmentId, user);
            redirectAttrs.addFlashAttribute("success",
                "Factura " + invoice.getInvoiceNumber() + " creada correctamente");
            return "redirect:/admin/invoices/" + invoice.getId();
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/dashboard";
        }
    }

    // ===================== EMITIR FACTURA =====================

    @PostMapping("/{id}/emit")
    public String emitInvoice(@PathVariable Long id,
                              RedirectAttributes redirectAttrs,
                              Authentication auth) {
        User user = getCurrentUser(auth);

        try {
            Invoice invoice = invoiceService.emit(id, user);
            redirectAttrs.addFlashAttribute("success",
                "Factura " + invoice.getInvoiceNumber() + " emitida correctamente. " +
                "Ya no se puede modificar.");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/invoices/" + id;
    }

    // ===================== MARCAR COMO PAGADA =====================

    @PostMapping("/{id}/pay")
    public String markAsPaid(@PathVariable Long id,
                             RedirectAttributes redirectAttrs,
                             Authentication auth) {
        User user = getCurrentUser(auth);

        try {
            Invoice invoice = invoiceService.markAsPaid(id, user);
            redirectAttrs.addFlashAttribute("success",
                "Factura " + invoice.getInvoiceNumber() + " marcada como pagada");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/invoices/" + id;
    }

    // ===================== ANULAR FACTURA =====================

    @PostMapping("/{id}/cancel")
    public String cancelInvoice(@PathVariable Long id,
                                @RequestParam String reason,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {
        User user = getCurrentUser(auth);

        try {
            Invoice invoice = invoiceService.cancel(id, reason, user);
            redirectAttrs.addFlashAttribute("success",
                "Factura " + invoice.getInvoiceNumber() + " anulada. " +
                "Se ha creado una factura rectificativa.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/invoices/" + id;
    }

    // ===================== PERFIL DE FACTURACIÓN =====================

    @GetMapping("/billing-profile")
    public String showBillingProfile(Model model, Authentication auth, HttpServletRequest request) {
        User user = getCurrentUser(auth);
        var config = businessConfigService.getByUserId(user.getId());

        BillingProfile profile = billingProfileService.getByUserId(user.getId()).orElse(null);
        BillingProfileDto dto = profile != null ? BillingProfileDto.fromEntity(profile) : null;

        model.addAttribute("billingProfile", dto);
        model.addAttribute("vatRegimes", VatRegime.values());
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());

        return "admin/billing-profile";
    }

    @PostMapping("/billing-profile")
    public String saveBillingProfile(@Valid @ModelAttribute("billingProfile") BillingProfileDto dto,
                                     BindingResult result,
                                     RedirectAttributes redirectAttrs,
                                     Authentication auth) {
        if (result.hasErrors()) {
            redirectAttrs.addFlashAttribute("error", "Por favor corrige los errores del formulario");
            return "redirect:/admin/invoices/billing-profile";
        }

        User user = getCurrentUser(auth);

        try {
            billingProfileService.saveProfile(user.getId(), dto);
            redirectAttrs.addFlashAttribute("success", "Perfil de facturación guardado correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/invoices/billing-profile";
    }

    // ===================== API REST =====================

    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<?> getStats(Authentication auth,
                                      @RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate) {
        User user = getCurrentUser(auth);

        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        var stats = Map.of(
            "totalInvoiced", invoiceService.getTotalInvoiced(user.getId(), start, end),
            "totalVat", invoiceService.getTotalVat(user.getId(), start, end),
            "countDraft", invoiceService.countByStatus(user.getId(), InvoiceStatus.DRAFT),
            "countIssued", invoiceService.countByStatus(user.getId(), InvoiceStatus.ISSUED),
            "countPaid", invoiceService.countByStatus(user.getId(), InvoiceStatus.PAID)
        );

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/for-appointment/{appointmentId}")
    @ResponseBody
    public ResponseEntity<?> getInvoiceForAppointment(@PathVariable Long appointmentId,
                                                      Authentication auth) {
        User user = getCurrentUser(auth);
        InvoiceDto invoice = invoiceService.getInvoiceForAppointment(appointmentId, user.getId());

        if (invoice != null) {
            return ResponseEntity.ok(invoice);
        }
        return ResponseEntity.notFound().build();
    }

    // ===================== HELPERS =====================

    private User getCurrentUser(Authentication auth) {
        return userRepo.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
