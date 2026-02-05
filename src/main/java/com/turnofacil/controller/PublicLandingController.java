package com.turnofacil.controller;

import com.turnofacil.exception.ResourceNotFoundException;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.PortfolioImage;
import com.turnofacil.model.Service;
import com.turnofacil.service.BusinessConfigService;
import com.turnofacil.service.PortfolioImageService;
import com.turnofacil.service.ServiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/public")
public class PublicLandingController {

    private static final Set<String> RESERVED_SLUGS = Set.of("book", "appointment");

    private final BusinessConfigService businessConfigService;
    private final ServiceService serviceService;
    private final PortfolioImageService portfolioImageService;

    public PublicLandingController(BusinessConfigService businessConfigService,
                                   ServiceService serviceService,
                                   PortfolioImageService portfolioImageService) {
        this.businessConfigService = businessConfigService;
        this.serviceService = serviceService;
        this.portfolioImageService = portfolioImageService;
    }

    @GetMapping("/{slug}")
    public String showLanding(@PathVariable String slug, Model model) {
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ResourceNotFoundException("Negocio", "slug", slug);
        }

        BusinessConfig config = businessConfigService.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio", "slug", slug));

        List<Service> services = serviceService.getActiveServicesByBusiness(config.getUser().getId());
        List<PortfolioImage> portfolioImages = portfolioImageService.getByBusinessConfig(config.getId());

        // Parse working days for display
        String[] dayNames = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        String workingDaysDisplay = "";
        if (config.getWorkingDays() != null && !config.getWorkingDays().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String day : config.getWorkingDays().split(",")) {
                int d = Integer.parseInt(day.trim());
                if (d >= 1 && d <= 7) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(dayNames[d]);
                }
            }
            workingDaysDisplay = sb.toString();
        }

        model.addAttribute("config", config);
        model.addAttribute("services", services);
        model.addAttribute("portfolioImages", portfolioImages);
        model.addAttribute("workingDaysDisplay", workingDaysDisplay);
        model.addAttribute("bookingUrl", "/public/book/" + slug);

        return "public/landing";
    }
}
