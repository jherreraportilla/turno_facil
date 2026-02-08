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

    @Column(name = "RECEIVE_EMAIL_NOTIFICATIONS", nullable = false)
    private boolean receiveEmailNotifications = true;

    // Landing page / Portfolio fields
    @Column(name = "BUSINESS_DESCRIPTION", columnDefinition = "TEXT")
    private String businessDescription;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "CITY", length = 100)
    private String city;

    @Column(name = "PROVINCE", length = 100)
    private String province;

    @Column(name = "INSTAGRAM_URL")
    private String instagramUrl;

    @Column(name = "FACEBOOK_URL")
    private String facebookUrl;

    @Column(name = "WHATSAPP_NUMBER", length = 20)
    private String whatsappNumber;

    @Column(name = "ENABLE_WHATSAPP_REMINDERS", nullable = false)
    private boolean enableWhatsappReminders = false;

    @Column(name = "ENABLE_WHATSAPP_CONFIRMATIONS", nullable = false)
    private boolean enableWhatsappConfirmations = false;

    @Column(name = "BUFFER_TIME_MINUTES", nullable = false)
    private int bufferTimeMinutes = 0; // 0, 5, 10, 15, 30 minutos entre citas

    @Column(name = "WEBSITE_URL")
    private String websiteUrl;

    @Column(name = "GOOGLE_MAPS_EMBED_URL", length = 1000)
    private String googleMapsEmbedUrl;

    // Personalización visual
    @Column(name = "PRIMARY_COLOR", length = 7)
    private String primaryColor = "#c9a227";

    @Column(name = "BACKGROUND_COLOR", length = 7)
    private String backgroundColor = "#1a1a2e";

    @Column(name = "TEXT_COLOR", length = 7)
    private String textColor = "#e0e0e0";
}