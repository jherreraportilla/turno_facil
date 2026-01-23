// src/main/java/com/turnofacil/repository/UserRepository.java
package com.turnofacil.repository;

import com.turnofacil.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // -------------------------------------------------
    // Para Spring Security (login por email o username)
    // -------------------------------------------------
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    // -------------------------------------------------
    // Validaciones en registro
    // -------------------------------------------------
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);                   // Si usas username también

    // -------------------------------------------------
    // Búsqueda rápida por email (usado en forgot password, etc.)
    // -------------------------------------------------
    boolean existsByEmailIgnoreCase(String email);

    // -------------------------------------------------
    // Opcional pero muy útil: búsqueda case-insensitive
    // -------------------------------------------------
    Optional<User> findByEmailIgnoreCase(String email);
}