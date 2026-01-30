package com.turnofacil.controller;

import com.turnofacil.dto.PublicBlockedSlotDto;
import com.turnofacil.dto.PublicSlotDto;
import com.turnofacil.exception.ResourceNotFoundException;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.BlockedSlot;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.Service;
import com.turnofacil.service.AppointmentService;
import com.turnofacil.service.BlockedSlotService;
import com.turnofacil.service.BusinessConfigService;
import com.turnofacil.service.RateLimiterService;
import com.turnofacil.service.ServiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/public/book")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final BusinessConfigService businessConfigService;
    private final AppointmentService appointmentService;
    private final ServiceService serviceService;
    private final BlockedSlotService blockedSlotService;
    private final RateLimiterService rateLimiterService;

    public PublicController(BusinessConfigService businessConfigService,
                            AppointmentService appointmentService,
                            ServiceService serviceService,
                            BlockedSlotService blockedSlotService,
                            RateLimiterService rateLimiterService) {
        this.businessConfigService = businessConfigService;
        this.appointmentService = appointmentService;
        this.serviceService = serviceService;
        this.blockedSlotService = blockedSlotService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/{slug}")
    public String showBookingPage(@PathVariable String slug, Model model) {
        BusinessConfig config = businessConfigService.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio", "slug", slug));

        log.info("Pagina publica cargada para negocio: {} (slug: {})", config.getBusinessName(), slug);

        // Obtener servicios activos del negocio
        List<Service> services = serviceService.getActiveServicesByBusiness(config.getUser().getId());

        // Obtener turnos ocupados - SOLO datos necesarios para el calendario
        // NO exponemos datos de clientes (nombres, telefonos, emails)
        List<Appointment> appointments = appointmentService.getAllByBusiness(config.getUser());

        // Transformar a DTO SEGURO - solo fecha, hora y duracion
        List<Map<String, Object>> occupiedSlots = appointments.stream()
                .map(appointment -> {
                    PublicSlotDto dto = PublicSlotDto.fromAppointment(appointment);
                    Map<String, Object> slot = new HashMap<>();
                    slot.put("date", dto.date().toString());
                    slot.put("time", dto.time().toString());
                    slot.put("start", dto.getStartIso());
                    slot.put("duration", dto.duration());
                    slot.put("end", dto.getEndIso());
                    // NO incluimos: clientName, clientPhone, clientEmail, notes, status
                    return slot;
                })
                .collect(Collectors.toList());

        // Obtener bloqueos de horario
        List<BlockedSlot> blockedSlots = blockedSlotService.getFutureBlockedSlots(config.getUser().getId());

        // Transformar bloqueos a DTO SEGURO - sin titulo ni notas del negocio
        List<Map<String, Object>> blockedEvents = blockedSlots.stream()
                .map(block -> {
                    PublicBlockedSlotDto dto = PublicBlockedSlotDto.fromBlockedSlot(block);
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", dto.id());
                    event.put("title", "No disponible"); // Titulo generico, no el real
                    event.put("startDate", dto.startDate().toString());
                    event.put("endDate", dto.endDate().toString());
                    event.put("allDay", dto.allDay());
                    event.put("type", "BLOCKED");
                    event.put("color", dto.color());
                    if (!dto.allDay()) {
                        event.put("startTime", dto.startTime() != null ? dto.startTime().toString() : null);
                        event.put("endTime", dto.endTime() != null ? dto.endTime().toString() : null);
                    }
                    return event;
                })
                .collect(Collectors.toList());

        log.info("Turnos ocupados: {} | Servicios: {} | Bloqueos: {}",
                occupiedSlots.size(), services.size(), blockedSlots.size());

        model.addAttribute("config", config);
        model.addAttribute("services", services);
        model.addAttribute("occupiedAppointments", occupiedSlots);
        model.addAttribute("blockedSlots", blockedEvents);

        return "public/booking";
    }

    @PostMapping("/{slug}")
    public String bookAppointment(@PathVariable String slug,
                                  @RequestParam LocalDate date,
                                  @RequestParam LocalTime time,
                                  @RequestParam Integer duration,
                                  @RequestParam(required = false) Long serviceId,
                                  @RequestParam @NotBlank @Size(min = 2, max = 100) String clientName,
                                  @RequestParam @NotBlank @Size(min = 9, max = 20) String clientPhone,
                                  @RequestParam(required = false) @Email @Size(max = 100) String clientEmail,
                                  @RequestParam(required = false) @Size(max = 500) String notes,
                                  RedirectAttributes redirectAttrs,
                                  HttpServletRequest request) {

        // Rate limiting: 5 reservas por IP cada hora
        String clientIp = getClientIp(request);
        rateLimiterService.checkPublicBookingLimit(clientIp);

        BusinessConfig config = businessConfigService.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio", "slug", slug));

        log.info("Intento de reserva - Negocio: {} | Cliente: {} | {} {} | {} min | IP: {}",
                config.getBusinessName(), sanitize(clientName), date, time, duration, clientIp);

        try {
            // Sanitizar inputs antes de guardar
            String sanitizedName = sanitize(clientName);
            String sanitizedPhone = sanitize(clientPhone);
            String sanitizedEmail = clientEmail != null ? sanitize(clientEmail) : null;
            String sanitizedNotes = notes != null ? sanitize(notes) : null;

            appointmentService.createAppointment(
                    config.getUser(),
                    date, time,
                    duration,
                    serviceId,
                    sanitizedName, sanitizedPhone,
                    sanitizedEmail, sanitizedNotes
            );

            log.info("TURNO RESERVADO CON EXITO - {} | {} {} | {} min", sanitizedName, date, time, duration);

            return "redirect:/public/book/" + slug + "?reserved=true";

        } catch (IllegalStateException e) {
            log.warn("ERROR AL RESERVAR - {} | {} {} | Motivo: {}", sanitize(clientName), date, time, e.getMessage());
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/public/book/" + slug;
        } catch (Exception e) {
            log.error("ERROR AL RESERVAR TURNO - {} | {} {} | Error: {}", sanitize(clientName), date, time, e.getMessage());
            redirectAttrs.addFlashAttribute("error", "No se pudo reservar. Por favor, intenta de nuevo.");
            return "redirect:/public/book/" + slug;
        }
    }

    /**
     * Sanitiza input para prevenir XSS y limpiar caracteres peligrosos
     */
    private String sanitize(String input) {
        if (input == null) return null;
        // Escapar HTML y eliminar caracteres de control
        return HtmlUtils.htmlEscape(input.trim())
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
    }

    /**
     * Obtiene la IP real del cliente, considerando proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
