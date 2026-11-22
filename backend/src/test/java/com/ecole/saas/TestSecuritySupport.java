package com.ecole.saas;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitaire de test positionnant temporairement un contexte Spring Security
 * autour des appels directs aux services applicatifs protégés par {@code @PreAuthorize}.
 */
final class TestSecuritySupport {

    private TestSecuritySupport() {
    }

    static <T> T runAsDirector(Supplier<T> action) {
        return runWithRole("ROLE_DIRECTOR", action);
    }

    static <T> T runAsAdminSaas(Supplier<T> action) {
        return runWithRole("ROLE_ADMIN_SAAS", action);
    }

    static void runAsAdminSaas(Runnable action) {
        runWithRole("ROLE_ADMIN_SAAS", () -> {
            action.run();
            return null;
        });
    }

    static <T> T runWithRole(String authority, Supplier<T> action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            UUID.randomUUID().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority(authority))));
            return action.get();
        } finally {
            if (previous == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previous);
            }
        }
    }
}
