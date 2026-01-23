package com.turnofacil.service;

import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessConfigRepository businessConfigRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       BusinessConfigRepository businessConfigRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessConfigRepository = businessConfigRepository;
    }

    // Registro de nuevo negocio (admin)
    @Transactional
    public User registerNewBusiness(User user) {
        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Ya existe un negocio con ese email");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);

        // CREAR AUTOMÁTICAMENTE EL BUSINESS CONFIG
        BusinessConfig config = getBusinessConfig(user, savedUser);

        businessConfigRepository.save(config);  // GUARDAR

        return savedUser;
    }

    private static @NonNull BusinessConfig getBusinessConfig(User user, User savedUser) {
        BusinessConfig config = new BusinessConfig();
        config.setUser(savedUser);
        config.setBusinessName(user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : savedUser.getEmail().split("@")[0]); // fallback al email sin dominio
        config.setSlotDurationMinutes(30);
        config.setOpeningTime("09:00");
        config.setClosingTime("20:00");
        config.setWorkingDays("1,2,3,4,5"); // lunes a viernes
        config.setTimezone("Europe/Madrid");
        return config;
    }

    // Validaciones rápidas (para usar en controladores o formularios)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    // ------------------------------------------------------------
    // Implementación obligatoria de UserDetailsService
    // ------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No se encontró el usuario: " + email));
    }

    // ------------------------------------------------------------
    // Métodos útiles extra (ya los vas a necesitar)
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public User getCurrentBusiness(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Negocio no encontrado"));
    }

    @Transactional(readOnly = true)
    public User getCurrentBusinessByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Negocio no encontrado"));
    }
}