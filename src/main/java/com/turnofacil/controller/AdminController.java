package com.turnofacil.controller;

import com.turnofacil.dto.AppointmentDto;
import com.turnofacil.dto.BlockedSlotDto;
import com.turnofacil.dto.ServiceDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.BlockedSlot;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.Service;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.model.enums.BlockedSlotType;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.model.PortfolioImage;
import com.turnofacil.service.AppointmentService;
import com.turnofacil.service.BlockedSlotService;
import com.turnofacil.service.BusinessConfigService;
import com.turnofacil.service.PortfolioImageService;
import com.turnofacil.service.ServiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.turnofacil.service.RateLimiterService;
import com.turnofacil.service.UserService;
import com.turnofacil.exception.RateLimitExceededException;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AppointmentRepository appointmentRepo;
    private final UserRepository userRepo;
    private final AppointmentService appointmentService;
    private final BusinessConfigService businessConfigService;
    private final ServiceService serviceService;
    private final BlockedSlotService blockedSlotService;
    private final UserService userService;
    private final RateLimiterService rateLimiterService;
    private final PortfolioImageService portfolioImageService;

    public AdminController(AppointmentRepository appointmentRepo,
                           UserRepository userRepo,
                           AppointmentService appointmentService,
                           BusinessConfigService businessConfigService,
                           ServiceService serviceService,
                           BlockedSlotService blockedSlotService,
                           UserService userService,
                           RateLimiterService rateLimiterService,
                           PortfolioImageService portfolioImageService) {
        this.appointmentRepo = appointmentRepo;
        this.userRepo = userRepo;
        this.appointmentService = appointmentService;
        this.businessConfigService = businessConfigService;
        this.serviceService = serviceService;
        this.blockedSlotService = blockedSlotService;
        this.userService = userService;
        this.rateLimiterService = rateLimiterService;
        this.portfolioImageService = portfolioImageService;
    }

    // ===================== DASHBOARD =====================

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        log.info("Acceso al dashboard - Usuario: {} ({})", business.getName(), business.getEmail());

        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        Long bId = business.getId();
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(java.time.DayOfWeek.SUNDAY);
        LocalDate startOfMonth = today.withDayOfMonth(1);

        // Turnos de hoy
        List<Appointment> todayAppointments = appointmentRepo
                .findByDateAndBusinessIdOrderByTimeAsc(today, bId)
                .stream()
                .peek(a -> { if (a.getStatus() == null) a.setStatus(AppointmentStatus.PENDING); })
                .collect(Collectors.toList());

        // Todos los turnos ordenados para paginación client-side
        List<Appointment> allAppointmentsList = appointmentRepo.findByBusinessIdOrderByDateDescTimeDesc(bId);

        // KPIs con COUNT queries (no carga todas las entidades)
        long turnosHoy = todayAppointments.size();
        long turnosSemana = appointmentRepo.countByBusinessIdAndDateBetween(bId, startOfWeek, endOfWeek);
        long turnosMes = appointmentRepo.countByBusinessIdAndDateFrom(bId, startOfMonth);
        long turnosCompletados = appointmentRepo.countByBusinessIdAndStatus(bId, AppointmentStatus.COMPLETED);
        long turnosCancelados = appointmentRepo.countByBusinessIdAndStatus(bId, AppointmentStatus.CANCELLED);
        long turnosPendientes = appointmentRepo.countByBusinessIdAndStatus(bId, AppointmentStatus.PENDING);
        long turnosNoShow = appointmentRepo.countByBusinessIdAndStatus(bId, AppointmentStatus.NO_SHOW);

        long turnosPasados = appointmentRepo.countPastAppointments(bId, today, LocalTime.now());
        double tasaNoShow = turnosPasados > 0 ? (turnosNoShow * 100.0 / turnosPasados) : 0.0;

        // Calendario: carga solo rango visible (mes actual ± 1 mes)
        LocalDate calStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate calEnd = today.plusMonths(2).withDayOfMonth(1);
        List<Appointment> calendarAppointments = appointmentRepo.findByDateBetweenAndBusinessId(calStart, calEnd, bId);

        List<Map<String, Object>> calendarEvents = calendarAppointments.stream()
                .map(a -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", a.getId());
                    event.put("clientName", a.getClientName());
                    event.put("clientPhone", a.getClientPhone());
                    event.put("clientEmail", a.getClientEmail() != null ? a.getClientEmail() : "");
                    event.put("notes", a.getNotes() != null ? a.getNotes() : "");
                    event.put("internalNotes", a.getInternalNotes() != null ? a.getInternalNotes() : "");
                    event.put("serviceId", a.getService() != null ? a.getService().getId() : "");
                    event.put("date", a.getDate().toString());
                    event.put("time", a.getTime().toString());
                    event.put("duration", a.getDuration());
                    event.put("statusCode", a.getStatus() != null ? a.getStatus().name() : "PENDING");

                    if (a.getStatus() != null) {
                        Map<String, String> statusInfo = new HashMap<>();
                        statusInfo.put("es", a.getStatus().getDisplayName("es"));
                        statusInfo.put("badgeClass", a.getStatus().getBadgeClass());
                        event.put("status", statusInfo);
                    } else {
                        Map<String, String> statusInfo = new HashMap<>();
                        statusInfo.put("es", "Sin estado");
                        statusInfo.put("badgeClass", "bg-secondary");
                        event.put("status", statusInfo);
                    }
                    return event;
                })
                .collect(Collectors.toList());

        // Bloqueos futuros para calendario
        List<BlockedSlot> blockedSlots = blockedSlotService.getFutureBlockedSlots(bId);
        List<Map<String, Object>> blockedEvents = blockedSlots.stream()
                .map(b -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", "blocked-" + b.getId());
                    event.put("title", b.getTitle());
                    event.put("type", b.getType().name());
                    event.put("typeDisplay", b.getType().getDisplayName());
                    event.put("color", b.getType().getColor());
                    event.put("startDate", b.getStartDate().toString());
                    event.put("endDate", b.getEndDate().toString());
                    event.put("allDay", b.isAllDay());
                    if (!b.isAllDay()) {
                        event.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : null);
                        event.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : null);
                    }
                    return event;
                })
                .collect(Collectors.toList());

        log.info("Dashboard cargado - Hoy: {} | Semana: {} | Bloqueos: {}", turnosHoy, turnosSemana, blockedSlots.size());

        // KPIs
        model.addAttribute("kpiTurnosHoy", turnosHoy);
        model.addAttribute("kpiTurnosSemana", turnosSemana);
        model.addAttribute("kpiTurnosMes", turnosMes);
        model.addAttribute("kpiCompletados", turnosCompletados);
        model.addAttribute("kpiCancelados", turnosCancelados);
        model.addAttribute("kpiPendientes", turnosPendientes);
        model.addAttribute("kpiNoShow", turnosNoShow);
        model.addAttribute("kpiTasaNoShow", String.format("%.1f", tasaNoShow));

        // Servicios activos para modal de edición
        List<Service> services = serviceService.getActiveServicesByBusiness(bId);
        model.addAttribute("services", services);

        // Paginación client-side
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("allAppointments", allAppointmentsList);

        model.addAttribute("calendarEvents", calendarEvents);
        model.addAttribute("blockedEvents", blockedEvents);
        model.addAttribute("today", today);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());

        // URL publica
        String publicUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            publicUrl += ":" + request.getServerPort();
        }
        publicUrl += "/public/book/" + config.getSlug();
        model.addAttribute("publicBookingUrl", publicUrl);

        return "admin/dashboard";
    }

    @GetMapping("/today-appointments")
    @ResponseBody
    public List<AppointmentDto> getTodayAppointments(Authentication authentication) {
        return appointmentService.getTodayAppointmentsForCurrentUser(authentication);
    }

    @GetMapping("/api/trends")
    @ResponseBody
    public Map<String, Object> getWeeklyTrends(Authentication auth) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusWeeks(4);

        List<Object[]> dailyCounts = appointmentRepo.countByDayBetween(business.getId(), start, today);

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        Map<LocalDate, Long> countMap = new LinkedHashMap<>();
        for (Object[] row : dailyCounts) {
            countMap.put((LocalDate) row[0], (Long) row[1]);
        }

        // Rellenar días sin citas con 0
        LocalDate cursor = start;
        while (!cursor.isAfter(today)) {
            labels.add(cursor.toString());
            data.add(countMap.getOrDefault(cursor, 0L));
            cursor = cursor.plusDays(1);
        }

        return Map.of("labels", labels, "data", data);
    }

    // ===================== CONFIGURACION =====================

    @GetMapping("/config")
    public String showConfig(Model model, Authentication auth, HttpServletRequest request) {
        User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(user.getId());

        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        return "admin/config";
    }

    @PostMapping("/config")
    public String saveConfig(@Valid @ModelAttribute BusinessConfig businessConfig,
                             BindingResult result,
                             RedirectAttributes redirectAttrs,
                             Authentication auth) {

        if (result.hasErrors()) {
            redirectAttrs.addFlashAttribute("org.springframework.validation.BindingResult.businessConfig", result);
            redirectAttrs.addFlashAttribute("businessConfig", businessConfig);
            redirectAttrs.addFlashAttribute("error", "Por favor corrige los errores");
            return "redirect:/admin/config";
        }

        try {
            User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            businessConfigService.updateConfig(user.getId(), businessConfig);
            redirectAttrs.addFlashAttribute("success", "Cambios guardados correctamente");
        } catch (Exception e) {
            log.error("Error al guardar configuracion: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al guardar los cambios");
        }

        return "redirect:/admin/config";
    }

    // ===================== PERFIL DE USUARIO =====================

    @GetMapping("/profile")
    public String showProfile(Model model, Authentication auth, HttpServletRequest request) {
        User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        return "admin/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam(required = false) String phone,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {

        try {
            User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            userService.updateProfile(user.getId(), name, email, phone);
            redirectAttrs.addFlashAttribute("success", "Datos actualizados correctamente");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error al actualizar perfil: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al actualizar los datos");
        }

        return "redirect:/admin/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttrs,
                                 Authentication auth) {

        try {
            User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();

            // Rate limiting: 3 intentos cada 15 minutos
            rateLimiterService.checkPasswordChangeLimit(user.getId().toString());

            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden");
            }

            userService.changePassword(user.getId(), currentPassword, newPassword);
            redirectAttrs.addFlashAttribute("successPassword", "Contraseña actualizada correctamente");
        } catch (RateLimitExceededException e) {
            redirectAttrs.addFlashAttribute("errorPassword", "Demasiados intentos. Espera unos minutos.");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("errorPassword", e.getMessage());
        } catch (Exception e) {
            log.error("Error al cambiar contraseña: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("errorPassword", "Error al cambiar la contraseña");
        }

        return "redirect:/admin/profile";
    }

    // ===================== SERVICIOS =====================

    @GetMapping("/services")
    public String showServices(Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        List<Service> services = serviceService.getServicesByBusiness(business.getId());

        model.addAttribute("services", services);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        model.addAttribute("blockedSlotTypes", BlockedSlotType.values());

        return "admin/services";
    }

    @PostMapping("/services")
    public String createService(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam Integer durationMinutes,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) String color,
                                @RequestParam(required = false) String icon,
                                @RequestParam(defaultValue = "true") boolean active,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            ServiceDto dto = new ServiceDto(null, name, description, durationMinutes, price,
                    color != null ? color : "#6366F1",
                    icon != null ? icon : "bi-calendar-check",
                    active, 0);
            serviceService.createService(business, dto);
            redirectAttrs.addFlashAttribute("success", "Servicio creado correctamente");
        } catch (Exception e) {
            log.error("Error al crear servicio: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al crear el servicio");
        }

        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/update")
    public String updateService(@PathVariable Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam Integer durationMinutes,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) String color,
                                @RequestParam(required = false) String icon,
                                @RequestParam(defaultValue = "false") boolean active,
                                @RequestParam(defaultValue = "0") Integer displayOrder,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            ServiceDto dto = new ServiceDto(id, name, description, durationMinutes, price,
                    color != null ? color : "#6366F1",
                    icon != null ? icon : "bi-calendar-check",
                    active, displayOrder);
            serviceService.updateService(id, business, dto);
            redirectAttrs.addFlashAttribute("success", "Servicio actualizado correctamente");
        } catch (Exception e) {
            log.error("Error al actualizar servicio: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al actualizar el servicio");
        }

        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/delete")
    public String deleteService(@PathVariable Long id,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            serviceService.deleteService(id, business);
            redirectAttrs.addFlashAttribute("success", "Servicio eliminado correctamente");
        } catch (Exception e) {
            log.error("Error al eliminar servicio: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al eliminar el servicio");
        }

        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleServiceActive(@PathVariable Long id, Authentication auth) {
        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            serviceService.toggleActive(id, business);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ===================== BLOQUEOS DE HORARIO =====================

    @GetMapping("/blocked-slots")
    public String showBlockedSlots(Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        List<BlockedSlot> blockedSlots = blockedSlotService.getBlockedSlotsByBusiness(business.getId());

        model.addAttribute("blockedSlots", blockedSlots);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        model.addAttribute("blockedSlotTypes", BlockedSlotType.values());

        return "admin/blocked-slots";
    }

    @PostMapping("/blocked-slots")
    public String createBlockedSlot(@RequestParam String title,
                                    @RequestParam LocalDate startDate,
                                    @RequestParam(required = false) LocalDate endDate,
                                    @RequestParam(required = false) LocalTime startTime,
                                    @RequestParam(required = false) LocalTime endTime,
                                    @RequestParam(defaultValue = "false") boolean allDay,
                                    @RequestParam(defaultValue = "CUSTOM") String type,
                                    @RequestParam(required = false) String notes,
                                    RedirectAttributes redirectAttrs,
                                    Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            BlockedSlotDto dto = new BlockedSlotDto(null, title, startDate,
                    endDate != null ? endDate : startDate,
                    startTime, endTime, allDay,
                    BlockedSlotType.fromCode(type), notes);
            blockedSlotService.createBlockedSlot(business, dto);
            redirectAttrs.addFlashAttribute("success", "Bloqueo creado correctamente");
        } catch (Exception e) {
            log.error("Error al crear bloqueo: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al crear el bloqueo");
        }

        return "redirect:/admin/blocked-slots";
    }

    @PostMapping("/blocked-slots/{id}/update")
    public String updateBlockedSlot(@PathVariable Long id,
                                    @RequestParam String title,
                                    @RequestParam LocalDate startDate,
                                    @RequestParam(required = false) LocalDate endDate,
                                    @RequestParam(required = false) LocalTime startTime,
                                    @RequestParam(required = false) LocalTime endTime,
                                    @RequestParam(defaultValue = "false") boolean allDay,
                                    @RequestParam(defaultValue = "CUSTOM") String type,
                                    @RequestParam(required = false) String notes,
                                    RedirectAttributes redirectAttrs,
                                    Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            BlockedSlotDto dto = new BlockedSlotDto(id, title, startDate,
                    endDate != null ? endDate : startDate,
                    startTime, endTime, allDay,
                    BlockedSlotType.fromCode(type), notes);
            blockedSlotService.updateBlockedSlot(id, business, dto);
            redirectAttrs.addFlashAttribute("success", "Bloqueo actualizado correctamente");
        } catch (Exception e) {
            log.error("Error al actualizar bloqueo: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al actualizar el bloqueo");
        }

        return "redirect:/admin/blocked-slots";
    }

    @PostMapping("/blocked-slots/{id}/delete")
    public String deleteBlockedSlot(@PathVariable Long id,
                                    RedirectAttributes redirectAttrs,
                                    Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            blockedSlotService.deleteBlockedSlot(id, business);
            redirectAttrs.addFlashAttribute("success", "Bloqueo eliminado correctamente");
        } catch (Exception e) {
            log.error("Error al eliminar bloqueo: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al eliminar el bloqueo");
        }

        return "redirect:/admin/blocked-slots";
    }

    // ===================== PORTFOLIO / MI PAGINA =====================

    @GetMapping("/portfolio")
    public String showPortfolio(Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        List<PortfolioImage> images = portfolioImageService.getByBusinessConfig(config.getId());

        model.addAttribute("businessConfig", config);
        model.addAttribute("portfolioImages", images);
        model.addAttribute("currentUrl", request.getRequestURI());

        // URL publica de la landing
        String publicUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            publicUrl += ":" + request.getServerPort();
        }
        publicUrl += "/public/" + config.getSlug();
        model.addAttribute("publicLandingUrl", publicUrl);

        return "admin/portfolio";
    }

    @PostMapping("/portfolio/images")
    public String addPortfolioImage(@RequestParam String imageUrl,
                                     @RequestParam(required = false) String caption,
                                     RedirectAttributes redirectAttrs,
                                     Authentication auth) {
        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            BusinessConfig config = businessConfigService.getByUserId(business.getId());
            portfolioImageService.addImage(config, imageUrl, caption);
            redirectAttrs.addFlashAttribute("success", "Imagen agregada correctamente");
        } catch (Exception e) {
            log.error("Error al agregar imagen: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al agregar la imagen");
        }
        return "redirect:/admin/portfolio";
    }

    @PostMapping("/portfolio/images/{id}/update")
    public String updatePortfolioImage(@PathVariable Long id,
                                        @RequestParam String imageUrl,
                                        @RequestParam(required = false) String caption,
                                        @RequestParam(required = false) Integer displayOrder,
                                        RedirectAttributes redirectAttrs,
                                        Authentication auth) {
        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            BusinessConfig config = businessConfigService.getByUserId(business.getId());
            portfolioImageService.updateImage(id, config, imageUrl, caption, displayOrder);
            redirectAttrs.addFlashAttribute("success", "Imagen actualizada correctamente");
        } catch (Exception e) {
            log.error("Error al actualizar imagen: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al actualizar la imagen");
        }
        return "redirect:/admin/portfolio";
    }

    @PostMapping("/portfolio/images/{id}/delete")
    public String deletePortfolioImage(@PathVariable Long id,
                                        RedirectAttributes redirectAttrs,
                                        Authentication auth) {
        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            BusinessConfig config = businessConfigService.getByUserId(business.getId());
            portfolioImageService.deleteImage(id, config);
            redirectAttrs.addFlashAttribute("success", "Imagen eliminada correctamente");
        } catch (Exception e) {
            log.error("Error al eliminar imagen: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al eliminar la imagen");
        }
        return "redirect:/admin/portfolio";
    }

    // ===================== TURNOS =====================

    // ===================== HISTORIAL DE CLIENTE =====================

    @GetMapping("/client-history")
    public String showClientHistory(@RequestParam(required = false) String search,
                                    Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        Long bId = business.getId();

        List<Appointment> appointments = List.of();
        if (search != null && !search.isBlank()) {
            appointments = appointmentService.getClientHistory(business, search.trim());
        }

        // Conteos para el resumen (SpEL no soporta lambdas)
        long countCompleted = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long countCancelled = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        long countNoShow = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();

        // Clientes recientes (últimos 10)
        List<Object[]> recentClientsRaw = appointmentRepo.findRecentClients(bId);
        List<Map<String, Object>> recentClients = recentClientsRaw.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> client = new HashMap<>();
                    client.put("name", row[0]);
                    client.put("phone", row[1]);
                    client.put("email", row[2]);
                    client.put("lastVisit", row[3]);
                    client.put("totalAppointments", row[4]);
                    return client;
                })
                .collect(Collectors.toList());

        // Clientes frecuentes (top 5)
        List<Object[]> topClientsRaw = appointmentRepo.findTopClients(bId);
        List<Map<String, Object>> topClients = topClientsRaw.stream()
                .limit(5)
                .map(row -> {
                    Map<String, Object> client = new HashMap<>();
                    client.put("name", row[0]);
                    client.put("phone", row[1]);
                    client.put("email", row[2]);
                    client.put("totalAppointments", row[3]);
                    client.put("completedAppointments", row[4]);
                    return client;
                })
                .collect(Collectors.toList());

        // Estadísticas generales
        long totalClients = recentClientsRaw.size();
        long totalAppointments = appointmentRepo.findByBusinessId(bId).size();

        model.addAttribute("appointments", appointments);
        model.addAttribute("countCompleted", countCompleted);
        model.addAttribute("countCancelled", countCancelled);
        model.addAttribute("countNoShow", countNoShow);
        model.addAttribute("search", search);
        model.addAttribute("recentClients", recentClients);
        model.addAttribute("topClients", topClients);
        model.addAttribute("totalClients", totalClients);
        model.addAttribute("totalAppointments", totalAppointments);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());
        return "admin/client-history";
    }

    // ===================== EDICION DE TURNOS =====================

    @PutMapping("/appointments/{id}")
    @ResponseBody
    public ResponseEntity<?> updateAppointment(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            LocalDate date = LocalDate.parse(payload.get("date"));
            LocalTime time = LocalTime.parse(payload.get("time"));
            Long serviceId = payload.get("serviceId") != null && !payload.get("serviceId").isBlank()
                    ? Long.parseLong(payload.get("serviceId")) : null;
            String notes = payload.get("notes");
            String internalNotes = payload.get("internalNotes");

            appointmentService.updateAppointment(id, business, date, time, serviceId, notes, internalNotes);

            log.info("Turno actualizado - ID: {}", id);

            return ResponseEntity.ok(Map.of("success", true, "message", "Turno actualizado correctamente"));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Acceso denegado"));
        } catch (Exception e) {
            log.error("Error al actualizar turno: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error al actualizar el turno"));
        }
    }

    @PatchMapping("/appointments/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            Authentication auth) {

        try {
            User business = userRepo.findByEmailIgnoreCase(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String statusCode = payload.get("status");
            if (statusCode == null || statusCode.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Estado no proporcionado"));
            }

            AppointmentStatus newStatus;
            try {
                newStatus = AppointmentStatus.fromCode(statusCode.toLowerCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Estado no valido: " + statusCode));
            }

            // Delegar al servicio (valida permisos y transiciones)
            appointmentService.updateStatus(id, business, newStatus);

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Estado actualizado correctamente"
            ));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Acceso denegado"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al actualizar estado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error al actualizar el estado"));
        }
    }

    @GetMapping("/appointments/export")
    public void exportToExcel(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status,
            Authentication auth,
            HttpServletResponse response) throws IOException {

        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Appointment> appointments = appointmentRepo.findByBusinessId(business.getId())
                .stream()
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime))
                .collect(Collectors.toList());

        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            appointments = appointments.stream()
                    .filter(a -> !a.getDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate);
            appointments = appointments.stream()
                    .filter(a -> !a.getDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            AppointmentStatus filterStatus = AppointmentStatus.fromCode(status.toLowerCase());
            appointments = appointments.stream()
                    .filter(a -> a.getStatus() == filterStatus)
                    .collect(Collectors.toList());
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Turnos");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        Row headerRow = sheet.createRow(0);
        String[] columns = {"Fecha", "Hora", "Cliente", "Telefono", "Email", "Notas", "Estado", "Duracion (min)"};

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Appointment apt : appointments) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(apt.getDate().toString());
            row.createCell(1).setCellValue(apt.getTime().toString());
            row.createCell(2).setCellValue(apt.getClientName());
            row.createCell(3).setCellValue(apt.getClientPhone());
            row.createCell(4).setCellValue(apt.getClientEmail() != null ? apt.getClientEmail() : "");
            row.createCell(5).setCellValue(apt.getNotes() != null ? apt.getNotes() : "");
            row.createCell(6).setCellValue(apt.getStatus() != null ? apt.getStatus().getDisplayName("es") : "Pendiente");
            row.createCell(7).setCellValue(apt.getDuration() != null ? apt.getDuration() : 0);
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=turnos_" + LocalDate.now() + ".xlsx");

        workbook.write(response.getOutputStream());
        workbook.close();

        log.info("Excel exportado - Usuario: {} | Total registros: {}", business.getEmail(), appointments.size());
    }

    @GetMapping("/appointments/export-csv")
    public void exportToCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status,
            Authentication auth,
            HttpServletResponse response) throws IOException {

        User business = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();

        List<Appointment> appointments = appointmentRepo.findByBusinessId(business.getId())
                .stream()
                .sorted(Comparator.comparing(Appointment::getDate).thenComparing(Appointment::getTime))
                .collect(Collectors.toList());

        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate);
            appointments = appointments.stream().filter(a -> !a.getDate().isBefore(start)).collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate);
            appointments = appointments.stream().filter(a -> !a.getDate().isAfter(end)).collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            AppointmentStatus filterStatus = AppointmentStatus.fromCode(status.toLowerCase());
            appointments = appointments.stream().filter(a -> a.getStatus() == filterStatus).collect(Collectors.toList());
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=turnos_" + LocalDate.now() + ".csv");
        response.getOutputStream().write(0xEF); // BOM for Excel UTF-8
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        var writer = new java.io.PrintWriter(response.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);
        writer.println("Fecha,Hora,Cliente,Telefono,Email,Notas,Estado,Duracion (min)");

        for (Appointment apt : appointments) {
            writer.printf("%s,%s,\"%s\",\"%s\",\"%s\",\"%s\",%s,%d%n",
                    apt.getDate(),
                    apt.getTime(),
                    escapeCsv(apt.getClientName()),
                    escapeCsv(apt.getClientPhone()),
                    escapeCsv(apt.getClientEmail()),
                    escapeCsv(apt.getNotes()),
                    apt.getStatus() != null ? apt.getStatus().getDisplayName("es") : "Pendiente",
                    apt.getDuration() != null ? apt.getDuration() : 0);
        }
        writer.flush();
        log.info("CSV exportado - Usuario: {} | Total registros: {}", business.getEmail(), appointments.size());
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
