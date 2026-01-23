package com.turnofacil.repository;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    // 1. Turnos de hoy (ordenados por hora)
    List<Appointment> findByDateAndBusinessIdOrderByTimeAsc(LocalDate date, Long businessId);

    // 2. Todos los turnos del negocio (para FullCalendar)
    List<Appointment> findByBusinessId(Long businessId);

    // 3. Búsqueda por rango (muy útil cuando tengas miles de turnos)
    List<Appointment> findByDateBetweenAndBusinessId(LocalDate start, LocalDate end, Long businessId);

    // 4. (Opcional) Turnos futuros para recordatorios
    List<Appointment> findByDateGreaterThanEqualAndStatusOrderByDateAscTimeAsc(
            LocalDate date, AppointmentStatus status);

    boolean existsByDateAndTimeAndBusinessId(LocalDate date, LocalTime time, Long businessId);
}
