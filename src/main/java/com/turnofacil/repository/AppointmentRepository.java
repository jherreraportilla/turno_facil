package com.turnofacil.repository;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    // 1. Turnos de hoy (ordenados por hora)
    List<Appointment> findByDateAndBusinessIdOrderByTimeAsc(LocalDate date, Long businessId);

    // 2. Todos los turnos del negocio (para FullCalendar)
    List<Appointment> findByBusinessId(Long businessId);

    // 3. Busqueda por rango (muy util cuando tengas miles de turnos)
    List<Appointment> findByDateBetweenAndBusinessId(LocalDate start, LocalDate end, Long businessId);

    // 4. Turnos futuros para recordatorios
    List<Appointment> findByDateGreaterThanEqualAndStatusOrderByDateAscTimeAsc(
            LocalDate date, AppointmentStatus status);

    boolean existsByDateAndTimeAndBusinessId(LocalDate date, LocalTime time, Long businessId);

    // 5. Query para recordatorios pendientes - turnos en ventana de tiempo sin recordatorio enviado
    @Query("SELECT a FROM Appointment a WHERE a.reminderSent = false " +
           "AND a.status IN :statuses " +
           "AND a.business.id IN (SELECT bc.user.id FROM BusinessConfig bc WHERE bc.enableReminders = true) " +
           "AND CONCAT(a.date, 'T', a.time) BETWEEN :startDateTime AND :endDateTime")
    List<Appointment> findAppointmentsNeedingReminder(
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("startDateTime") String startDateTime,
            @Param("endDateTime") String endDateTime);

    // 6. Query para validar solapamientos (native query para usar funciones MySQL)
    @Query(value = "SELECT COUNT(*) > 0 FROM APPOINTMENTS a WHERE a.USER_ID = :businessId " +
           "AND a.DATE = :date " +
           "AND a.STATUS != 'CANCELLED' " +
           "AND a.ID != :excludeId " +
           "AND (a.TIME < :endTime AND ADDTIME(a.TIME, SEC_TO_TIME(a.DURATION * 60)) > :startTime)",
           nativeQuery = true)
    boolean hasOverlappingAppointment(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    // 7. Query simplificada para solapamientos (sin MySQL functions)
    @Query("SELECT a FROM Appointment a WHERE a.business.id = :businessId " +
           "AND a.date = :date " +
           "AND a.status NOT IN (com.turnofacil.model.enums.AppointmentStatus.CANCELLED)")
    List<Appointment> findActiveAppointmentsByDateAndBusiness(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date);
}
