package com.turnofacil.service;

import com.turnofacil.dto.AppointmentDto;
import com.turnofacil.model.Appointment;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.AppointmentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepo;
    private final UserService userService;

    public AppointmentService(AppointmentRepository appointmentRepo, UserService userService) {
        this.appointmentRepo = appointmentRepo;
        this.userService = userService;
    }

    // CREAR TURNO DESDE PÁGINA PÚBLICA – CON DURACIÓN
    @Transactional
    public Appointment createAppointment(User business,
                                         LocalDate date,
                                         LocalTime time,
                                         Integer duration,           // ← NUEVO
                                         String clientName,
                                         String clientPhone,
                                         String clientEmail,
                                         String notes) {

        // Validación: no permitir turnos ya ocupados
        boolean exists = appointmentRepo.existsByDateAndTimeAndBusinessId(date, time, business.getId());
        if (exists) {
            throw new IllegalStateException("Este horario ya está ocupado");
        }

        Appointment appointment = new Appointment();
        appointment.setBusiness(business);
        appointment.setDate(date);
        appointment.setTime(time);
        appointment.setDuration(duration != null ? duration : 30); // ← GUARDAMOS LA DURACIÓN
        appointment.setClientName(clientName);
        appointment.setClientPhone(clientPhone);
        appointment.setClientEmail(clientEmail);
        appointment.setNotes(notes);
        appointment.setStatus(AppointmentStatus.PENDING);

        return appointmentRepo.save(appointment);
    }

    // Método auxiliar
    public boolean isSlotTaken(LocalDate date, LocalTime time, Long businessId) {
        return appointmentRepo.existsByDateAndTimeAndBusinessId(date, time, businessId);
    }

    // Obtener todos los turnos de un negocio
    public List<Appointment> getAllByBusiness(User business) {
        return appointmentRepo.findByBusinessId(business.getId());
    }

    public List<Appointment> getByDateAndBusiness(LocalDate date, User business) {
        return appointmentRepo.findByDateAndBusinessIdOrderByTimeAsc(date, business.getId());
    }

    // Cancelar turno
    @Transactional
    public void cancelAppointment(Long id, User business) {
        Appointment appt = appointmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
        if (!appt.getBusiness().getId().equals(business.getId())) {
            throw new SecurityException("No tienes permiso");
        }
        appt.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepo.save(appt);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getTodayAppointmentsForCurrentUser(Authentication authentication) {
        User business = userService.getCurrentBusinessByEmail(authentication.getName());
        LocalDate today = LocalDate.now();

        return appointmentRepo.findByDateAndBusinessIdOrderByTimeAsc(today, business.getId())
                .stream()
                .map(appt -> new AppointmentDto(
                        appt.getTime(),
                        appt.getClientName(),
                        appt.getClientPhone(),
                        appt.getNotes(),
                        appt.getStatus()
                ))
                .collect(Collectors.toList());
    }
}