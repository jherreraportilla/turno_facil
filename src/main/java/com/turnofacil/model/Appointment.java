package com.turnofacil.model;

import com.turnofacil.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "APPOINTMENTS")
@Data
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DATE", nullable = false)
    private LocalDate date;

    @Column(name = "TIME", nullable = false)
    private LocalTime time;

    @Column(name = "DURATION", nullable = false)
    private Integer duration = 30; // AÑADIDO

    @Column(name = "CLIENT_NAME", length = 100)
    private String clientName;

    @Column(name = "CLIENT_PHONE", length = 20)
    private String clientPhone;

    @Column(name = "CLIENT_EMAIL", length = 120)
    private String clientEmail;

    @Column(name = "NOTES", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User business;
}