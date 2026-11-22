package com.ecole.saas.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecole.saas.shared.application.SqlLikePatterns;
import com.ecole.saas.shared.infrastructure.security.ProblemDetailSecurityHandlers;
import com.ecole.saas.shared.infrastructure.security.SecurityConfig;
import com.ecole.saas.shared.infrastructure.security.SecurityProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class Wave3HardeningUnitTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sqlLikePatternsShouldEscapeWildcardsAndBackslashes() {
        assertThat(SqlLikePatterns.escape(null)).isEmpty();
        assertThat(SqlLikePatterns.escape("")).isEmpty();
        assertThat(SqlLikePatterns.escape("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");

        assertThat(SqlLikePatterns.containsLower(null)).isNull();
        assertThat(SqlLikePatterns.containsLower("   ")).isNull();
        assertThat(SqlLikePatterns.containsLower("  Diop_100%\\X  "))
                .isEqualTo("%diop\\_100\\%\\\\x%");
    }

    @Test
    void globalProblemHandlerShouldMaskRawIllegalMessagesUseInjectedClockAndSafeInstanceUri() {
        GlobalProblemHandler handler = new GlobalProblemHandler(FIXED_CLOCK);

        MockHttpServletRequest badUriRequest = new MockHttpServletRequest("GET", "/api/v1/test with spaces");
        ProblemDetail argProblem = handler.handleIllegalArgument(
                new IllegalArgumentException("secret SQL fragment / internal detail"),
                badUriRequest);

        assertThat(argProblem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(argProblem.getDetail()).isEqualTo("Un paramètre de la requête est invalide.");
        assertThat(argProblem.getDetail()).doesNotContain("secret");
        assertThat(argProblem.getInstance()).isEqualTo(URI.create("/"));
        assertThat(argProblem.getProperties()).containsEntry("timestamp", FIXED_INSTANT.toString());
        assertThat(argProblem.getProperties().get("requestId")).asString().isNotBlank();

        MockHttpServletRequest validRequest = new MockHttpServletRequest("POST", "/api/v1/students");
        MDC.put("requestId", "req-trace-123");
        ProblemDetail stateProblem = handler.handleIllegalState(
                new IllegalStateException("internal state leak"),
                validRequest);

        assertThat(stateProblem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(stateProblem.getDetail()).isEqualTo("L'état courant ne permet pas d'effectuer cette opération.");
        assertThat(stateProblem.getDetail()).doesNotContain("internal state leak");
        assertThat(stateProblem.getInstance()).isEqualTo(URI.create("/api/v1/students"));
        assertThat(stateProblem.getProperties())
                .containsEntry("timestamp", FIXED_INSTANT.toString())
                .containsEntry("requestId", "req-trace-123");
    }

    @Test
    void securityHandlersShouldAlwaysIncludeRequestIdAndInjectedClockTimestamp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ProblemDetailSecurityHandlers handlers = new ProblemDetailSecurityHandlers(objectMapper, FIXED_CLOCK);

        MockHttpServletRequest req401 = new MockHttpServletRequest("GET", "/api/v1/students");
        MockHttpServletResponse res401 = new MockHttpServletResponse();
        handlers.commence(req401, res401, new BadCredentialsException("raw auth error"));

        assertThat(res401.getStatus()).isEqualTo(401);
        JsonNode body401 = objectMapper.readTree(res401.getContentAsString(StandardCharsets.UTF_8));
        JsonNode props401 = body401.has("properties") ? body401.get("properties") : body401;
        assertThat(props401.get("timestamp").asString()).isEqualTo(FIXED_INSTANT.toString());
        assertThat(props401.get("requestId").asString()).isNotBlank();
        assertThat(body401.get("detail").asString()).doesNotContain("raw auth error");

        MockHttpServletRequest req403 = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse res403 = new MockHttpServletResponse();
        handlers.handle(req403, res403, new MissingCsrfTokenException("missing"));

        assertThat(res403.getStatus()).isEqualTo(403);
        JsonNode body403 = objectMapper.readTree(res403.getContentAsString(StandardCharsets.UTF_8));
        JsonNode props403 = body403.has("properties") ? body403.get("properties") : body403;
        assertThat(body403.get("type").asString()).isEqualTo("urn:ecole:problem:csrf-invalid");
        assertThat(props403.get("requestId").asString()).isNotBlank();
        assertThat(props403.get("timestamp").asString()).isEqualTo(FIXED_INSTANT.toString());

        MockHttpServletRequest reqDenied = new MockHttpServletRequest("GET", "/api/v1/bad uri");
        MockHttpServletResponse resDenied = new MockHttpServletResponse();
        handlers.handle(reqDenied, resDenied, new AccessDeniedException("denied"));
        assertThat(resDenied.getStatus()).isEqualTo(403);
        JsonNode bodyDenied = objectMapper.readTree(resDenied.getContentAsString(StandardCharsets.UTF_8));
        assertThat(bodyDenied.get("instance").asString()).isEqualTo("/");
    }

    @Test
    void corsConfigurationSourceShouldBindConfiguredAllowedOrigins() {
        SecurityConfig config = new SecurityConfig();
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("", "ecole-saas"),
                new SecurityProperties.Mfa(""),
                new SecurityProperties.Cookie("ecole_refresh_token", true, "Strict", "/api/v1/auth"),
                new SecurityProperties.Csrf("XSRF-TOKEN", "X-XSRF-TOKEN"),
                new SecurityProperties.Cors(List.of("https://app.ecole.sn", " https://admin.ecole.sn ")));

        CorsConfigurationSource source = config.corsConfigurationSource(properties);
        CorsConfiguration apiCors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/students"));
        assertThat(apiCors).isNotNull();
        assertThat(apiCors.getAllowedOrigins()).containsExactly("https://app.ecole.sn", "https://admin.ecole.sn");
        assertThat(apiCors.getAllowCredentials()).isTrue();
        assertThat(apiCors.getAllowedHeaders()).contains("Authorization", "X-XSRF-TOKEN");

        CorsConfiguration healthCors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/actuator/health"));
        assertThat(healthCors).isNull();
    }
}
