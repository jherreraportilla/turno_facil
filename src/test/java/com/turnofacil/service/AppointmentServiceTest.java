package com.turnofacil.service;

import com.turnofacil.model.Appointment;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.AppointmentStatus;
import com.turnofacil.repository.AppointmentRepository;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AppointmentService.
 * Usa mocks para aislar la lógica de negocio.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepo;

    @Mock
    private UserService userService;

    @Mock
    private BlockedSlotService blockedSlotService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ServiceRepository serviceRepo;

    @Mock
    private BusinessConfigRepository businessConfigRepo;

    @Mock
    private PlanLimitsService planLimitsService;

    @InjectMocks
    private AppointmentService appointmentService;

    private User business;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        business = new User();
        business.setId(1L);
        business.setEmail("test@business.com");
        business.setName("Test Business");

        appointment = new Appointment();
        appointment.setId(100L);
        appointment.setBusiness(business);
        appointment.setStatus(AppointmentStatus.PENDING);
    }

    @Nested
    @DisplayName("updateStatus - Cambio de estado con validaciones")
    class UpdateStatus {

        @Test
        @DisplayName("Cambia estado correctamente cuando la transición es válida")
        void updatesStatusWhenTransitionIsValid() {
            // Given
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));
            when(appointmentRepo.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Appointment result = appointmentService.updateStatus(100L, business, AppointmentStatus.CONFIRMED);

            // Then
            assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
            verify(appointmentRepo).save(appointment);
        }

        @Test
        @DisplayName("Lanza excepción cuando el turno no existe")
        void throwsWhenAppointmentNotFound() {
            // Given
            when(appointmentRepo.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(RuntimeException.class,
                    () -> appointmentService.updateStatus(999L, business, AppointmentStatus.CONFIRMED));

            verify(appointmentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Lanza SecurityException cuando el usuario no es dueño del turno")
        void throwsSecurityExceptionWhenNotOwner() {
            // Given
            User otherBusiness = new User();
            otherBusiness.setId(2L);

            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            assertThrows(SecurityException.class,
                    () -> appointmentService.updateStatus(100L, otherBusiness, AppointmentStatus.CONFIRMED));

            verify(appointmentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Lanza IllegalStateException cuando la transición no es válida")
        void throwsWhenTransitionIsInvalid() {
            // Given - turno ya completado
            appointment.setStatus(AppointmentStatus.COMPLETED);
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> appointmentService.updateStatus(100L, business, AppointmentStatus.PENDING));

            assertTrue(exception.getMessage().contains("No se puede cambiar"));
            verify(appointmentRepo, never()).save(any());
        }

        @Test
        @DisplayName("No permite cambiar de CANCELLED a COMPLETED")
        void cannotChangeCancelledToCompleted() {
            // Given
            appointment.setStatus(AppointmentStatus.CANCELLED);
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            assertThrows(IllegalStateException.class,
                    () -> appointmentService.updateStatus(100L, business, AppointmentStatus.COMPLETED));

            verify(appointmentRepo, never()).save(any());
        }

        @Test
        @DisplayName("No permite cambiar de NO_SHOW a CONFIRMED")
        void cannotChangeNoShowToConfirmed() {
            // Given
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            assertThrows(IllegalStateException.class,
                    () -> appointmentService.updateStatus(100L, business, AppointmentStatus.CONFIRMED));

            verify(appointmentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Permite todas las transiciones desde PENDING")
        void allowsAllTransitionsFromPending() {
            // Given
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));
            when(appointmentRepo.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

            // When/Then - probar cada transición válida desde PENDING
            for (AppointmentStatus targetStatus : AppointmentStatus.values()) {
                if (targetStatus != AppointmentStatus.PENDING) {
                    appointment.setStatus(AppointmentStatus.PENDING); // Reset

                    Appointment result = appointmentService.updateStatus(100L, business, targetStatus);

                    assertEquals(targetStatus, result.getStatus(),
                            "Debería permitir transición de PENDING a " + targetStatus);
                }
            }
        }

        @Test
        @DisplayName("CONFIRMED no puede volver a PENDING")
        void confirmedCannotGoBackToPending() {
            // Given
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            assertThrows(IllegalStateException.class,
                    () -> appointmentService.updateStatus(100L, business, AppointmentStatus.PENDING));
        }
    }

    @Nested
    @DisplayName("getByIdAndBusiness - Obtener turno con validación de permisos")
    class GetByIdAndBusiness {

        @Test
        @DisplayName("Retorna turno cuando el usuario es dueño")
        void returnsAppointmentWhenOwner() {
            // Given
            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When
            Appointment result = appointmentService.getByIdAndBusiness(100L, business);

            // Then
            assertNotNull(result);
            assertEquals(100L, result.getId());
        }

        @Test
        @DisplayName("Lanza SecurityException cuando no es dueño")
        void throwsWhenNotOwner() {
            // Given
            User otherBusiness = new User();
            otherBusiness.setId(2L);

            when(appointmentRepo.findById(100L)).thenReturn(Optional.of(appointment));

            // When/Then
            assertThrows(SecurityException.class,
                    () -> appointmentService.getByIdAndBusiness(100L, otherBusiness));
        }

        @Test
        @DisplayName("Lanza excepción cuando el turno no existe")
        void throwsWhenNotFound() {
            // Given
            when(appointmentRepo.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(RuntimeException.class,
                    () -> appointmentService.getByIdAndBusiness(999L, business));
        }
    }
}
