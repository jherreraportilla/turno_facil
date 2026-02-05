package com.turnofacil.repository;

import com.turnofacil.model.PortfolioImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioImageRepository extends JpaRepository<PortfolioImage, Long> {

    List<PortfolioImage> findByBusinessConfigIdOrderByDisplayOrderAsc(Long businessConfigId);

    int countByBusinessConfigId(Long businessConfigId);
}
