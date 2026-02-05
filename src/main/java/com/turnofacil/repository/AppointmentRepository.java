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
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Token de cancelación
    Optional<Appointment> findByCancellationToken(String cancellationToken);

    // Historial de cliente por teléfono o email
    @Query("SELECT a FROM Appointment a WHERE a.business.id = :businessId " +
           "AND (LOWER(a.clientPhone) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(a.clientEmail) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(a.clientName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY a.date DESC, a.time DESC")
    List<Appointment> findByClientSearch(@Param("businessId") Long businessId, @Param("search") String search);

    // 1. Turnos de hoy (ordenados por hora)
    List<Appointment> findByDateAndBusinessIdOrderByTimeAsc(LocalDate date, Long businessId);

    // 2. Todos los turnos del negocio (para FullCalendar)
    List<Appointment> findByBusinessId(Long businessId);

    // 2.1 Contar todos los turnos del negocio
    long countByBusinessId(Long businessId);

    // 2a. Todos los turnos ordenados por fecha desc (para tabla del dashboard con paginación client-side)
    @Query("SELECT a FROM Appointment a WHERE a.business.id = :businessId ORDER BY a.date DESC, a.time DESC")
    List<Appointment> findByBusinessIdOrderByDateDescTimeDesc(@Param("businessId") Long businessId);

    // 2b. Clientes recientes (últimas citas agrupadas por teléfono)
    @Query("SELECT a.clientName, a.clientPhone, a.clientEmail, MAX(a.date), COUNT(a) " +
           "FROM Appointment a WHERE a.business.id = :businessId " +
           "GROUP BY a.clientPhone, a.clientName, a.clientEmail " +
           "ORDER BY MAX(a.date) DESC")
    List<Object[]> findRecentClients(@Param("businessId") Long businessId);

    // 2c. Clientes frecuentes (más citas)
    @Query("SELECT a.clientName, a.clientPhone, a.clientEmail, COUNT(a) as totalCitas, " +
           "SUM(CASE WHEN a.status = 'COMPLETED' THEN 1 ELSE 0 END) as completadas " +
           "FROM Appointment a WHERE a.business.id = :businessId " +
           "GROUP BY a.clientPhone, a.clientName, a.clientEmail " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findTopClients(@Param("businessId") Long businessId);

    // KPIs: counts por estado sin cargar entidades
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.business.id = :businessId AND a.date BETWEEN :start AND :end")
    long countByBusinessIdAndDateBetween(@Param("businessId") Long businessId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.business.id = :businessId AND a.status = :status")
    long countByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") AppointmentStatus status);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.business.id = :businessId AND a.date >= :start")
    long countByBusinessIdAndDateFrom(@Param("businessId") Long businessId, @Param("start") LocalDate start);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.business.id = :businessId " +
           "AND (a.date < :today OR (a.date = :today AND a.time < :now))")
    long countPastAppointments(@Param("businessId") Long businessId, @Param("today") LocalDate today, @Param("now") LocalTime now);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.business.id = :businessId " +
           "AND a.status = 'NO_SHOW' AND (a.date < :today OR (a.date = :today AND a.time < :now))")
    long countNoShowPastAppointments(@Param("businessId") Long businessId, @Param("today") LocalDate today, @Param("now") LocalTime now);

    // Tendencia: citas por día en un rango
    @Query("SELECT a.date, COUNT(a) FROM Appointment a WHERE a.business.id = :businessId " +
           "AND a.date BETWEEN :start AND :end GROUP BY a.date ORDER BY a.date")
    List<Object[]> countByDayBetween(@Param("businessId") Long businessId, @Param("start") LocalDate start, @Param("end") LocalDate end);

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
