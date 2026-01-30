package com.turnofacil.model;

import com.turnofacil.model.enums.BlockedSlotType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "BLOCKED_SLOTS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockedSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "BUSINESS_ID", nullable = false)
    private User business;

    @Column(name = "TITLE", nullable = false, length = 100)
    private String title;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "START_TIME")
    private LocalTime startTime;

    @Column(name = "END_TIME")
    private LocalTime endTime;

    @Column(name = "ALL_DAY", nullable = false)
    private boolean allDay = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 20)
    private BlockedSlotType type = BlockedSlotType.CUSTOM;

    @Column(name = "NOTES", columnDefinition = "TEXT")
    private String notes;
}
