package com.turnofacil.service;

import com.turnofacil.dto.BillingProfileDto;
import com.turnofacil.model.BillingProfile;
import com.turnofacil.model.User;
import com.turnofacil.repository.BillingProfileRepository;
import com.turnofacil.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BillingProfileService {

    private final BillingProfileRepository billingProfileRepo;
    private final UserRepository userRepo;

    public BillingProfileService(BillingProfileRepository billingProfileRepo, UserRepository userRepo) {
        this.billingProfileRepo = billingProfileRepo;
        this.userRepo = userRepo;
    }

    /**
     * Obtiene el perfil de facturación de un usuario
     */
    @Transactional(readOnly = true)
    public Optional<BillingProfile> getByUserId(Long userId) {
        return billingProfileRepo.findByUserId(userId);
    }

    /**
     * Verifica si un usuario tiene perfil de facturación configurado
     */
    @Transactional(readOnly = true)
    public boolean hasProfile(Long userId) {
        return billingProfileRepo.existsByUserId(userId);
    }

    /**
     * Crea o actualiza el perfil de facturación
     */
    @Transactional
    public BillingProfile saveProfile(Long userId, BillingProfileDto dto) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        BillingProfile profile = billingProfileRepo.findByUserId(userId)
            .orElseGet(() -> {
                BillingProfile newProfile = new BillingProfile();
                newProfile.setUser(user);
                return newProfile;
            });

        // Validar NIF/CIF único
        if (!profile.getTaxId().equals(dto.taxId())) {
            billingProfileRepo.findByTaxId(dto.taxId()).ifPresent(existing -> {
                if (!existing.getId().equals(profile.getId())) {
                    throw new IllegalArgumentException("Ya existe un negocio con ese NIF/CIF");
                }
            });
        }

        dto.applyTo(profile);
        return billingProfileRepo.save(profile);
    }

    /**
     * Crea un perfil de facturación inicial (sin datos fiscales completos)
     */
    @Transactional
    public BillingProfile createInitialProfile(User user, String businessName) {
        if (billingProfileRepo.existsByUserId(user.getId())) {
            return billingProfileRepo.findByUserId(user.getId()).orElseThrow();
        }

        BillingProfile profile = new BillingProfile();
        profile.setUser(user);
        profile.setLegalName(businessName);
        profile.setTaxId("PENDIENTE"); // Marcador temporal
        profile.setAddressLine1("PENDIENTE");
        profile.setCity("PENDIENTE");
        profile.setPostalCode("00000");
        profile.setProvince("PENDIENTE");
        profile.setCountry("ES");

        return billingProfileRepo.save(profile);
    }

    /**
     * Verifica si el perfil tiene todos los datos fiscales completos
     */
    @Transactional(readOnly = true)
    public boolean isProfileComplete(Long userId) {
        Optional<BillingProfile> profileOpt = billingProfileRepo.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            return false;
        }

        BillingProfile profile = profileOpt.get();
        return profile.getTaxId() != null && !profile.getTaxId().equals("PENDIENTE")
            && profile.getLegalName() != null && !profile.getLegalName().isBlank()
            && profile.getAddressLine1() != null && !profile.getAddressLine1().equals("PENDIENTE")
            && profile.getCity() != null && !profile.getCity().equals("PENDIENTE")
            && profile.getPostalCode() != null && !profile.getPostalCode().equals("00000")
            && profile.getProvince() != null && !profile.getProvince().equals("PENDIENTE");
    }
}
