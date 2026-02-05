package com.turnofacil.config;

import com.turnofacil.security.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final TenantFilter tenantFilter;

    public SecurityConfig(TenantFilter tenantFilter) {
        this.tenantFilter = tenantFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ==================== CSRF PROTECTION ====================
                .csrf(csrf -> {
                        // Handler para que el token esté disponible inmediatamente (no deferred)
                        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                        requestHandler.setCsrfRequestAttributeName(null); // Fuerza evaluación inmediata del token

                        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .csrfTokenRequestHandler(requestHandler)
                            // SSE y webhooks no pueden enviar tokens CSRF
                            .ignoringRequestMatchers("/api/notifications/stream", "/webhook/stripe");
                })

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
                                        "connect-src 'self' https://cdn.jsdelivr.net; " +
                                        "frame-src 'self' https://www.google.com https://*.google.com https://*.google.com.ar https://*.goo.gl; " +
                                        "frame-ancestors 'none'"
                                )
                        )
                        // Referrer Policy
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        // Permissions-Policy header (reemplaza Feature-Policy)
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy",
                                "geolocation=(), microphone=(), camera=()"
                        ))
                )

                // ==================== AUTHORIZATION ====================
                .authorizeHttpRequests(auth -> auth
                        // PÁGINAS PÚBLICAS (cualquiera puede acceder)
                        .requestMatchers("/", "/home", "/auth/**", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/public/**").permitAll()  // páginas públicas (landing, reservas, gestión turno)
                        .requestMatchers("/legal/**", "/pricing", "/faq").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/webhook/stripe").permitAll()
                        .requestMatchers("/error/**").permitAll()        // páginas de error
                        .requestMatchers("/platform/**").hasRole("SUPER_ADMIN") // super admin solamente
                        .requestMatchers("/admin/**").hasRole("ADMIN")   // solo admins (negocios)
                        .requestMatchers("/api/**").hasRole("ADMIN")     // API REST para admin
                        .anyRequest().authenticated()
                )

                // ==================== FORM LOGIN ====================
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/auth/login?error=true")
                        .permitAll()
                )

                // ==================== LOGOUT ====================
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/auth/login?logout")
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )

                // ==================== SESSION MANAGEMENT ====================
                .sessionManagement(session -> session
                        .maximumSessions(2)                    // máximo 2 sesiones por usuario
                        .expiredUrl("/auth/login?expired=true")     // URL si la sesión expira
                )

                // ==================== TENANT FILTER ====================
                .addFilterAfter(tenantFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
