package com.turnofacil.service;

import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.util.SlugUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BusinessConfigService {

    private final BusinessConfigRepository configRepo;

    public BusinessConfigService(BusinessConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Transactional
    public BusinessConfig createDefaultConfig(User user) {
        BusinessConfig config = new BusinessConfig();
        config.setUser(user);
        // valores por defecto (puedes cambiarlos después)
        config.setBusinessName(user.getEmail().split("@")[0] + " - Turnos");
        config.setSlotDurationMinutes(30);
        config.setOpeningTime("09:00");
        config.setClosingTime("20:00");
        config.setWorkingDays("1,2,3,4,5"); // lunes a viernes
        config.setTimezone("America/Argentina/Buenos_Aires");
        return configRepo.save(config);
    }

    public BusinessConfig getByUserId(Long userId) {
        return configRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));
    }

    public BusinessConfig getByUsername(String username) {
        return configRepo.findByUserUsername(username)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado"));
    }

    public Optional<BusinessConfig> findBySlug(String slug) {
        return configRepo.findBySlug(slug);
    }

    @Transactional
    public BusinessConfig updateConfig(Long userId, BusinessConfig updatedConfig) {
        BusinessConfig existing = getByUserId(userId);

        existing.setBusinessName(updatedConfig.getBusinessName());
        existing.setOpeningTime(updatedConfig.getOpeningTime());
        existing.setClosingTime(updatedConfig.getClosingTime());
        existing.setSlotDurationMinutes(updatedConfig.getSlotDurationMinutes());
        existing.setWorkingDays(updatedConfig.getWorkingDays());
        existing.setTimezone(updatedConfig.getTimezone());
        existing.setSlug(SlugUtils.toSlug(updatedConfig.getBusinessName()));
        // aquí puedes añadir más campos en el futuro

        return configRepo.save(existing);
    }
}