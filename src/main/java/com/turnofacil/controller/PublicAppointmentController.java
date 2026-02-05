package com.turnofacil.controller;

import com.turnofacil.dto.PublicSlotDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.BlockedSlot;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.service.AppointmentService;
import com.turnofacil.service.BlockedSlotService;
import com.turnofacil.repository.BusinessConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/public/appointment")
public class PublicAppointmentController {

    private static final Logger log = LoggerFactory.getLogger(PublicAppointmentController.class);

    private final AppointmentService appointmentService;
    private final BlockedSlotService blockedSlotService;
    private final BusinessConfigRepository businessConfigRepo;

    public PublicAppointmentController(AppointmentService appointmentService,
                                       BlockedSlotService blockedSlotService,
                                       BusinessConfigRepository businessConfigRepo) {
        this.appointmentService = appointmentService;
        this.blockedSlotService = blockedSlotService;
        this.businessConfigRepo = businessConfigRepo;
    }

    @GetMapping("/{token}")
    public String viewAppointment(@PathVariable String token, Model model) {
        try {
            Appointment appointment = appointmentService.getByToken(token);
            model.addAttribute("appointment", appointment);
            model.addAttribute("token", token);

            // Load business config for reschedule form
            BusinessConfig config = businessConfigRepo.findByUserId(appointment.getBusiness().getId()).orElse(null);
            if (config != null) {
                model.addAttribute("businessConfig", config);
            }
        } catch (Exception e) {
            log.warn("Token de turno no encontrado: {}", token);
            model.addAttribute("error", "No se encontró el turno solicitado.");
        }
        return "public/appointment-manage";
    }

    @PostMapping("/{token}/cancel")
    public String cancelAppointment(@PathVariable String token, RedirectAttributes redirectAttrs) {
        try {
            appointmentService.cancelByToken(token);
            redirectAttrs.addFlashAttribute("success", "Tu turno ha sido cancelado correctamente.");
            log.info("Turno cancelado por cliente - Token: {}", token);
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error al cancelar turno por token: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "No se pudo cancelar el turno.");
        }
        return "redirect:/public/appointment/" + token;
    }

    @PostMapping("/{token}/reschedule")
    public String rescheduleAppointment(@PathVariable String token,
                                        @RequestParam LocalDate date,
                                        @RequestParam LocalTime time,
                                        RedirectAttributes redirectAttrs) {
        try {
            appointmentService.rescheduleByToken(token, date, time);
            redirectAttrs.addFlashAttribute("success", "Tu turno ha sido reagendado correctamente.");
            log.info("Turno reagendado por cliente - Token: {} - Nueva fecha: {} {}", token, date, time);
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error al reagendar turno por token: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "No se pudo reagendar el turno.");
        }
        return "redirect:/public/appointment/" + token;
    }

    @GetMapping("/{token}/available-slots")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailableSlots(@PathVariable String token) {
        try {
            Appointment appointment = appointmentService.getByToken(token);
            Long businessId = appointment.getBusiness().getId();
            BusinessConfig config = businessConfigRepo.findByUserId(businessId).orElse(null);

            // Occupied slots
            List<Appointment> appointments = appointmentService.getAllByBusiness(appointment.getBusiness());
            List<Map<String, Object>> occupiedSlots = appointments.stream()
                    .filter(a -> !a.getId().equals(appointment.getId())) // exclude current appointment
                    .map(a -> {
                        PublicSlotDto dto = PublicSlotDto.fromAppointment(a);
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dto.date().toString());
                        slot.put("time", dto.time().toString());
                        slot.put("duration", dto.duration());
                        return slot;
                    })
                    .collect(Collectors.toList());

            // Blocked slots
            List<BlockedSlot> blockedSlots = blockedSlotService.getFutureBlockedSlots(businessId);
            List<Map<String, Object>> blockedEvents = blockedSlots.stream()
                    .map(block -> {
                        Map<String, Object> event = new HashMap<>();
                        event.put("startDate", block.getStartDate().toString());
                        event.put("endDate", block.getEndDate().toString());
                        event.put("allDay", block.isAllDay());
                        if (!block.isAllDay()) {
                            event.put("startTime", block.getStartTime() != null ? block.getStartTime().toString() : null);
                            event.put("endTime", block.getEndTime() != null ? block.getEndTime().toString() : null);
                        }
                        return event;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("occupiedSlots", occupiedSlots);
            result.put("blockedSlots", blockedEvents);
            if (config != null) {
                result.put("openingTime", config.getOpeningTime());
                result.put("closingTime", config.getClosingTime());
                result.put("slotDuration", config.getSlotDurationMinutes());
                result.put("workingDays", config.getWorkingDays());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
