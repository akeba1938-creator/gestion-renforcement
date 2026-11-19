package com.ecole.saas.shared.api;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Gestionnaire global d'erreurs retournant systématiquement un {@link ProblemDetail} RFC 9457
 * en français, sans jamais exposer ni journaliser de secrets ou de données personnelles.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalProblemHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = mapStatus(ex.getCategory());
        return createProblem(
                status,
                "urn:ecole:problem:" + ex.getProblemCode(),
                ex.getTitle(),
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "Valeur invalide."))
                .toList();
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "urn:ecole:problem:validation-error",
                "Données invalides",
                "Certains champs de la requête sont invalides.",
                request);
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(
                        v.getPropertyPath().toString(),
                        v.getMessage() != null ? v.getMessage() : "Valeur invalide."))
                .toList();
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "urn:ecole:problem:validation-error",
                "Données invalides",
                "Certains paramètres de la requête sont invalides.",
                request);
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingRequestCookieException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "urn:ecole:problem:malformed-request",
                "Requête invalide",
                "La requête est incomplète ou mal formée.",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "urn:ecole:problem:not-found",
                "Ressource introuvable",
                "La ressource demandée est introuvable.",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "urn:ecole:problem:method-not-allowed",
                "Méthode non autorisée",
                "La méthode HTTP utilisée n'est pas autorisée pour cette ressource.",
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return createProblem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "urn:ecole:problem:unsupported-media-type",
                "Type de contenu non pris en charge",
                "Le type de contenu envoyé n'est pas pris en charge.",
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.UNAUTHORIZED,
                "urn:ecole:problem:authentication-required",
                "Authentification requise",
                "Authentification requise ou jeton invalide.",
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "urn:ecole:problem:access-denied",
                "Accès refusé",
                "Vous ne disposez pas des autorisations nécessaires pour accéder à cette ressource.",
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "urn:ecole:problem:invalid-argument",
                "Paramètre invalide",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                        ? ex.getMessage()
                        : "Un paramètre de la requête est invalide.",
                request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.CONFLICT,
                "urn:ecole:problem:invalid-state",
                "Opération impossible",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                        ? ex.getMessage()
                        : "L'état courant ne permet pas d'effectuer cette opération.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // Ne journaliser ni corps, ni en-têtes, ni message potentiellement sensible.
        log.error(
                "Erreur interne non gérée sur {} {} (type={})",
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName());
        return createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:ecole:problem:internal-error",
                "Erreur interne",
                "Une erreur interne est survenue.",
                request);
    }

    private static ProblemDetail createProblem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private static HttpStatus mapStatus(ErrorCategory category) {
        return switch (category) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case LOCKED -> HttpStatus.LOCKED;
            case UNPROCESSABLE_ENTITY -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    public record FieldViolation(String field, String message) {
    }
}
