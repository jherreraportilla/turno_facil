package com.turnofacil.security;

import com.turnofacil.model.User;
import com.turnofacil.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que setea el TenantContext con el ID del usuario autenticado
 * en cada request. Se limpia al finalizar para evitar leaks entre requests.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public TenantFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
                TenantContext.setTenantId(user.getId());
            } else if (auth != null && auth.isAuthenticated()
                       && auth.getPrincipal() instanceof String email
                       && !"anonymousUser".equals(email)) {
                userRepository.findByEmailIgnoreCase(email)
                        .ifPresent(user -> TenantContext.setTenantId(user.getId()));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
