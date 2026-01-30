package com.turnofacil.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "SERVICES")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "BUSINESS_ID", nullable = false)
    private User business;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "DURATION_MINUTES", nullable = false)
    private Integer durationMinutes = 30;

    @Column(name = "PRICE", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "COLOR", length = 7)
    private String color = "#6366F1";

    @Column(name = "ICON", length = 50)
    private String icon = "bi-calendar-check";

    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 0;
}
