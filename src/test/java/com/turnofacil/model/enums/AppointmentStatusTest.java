package com.turnofacil.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para las reglas de dominio de AppointmentStatus.
 * Tests puros sin dependencias de Spring.
 */
class AppointmentStatusTest {

    @Nested
    @DisplayName("canTransitionTo - Reglas de transición de estado")
    class TransitionRules {

        @Test
        @DisplayName("PENDING puede transicionar a cualquier estado")
        void pendingCanTransitionToAnyState() {
            assertAll(
                () -> assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CONFIRMED)),
                () -> assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CANCELLED)),
                () -> assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.COMPLETED)),
                () -> assertTrue(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.NO_SHOW))
            );
        }

        @Test
        @DisplayName("CONFIRMED puede transicionar a estados finales pero no a PENDING")
        void confirmedCannotGoBackToPending() {
            assertAll(
                () -> assertFalse(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.PENDING),
                        "CONFIRMED no debe poder volver a PENDING"),
                () -> assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.CANCELLED)),
                () -> assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.COMPLETED)),
                () -> assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.NO_SHOW))
            );
        }

        @Test
        @DisplayName("CANCELLED es estado final - no permite transiciones")
        void cancelledIsFinalState() {
            assertAll(
                () -> assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.PENDING)),
                () -> assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.CONFIRMED)),
                () -> assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.COMPLETED)),
                () -> assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(AppointmentStatus.NO_SHOW))
            );
        }

        @Test
        @DisplayName("COMPLETED es estado final - no permite transiciones")
        void completedIsFinalState() {
            assertAll(
                () -> assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.PENDING)),
                () -> assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.CONFIRMED)),
                () -> assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.CANCELLED)),
                () -> assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(AppointmentStatus.NO_SHOW))
            );
        }

        @Test
        @DisplayName("NO_SHOW es estado final - no permite transiciones")
        void noShowIsFinalState() {
            assertAll(
                () -> assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.PENDING)),
                () -> assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.CONFIRMED)),
                () -> assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.CANCELLED)),
                () -> assertFalse(AppointmentStatus.NO_SHOW.canTransitionTo(AppointmentStatus.COMPLETED))
            );
        }

        @ParameterizedTest
        @EnumSource(AppointmentStatus.class)
        @DisplayName("Ningún estado puede transicionar a sí mismo")
        void cannotTransitionToSameState(AppointmentStatus status) {
            assertFalse(status.canTransitionTo(status),
                    status + " no debe poder transicionar a sí mismo");
        }

        @ParameterizedTest
        @EnumSource(AppointmentStatus.class)
        @DisplayName("Ningún estado puede transicionar a null")
        void cannotTransitionToNull(AppointmentStatus status) {
            assertFalse(status.canTransitionTo(null),
                    status + " no debe poder transicionar a null");
        }
    }

    @Nested
    @DisplayName("isFinalState - Identificación de estados finales")
    class FinalStateIdentification {

        @Test
        @DisplayName("PENDING no es estado final")
        void pendingIsNotFinal() {
            assertFalse(AppointmentStatus.PENDING.isFinalState());
        }

        @Test
        @DisplayName("CONFIRMED no es estado final")
        void confirmedIsNotFinal() {
            assertFalse(AppointmentStatus.CONFIRMED.isFinalState());
        }

        @Test
        @DisplayName("CANCELLED es estado final")
        void cancelledIsFinal() {
            assertTrue(AppointmentStatus.CANCELLED.isFinalState());
        }

        @Test
        @DisplayName("COMPLETED es estado final")
        void completedIsFinal() {
            assertTrue(AppointmentStatus.COMPLETED.isFinalState());
        }

        @Test
        @DisplayName("NO_SHOW es estado final")
        void noShowIsFinal() {
            assertTrue(AppointmentStatus.NO_SHOW.isFinalState());
        }
    }

    @Nested
    @DisplayName("fromCode - Parsing de códigos")
    class CodeParsing {

        @Test
        @DisplayName("Parsea códigos válidos correctamente")
        void parsesValidCodes() {
            assertAll(
                () -> assertEquals(AppointmentStatus.PENDING, AppointmentStatus.fromCode("pending")),
                () -> assertEquals(AppointmentStatus.CONFIRMED, AppointmentStatus.fromCode("confirmed")),
                () -> assertEquals(AppointmentStatus.CANCELLED, AppointmentStatus.fromCode("cancelled")),
                () -> assertEquals(AppointmentStatus.COMPLETED, AppointmentStatus.fromCode("completed")),
                () -> assertEquals(AppointmentStatus.NO_SHOW, AppointmentStatus.fromCode("no_show"))
            );
        }

        @Test
        @DisplayName("Parsea códigos en mayúsculas (case insensitive)")
        void parsesCaseInsensitive() {
            assertAll(
                () -> assertEquals(AppointmentStatus.PENDING, AppointmentStatus.fromCode("PENDING")),
                () -> assertEquals(AppointmentStatus.CONFIRMED, AppointmentStatus.fromCode("Confirmed"))
            );
        }

        @Test
        @DisplayName("Lanza excepción para código inválido")
        void throwsForInvalidCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> AppointmentStatus.fromCode("invalid_status"));
        }

        @Test
        @DisplayName("Retorna null para código null")
        void returnsNullForNullCode() {
            assertNull(AppointmentStatus.fromCode(null));
        }
    }
}
