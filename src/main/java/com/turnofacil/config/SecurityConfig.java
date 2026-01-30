package com.turnofacil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ==================== CSRF PROTECTION ====================
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // SSE (Server-Sent Events) no puede enviar tokens CSRF
                        .ignoringRequestMatchers("/api/notifications/stream")
                )

                // ==================== SECURITY HEADERS ====================
                .headers(headers -> headers
                        // Prevenir clickjacking - no permitir iframe
                        .frameOptions(frame -> frame.deny())
                        // Prevenir MIME type sniffing
                        .contentTypeOptions(contentType -> {})
                        // XSS Protection (navegadores modernos)
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                        )
                        // Content Security Policy
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                                        "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com; " +
                                        "img-src 'self' data: https:; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'"
                                )
                        )
                        // Referrer Policy
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        // Permissions Policy (antes Feature-Policy)
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=()")
                        )
                )

                // ==================== AUTHORIZATION ====================
                .authorizeHttpRequests(auth -> auth
                        // PÁGINAS PÚBLICAS (cualquiera puede acceder)
                        .requestMatchers("/", "/home", "/login", "/register", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/public/book/**").permitAll()  // página de reservas pública
                        .requestMatchers("/error/**").permitAll()        // páginas de error
                        .requestMatchers("/admin/**").hasRole("ADMIN")   // solo admins (negocios)
                        .requestMatchers("/api/**").hasRole("ADMIN")     // API REST para admin
                        .anyRequest().authenticated()
                )

                // ==================== FORM LOGIN ====================
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // ==================== LOGOUT ====================
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )

                // ==================== SESSION MANAGEMENT ====================
                .sessionManagement(session -> session
                        .maximumSessions(2)                    // máximo 2 sesiones por usuario
                        .expiredUrl("/login?expired=true")     // URL si la sesión expira
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
