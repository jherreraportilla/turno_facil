package com.turnofacil.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FAQS")
@Data
@NoArgsConstructor
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User business;

    @Column(name = "QUESTION", nullable = false, length = 500)
    private String question;

    @Column(name = "ANSWER", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder = 0;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;
}
