package com.turnofacil.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "BUSINESS_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @OneToOne
    @JoinColumn(name = "USER_ID", unique = true, nullable = false)
    private User user;

    @Column(name = "BUSINESS_NAME", nullable = false, length = 120)
    private String businessName = "Mi Negocio";

    @Column(name = "SLOT_DURATION_MINUTES", nullable = false)
    private int slotDurationMinutes = 30;

    @Column(name = "OPENING_TIME", nullable = false, length = 5)
    private String openingTime = "09:00";

    @Column(name = "CLOSING_TIME", nullable = false, length = 5)
    private String closingTime = "20:00";

    // Mejor como String que como int[] → más fácil de editar desde formulario
    @Column(name = "WORKING_DAYS", nullable = false, length = 30)
    private String workingDays = "1,2,3,4,5"; // Lunes a viernes por defecto

    @Column(name = "TIMEZONE", nullable = false, length = 50)
    private String timezone = "America/Argentina/Buenos_Aires"; // o Europe/Madrid según tu público

    // Opcional: para futuros recordatorios automáticos
    @Column(name = "ENABLE_REMINDERS", nullable = false)
    private boolean enableReminders = true;

    @Column(name = "REMINDER_HOURS_BEFORE", nullable = false)
    private int reminderHoursBefore = 24;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;
}