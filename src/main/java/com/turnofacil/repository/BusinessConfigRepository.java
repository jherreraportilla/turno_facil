package com.turnofacil.repository;

import com.turnofacil.model.BusinessConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessConfigRepository extends JpaRepository<BusinessConfig, Long> {
    Optional<BusinessConfig> findByUserId(Long userId);
    Optional<BusinessConfig> findByUserUsername(String username); // para URL amigable
    Optional<BusinessConfig> findBySlug(String slug);
    List<BusinessConfig> findByEnableRemindersTrue();
}
