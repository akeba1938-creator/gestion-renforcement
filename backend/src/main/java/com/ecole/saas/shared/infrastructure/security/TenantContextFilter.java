package com.ecole.saas.shared.infrastructure.security;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Alimente {@link TenantContext} uniquement à partir du JWT authentifié côté serveur.
 * N'inspecte jamais aucun en-tête, paramètre de requête, cookie ou corps envoyé par le client.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        TenantContext.clear();
        MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth && authentication.isAuthenticated()) {
                Jwt jwt = jwtAuth.getToken();
                String subjectType = jwt.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE);
                String tenantIdClaim = jwt.getClaimAsString(JwtClaims.CLAIM_TENANT_ID);
                if (JwtClaims.SUBJECT_TYPE_USER.equals(subjectType)
                        && tenantIdClaim != null
                        && !tenantIdClaim.isBlank()) {
                    TenantId tenantId = TenantId.of(UUID.fromString(tenantIdClaim));
                    TenantContext.set(tenantId);
                    MDC.put(MDC_TENANT_ID, tenantId.value().toString());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
