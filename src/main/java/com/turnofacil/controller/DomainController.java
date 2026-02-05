package com.turnofacil.controller;

import com.turnofacil.model.CustomDomain;
import com.turnofacil.model.User;
import com.turnofacil.service.CustomDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador para gestión de dominios personalizados.
 */
@Controller
@RequestMapping("/admin/domains")
public class DomainController {

    private static final Logger log = LoggerFactory.getLogger(DomainController.class);

    private final CustomDomainService domainService;

    public DomainController(CustomDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * Lista los dominios del negocio.
     */
    @GetMapping
    public String listDomains(@AuthenticationPrincipal User business, Model model) {
        model.addAttribute("domains", domainService.getDomainsForBusiness(business.getId()));
        return "admin/domains";
    }

    /**
     * Registra un nuevo dominio.
     */
    @PostMapping("/add")
    public String addDomain(@AuthenticationPrincipal User business,
                           @RequestParam String domain,
                           RedirectAttributes redirectAttributes) {
        try {
            CustomDomain newDomain = domainService.registerDomain(business, domain);
            redirectAttributes.addFlashAttribute("success",
                    "Dominio registrado. Configura los registros DNS para activarlo.");
            return "redirect:/admin/domains/" + newDomain.getId();
        } catch (CustomDomainService.DomainAlreadyExistsException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/domains";
        } catch (Exception e) {
            log.error("Error registrando dominio: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al registrar el dominio");
            return "redirect:/admin/domains";
        }
    }

    /**
     * Muestra detalles y configuración DNS de un dominio.
     */
    @GetMapping("/{id}")
    public String domainDetail(@AuthenticationPrincipal User business,
                               @PathVariable Long id,
                               Model model) {
        CustomDomain domain = domainService.getDomainForBusiness(id, business.getId())
                .orElseThrow(() -> new RuntimeException("Dominio no encontrado"));

        model.addAttribute("domain", domain);
        model.addAttribute("dnsInstructions", domainService.getDnsInstructions(domain));

        return "admin/domain-detail";
    }

    /**
     * Intenta verificar un dominio.
     */
    @PostMapping("/{id}/verify")
    public String verifyDomain(@AuthenticationPrincipal User business,
                              @PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            CustomDomainService.VerificationResult result = domainService.verifyDomain(id, business.getId());

            if (result.success()) {
                redirectAttributes.addFlashAttribute("success", result.message());
            } else {
                redirectAttributes.addFlashAttribute("error", result.message());
            }
        } catch (Exception e) {
            log.error("Error verificando dominio: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al verificar el dominio");
        }

        return "redirect:/admin/domains/" + id;
    }

    /**
     * Elimina un dominio.
     */
    @PostMapping("/{id}/delete")
    public String deleteDomain(@AuthenticationPrincipal User business,
                              @PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            domainService.deleteDomain(id, business.getId());
            redirectAttributes.addFlashAttribute("success", "Dominio eliminado");
        } catch (Exception e) {
            log.error("Error eliminando dominio: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al eliminar el dominio");
        }

        return "redirect:/admin/domains";
    }
}
