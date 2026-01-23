package com.turnofacil.controller;

import com.turnofacil.dto.AppointmentDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.service.AppointmentService;
import com.turnofacil.service.BusinessConfigService;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AppointmentRepository appointmentRepo;
    private final UserRepository userRepo;
    private final AppointmentService appointmentService;
    private final BusinessConfigService businessConfigService;

    public AdminController(AppointmentRepository appointmentRepo,
                           UserRepository userRepo,
                           AppointmentService appointmentService,
                           BusinessConfigService businessConfigService) {
        this.appointmentRepo = appointmentRepo;
        this.userRepo = userRepo;
        this.appointmentService = appointmentService;
        this.businessConfigService = businessConfigService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth, HttpServletRequest request) {
        User business = userRepo.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));

        log.info("Acceso al dashboard → Usuario: {} ({})", business.getName(), business.getEmail());

        BusinessConfig config = businessConfigService.getByUserId(business.getId());
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = today.with(java.time.DayOfWeek.SUNDAY);
        LocalDate startOfMonth = today.withDayOfMonth(1);

        // Obtener turnos de hoy
        List<Appointment> todayAppointments = appointmentRepo
                .findByDateAndBusinessIdOrderByTimeAsc(today, business.getId())
                .stream()
                .peek(a -> {
                    if (a.getStatus() == null) {
                        a.setStatus(AppointmentStatus.PENDING);
                    }
                })
                .collect(Collectors.toList());

        // Obtener TODOS los turnos
        List<Appointment> allAppointments = appointmentRepo.findByBusinessId(business.getId())
                .stream()
                .peek(a -> {
                    if (a.getStatus() == null) {
                        a.setStatus(AppointmentStatus.PENDING);
                    }
                })
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime)
                        .reversed())
                .collect(Collectors.toList());

        // CÁLCULO DE KPIs
        long turnosHoy = todayAppointments.size();

        long turnosSemana = allAppointments.stream()
                .filter(a -> !a.getDate().isBefore(startOfWeek) && !a.getDate().isAfter(endOfWeek))
                .count();

        long turnosMes = allAppointments.stream()
                .filter(a -> !a.getDate().isBefore(startOfMonth))
                .count();

        long turnosCompletados = allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .count();

        long turnosCancelados = allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                .count();

        long turnosPendientes = allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                .count();

        long turnosNoShow = allAppointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW)
                .count();

        // Calcular tasa de no-show (porcentaje)
        long turnosPasados = allAppointments.stream()
                .filter(a -> a.getDate().isBefore(today) ||
                        (a.getDate().equals(today) && a.getTime().isBefore(LocalTime.now())))
                .count();

        double tasaNoShow = turnosPasados > 0 ? (turnosNoShow * 100.0 / turnosPasados) : 0.0;

        // Convertir appointments a formato JSON-friendly para el calendario
        List<Map<String, Object>> calendarEvents = allAppointments.stream()
                .map(a -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("clientName", a.getClientName());
                    event.put("clientPhone", a.getClientPhone());
                    event.put("notes", a.getNotes() != null ? a.getNotes() : "");
                    event.put("date", a.getDate().toString());
                    event.put("time", a.getTime().toString());

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

        log.info("Dashboard cargado → Total: {} | Hoy: {} | Semana: {} | Mes: {} | Completados: {} | Cancelados: {} | No-show: {}",
                allAppointments.size(), turnosHoy, turnosSemana, turnosMes,
                turnosCompletados, turnosCancelados, turnosNoShow);

        // Agregar KPIs al modelo
        model.addAttribute("kpiTurnosHoy", turnosHoy);
        model.addAttribute("kpiTurnosSemana", turnosSemana);
        model.addAttribute("kpiTurnosMes", turnosMes);
        model.addAttribute("kpiCompletados", turnosCompletados);
        model.addAttribute("kpiCancelados", turnosCancelados);
        model.addAttribute("kpiPendientes", turnosPendientes);
        model.addAttribute("kpiNoShow", turnosNoShow);
        model.addAttribute("kpiTasaNoShow", String.format("%.1f", tasaNoShow));

        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("allAppointments", allAppointments);
        model.addAttribute("calendarEvents", calendarEvents);
        model.addAttribute("today", today);
        model.addAttribute("businessConfig", config);
        model.addAttribute("currentUrl", request.getRequestURI());

        return "admin/dashboard";
    }

    @GetMapping("/today-appointments")
    @ResponseBody
    public List<AppointmentDto> getTodayAppointments(Authentication authentication) {
        log.debug("Solicitud AJAX de turnos de hoy");
        return appointmentService.getTodayAppointmentsForCurrentUser(authentication);
    }

    @GetMapping("/config")
    public String showConfig(Model model, Authentication auth, HttpServletRequest request) {
        User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        BusinessConfig config = businessConfigService.getByUserId(user.getId());

        log.info("Acceso a configuración → Usuario: {}", user.getEmail());

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
            log.info("Configuración actualizada → Usuario: {} | Negocio: {}",
                    user.getEmail(), businessConfig.getBusinessName());
            redirectAttrs.addFlashAttribute("success", "¡Cambios guardados correctamente!");
        } catch (Exception e) {
            log.error("Error al guardar configuración → {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al guardar los cambios");
        }

        return "redirect:/admin/config";
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

            Appointment appointment = appointmentRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

            // Verificar que el appointment pertenece al negocio
            if (!appointment.getBusiness().getId().equals(business.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String statusCode = payload.get("status");
            AppointmentStatus newStatus = AppointmentStatus.fromCode(statusCode.toLowerCase());

            appointment.setStatus(newStatus);
            appointmentRepo.save(appointment);

            log.info("Estado actualizado → Turno ID: {} | Nuevo estado: {} | Usuario: {}",
                    id, newStatus.getDisplayName("es"), business.getEmail());

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Estado actualizado correctamente"
            ));

        } catch (Exception e) {
            log.error("Error al actualizar estado → {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
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

        // Obtener todos los appointments
        List<Appointment> appointments = appointmentRepo.findByBusinessId(business.getId())
                .stream()
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime))
                .collect(Collectors.toList());

        // Aplicar filtros si existen
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

        // Crear el archivo Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Turnos");

        // Estilos
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        // Crear encabezados
        Row headerRow = sheet.createRow(0);
        String[] columns = {"Fecha", "Hora", "Cliente", "Teléfono", "Email", "Notas", "Estado", "Duración (min)"};

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // Llenar datos
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

        // Ajustar ancho de columnas
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Configurar respuesta HTTP
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=turnos_" + LocalDate.now() + ".xlsx");

        workbook.write(response.getOutputStream());
        workbook.close();

        log.info("Excel exportado → Usuario: {} | Total registros: {}", business.getEmail(), appointments.size());
    }
}