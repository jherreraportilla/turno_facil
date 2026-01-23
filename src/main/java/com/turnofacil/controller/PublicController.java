package com.turnofacil.controller;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.service.AppointmentService;
import com.turnofacil.service.BusinessConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    public PublicController(BusinessConfigService businessConfigService,
                            AppointmentService appointmentService) {
        this.businessConfigService = businessConfigService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/{slug}")
    public String showBookingPage(@PathVariable String slug, Model model) {
        BusinessConfig config = businessConfigService.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        log.info("Página pública cargada para negocio: {} (slug: {})", config.getBusinessName(), slug);

        // Obtener todos los turnos ocupados
        List<Appointment> appointments = appointmentService.getAllByBusiness(config.getUser());

        // Transformar a formato para el calendario JavaScript
        List<Map<String, String>> occupiedSlots = appointments.stream()
                .map(appointment -> {
                    Map<String, String> slot = new HashMap<>();
                    slot.put("date", appointment.getDate().toString());
                    slot.put("time", appointment.getTime().toString());
                    slot.put("start", appointment.getDate() + "T" + appointment.getTime());
                    return slot;
                })
                .collect(Collectors.toList());

        log.info("Turnos ocupados encontrados: {}", occupiedSlots.size());
        log.info("Datos de turnos ocupados: {}", occupiedSlots);

        model.addAttribute("config", config);
        model.addAttribute("occupiedAppointments", occupiedSlots);

        return "public/booking";
    }

    @PostMapping("/{slug}")
    public String bookAppointment(@PathVariable String slug,
                                  @RequestParam LocalDate date,
                                  @RequestParam LocalTime time,
                                  @RequestParam Integer duration,
                                  @RequestParam String clientName,
                                  @RequestParam String clientPhone,
                                  @RequestParam(required = false) String clientEmail,
                                  @RequestParam(required = false) String notes,
                                  RedirectAttributes redirectAttrs) {

        BusinessConfig config = businessConfigService.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        log.info("Intento de reserva → Negocio: {} | Cliente: {} | {} {} | {} min | Tel: {}",
                config.getBusinessName(), clientName, date, time, duration, clientPhone);

        try {
            appointmentService.createAppointment(
                    config.getUser(),
                    date, time,
                    duration,
                    clientName, clientPhone,
                    clientEmail, notes
            );

            log.info("TURNO RESERVADO CON ÉXITO → {} | {} {} | {} min", clientName, date, time, duration);

            // REDIRECT CON PARÁMETRO PARA ACTIVAR MENSAJE + RECARGA
            return "redirect:/public/book/" + slug + "?reserved=true";

        } catch (Exception e) {
            log.error("ERROR AL RESERVAR TURNO → {} | {} {} | Motivo: {}", clientName, date, time, e.getMessage());
            redirectAttrs.addFlashAttribute("error", "No se pudo reservar: " + e.getMessage());
            return "redirect:/public/book/" + slug;
        }
    }
}