package com.ecole.saas.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        writeProblem(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "urn:ecole:problem:authentication-required",
                "Authentification requise",
                "Authentification requise ou jeton invalide.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            writeProblem(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "urn:ecole:problem:csrf-invalid",
                    "Protection CSRF",
                    "Jeton CSRF manquant ou invalide.");
            return;
        }
        writeProblem(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "urn:ecole:problem:access-denied",
                "Accès refusé",
                "Vous ne disposez pas des autorisations nécessaires pour accéder à cette ressource.");
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String title,
            String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            problem.setProperty("requestId", requestId);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(problem));
    }
}
