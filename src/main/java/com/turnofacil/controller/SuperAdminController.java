package com.turnofacil.controller;

import com.turnofacil.service.PlatformService;
import com.turnofacil.service.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador para la sección de super administrador.
 * Solo accesible por usuarios con rol SUPER_ADMIN.
 */
@Controller
@RequestMapping("/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminController.class);

    private final PlatformService platformService;
    private final CacheService cacheService;

    public SuperAdminController(PlatformService platformService, CacheService cacheService) {
        this.platformService = platformService;
        this.cacheService = cacheService;
    }

    /**
     * Dashboard principal del super admin con métricas de plataforma.
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        PlatformService.PlatformMetrics metrics = platformService.getMetrics();

        model.addAttribute("metrics", metrics);
        model.addAttribute("cacheProvider", cacheService.getProviderName());
        model.addAttribute("cacheAvailable", cacheService.isAvailable());

        return "platform/dashboard";
    }

    /**
     * Lista de todos los negocios registrados.
     */
    @GetMapping("/businesses")
    public String listBusinesses(Model model) {
        model.addAttribute("businesses", platformService.listBusinesses());
        return "platform/businesses";
    }

    /**
     * Detalle de un negocio específico.
     */
    @GetMapping("/businesses/{id}")
    public String businessDetail(@PathVariable Long id, Model model) {
        PlatformService.BusinessDetails details = platformService.getBusinessDetails(id);
        model.addAttribute("business", details);
        return "platform/business-detail";
    }

    /**
     * Habilitar/deshabilitar un negocio.
     */
    @PostMapping("/businesses/{id}/toggle")
    public String toggleBusiness(@PathVariable Long id,
                                 @RequestParam boolean enabled,
                                 RedirectAttributes redirectAttributes) {
        try {
            platformService.setBusinessEnabled(id, enabled);
            redirectAttributes.addFlashAttribute("success",
                    "Negocio " + (enabled ? "habilitado" : "deshabilitado") + " correctamente");
        } catch (Exception e) {
            log.error("Error toggling business {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al modificar el negocio");
        }
        return "redirect:/platform/businesses/" + id;
    }

    /**
     * Página de métricas SaaS detalladas.
     */
    @GetMapping("/metrics")
    public String metrics(Model model) {
        PlatformService.PlatformMetrics metrics = platformService.getMetrics();
        model.addAttribute("metrics", metrics);
        return "platform/metrics";
    }

    /**
     * Página de estado del sistema.
     */
    @GetMapping("/system")
    public String systemStatus(Model model) {
        model.addAttribute("cacheProvider", cacheService.getProviderName());
        model.addAttribute("cacheAvailable", cacheService.isAvailable());
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
        model.addAttribute("availableProcessors", Runtime.getRuntime().availableProcessors());
        model.addAttribute("maxMemory", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        model.addAttribute("freeMemory", Runtime.getRuntime().freeMemory() / 1024 / 1024);

        return "platform/system";
    }
}
