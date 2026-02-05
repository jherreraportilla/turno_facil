package com.turnofacil.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "PORTFOLIO_IMAGES")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "BUSINESS_CONFIG_ID", nullable = false)
    private BusinessConfig businessConfig;

    @Column(name = "IMAGE_URL", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "CAPTION")
    private String caption;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private int displayOrder = 0;
}
