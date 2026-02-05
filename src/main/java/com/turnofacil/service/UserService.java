package com.turnofacil.service;

import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.User;
import com.turnofacil.repository.BusinessConfigRepository;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.util.SlugUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
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
    private final ServiceService serviceService;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       BusinessConfigRepository businessConfigRepository,
                       @Lazy ServiceService serviceService,
                       @Lazy SubscriptionService subscriptionService,
                       @Lazy EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessConfigRepository = businessConfigRepository;
        this.serviceService = serviceService;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
    }

    // Registro de nuevo negocio (admin)
    @Transactional
    public User registerNewBusiness(User user) {
        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Ya existe un negocio con ese email");
        }

        // Validar contraseña segura
        validatePassword(user.getPassword(), user.getName(), user.getEmail());

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);

        // CREAR AUTOMATICAMENTE EL BUSINESS CONFIG
        BusinessConfig config = getBusinessConfig(user, savedUser);
        businessConfigRepository.save(config);

        // CREAR SERVICIOS POR DEFECTO
        serviceService.createDefaultServices(savedUser);

        // CREAR SUSCRIPCIÓN CON TRIAL DE 14 DÍAS
        subscriptionService.createTrialSubscription(savedUser);

        // EMAIL DE BIENVENIDA (async)
        emailService.sendWelcomeEmail(savedUser.getEmail(), config.getBusinessName());

        return savedUser;
    }

    private @NonNull BusinessConfig getBusinessConfig(User user, User savedUser) {
        BusinessConfig config = new BusinessConfig();
        config.setUser(savedUser);

        String businessName = user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : savedUser.getEmail().split("@")[0]; // fallback al email sin dominio

        config.setBusinessName(businessName);
        config.setSlug(generateUniqueSlug(businessName));
        config.setSlotDurationMinutes(30);
        config.setOpeningTime("09:00");
        config.setClosingTime("20:00");
        config.setWorkingDays("1,2,3,4,5"); // lunes a viernes
        config.setTimezone("Europe/Madrid");
        return config;
    }

    private String generateUniqueSlug(String businessName) {
        String baseSlug = SlugUtils.toSlug(businessName);
        String slug = baseSlug;
        int counter = 1;

        while (businessConfigRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
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

    /**
     * Actualiza los datos del perfil del usuario (nombre, email, teléfono)
     */
    @Transactional
    public User updateProfile(Long userId, String name, String email, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Verificar si el nuevo email ya existe (y no es el mismo usuario)
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Ya existe otro usuario con ese email");
        }

        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);

        return userRepository.save(user);
    }

    /**
     * Cambia la contraseña del usuario
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Verificar contraseña actual
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        // Validar nueva contraseña con requisitos de seguridad
        validatePassword(newPassword, user.getName(), user.getEmail());

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Valida que la contraseña cumpla con los requisitos de seguridad:
     * - Mínimo 8 caracteres
     * - Al menos una mayúscula
     * - Al menos un número
     * - Al menos un carácter especial
     * - No debe contener el nombre del negocio ni partes del email
     */
    private void validatePassword(String password, String businessName, String email) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra mayúscula");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un número");
        }

        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un carácter especial (!@#$%^&*...)");
        }

        // Verificar que no contenga el nombre del negocio
        if (businessName != null && !businessName.isBlank()) {
            String nameLower = businessName.toLowerCase().replaceAll("\\s+", "");
            String passLower = password.toLowerCase();
            if (nameLower.length() >= 3 && passLower.contains(nameLower)) {
                throw new IllegalArgumentException("La contraseña no debe contener el nombre del negocio");
            }
            // Verificar partes del nombre (palabras de 4+ caracteres)
            for (String part : businessName.toLowerCase().split("\\s+")) {
                if (part.length() >= 4 && passLower.contains(part)) {
                    throw new IllegalArgumentException("La contraseña no debe contener partes del nombre del negocio");
                }
            }
        }

        // Verificar que no contenga partes del email
        if (email != null && !email.isBlank()) {
            String emailUser = email.split("@")[0].toLowerCase();
            String passLower = password.toLowerCase();
            if (emailUser.length() >= 4 && passLower.contains(emailUser)) {
                throw new IllegalArgumentException("La contraseña no debe contener tu email o partes de él");
            }
        }
    }
}