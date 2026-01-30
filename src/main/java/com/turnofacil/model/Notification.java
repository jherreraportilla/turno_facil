package com.turnofacil.model;

import com.turnofacil.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATIONS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "BUSINESS_ID", nullable = false)
    private User business;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "TITLE", nullable = false, length = 150)
    private String title;

    @Column(name = "MESSAGE", nullable = false, length = 500)
    private String message;

    @Column(name = "LINK", length = 255)
    private String link;

    @Column(name = "IS_READ", nullable = false)
    private boolean read = false;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "READ_AT")
    private LocalDateTime readAt;

    @ManyToOne
    @JoinColumn(name = "APPOINTMENT_ID")
    private Appointment appointment;
}
