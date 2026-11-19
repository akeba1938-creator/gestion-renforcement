package com.ecole.saas.identity.api;

import com.ecole.saas.identity.application.AuthOutcome;
import com.ecole.saas.identity.application.AuthService;
import com.ecole.saas.identity.application.MfaEnrollmentResult;
import com.ecole.saas.identity.application.UserProfile;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.AuthenticationFailedException;
import com.ecole.saas.shared.infrastructure.security.JwtClaims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody TenantLoginRequest request,
            HttpServletResponse response) {
        AuthOutcome outcome = authService.loginTenantUser(
                request.schoolCode(),
                request.email(),
                request.password());
        return buildAuthResponse(outcome, response);
    }

    @PostMapping("/platform/login")
    public ResponseEntity<AuthResponse> platformLogin(
            @Valid @RequestBody PlatformLoginRequest request,
            HttpServletResponse response) {
        AuthOutcome outcome = authService.loginPlatformAdmin(request.email(), request.password());
        return buildAuthResponse(outcome, response);
    }

    @PostMapping("/mfa/enroll")
    public ResponseEntity<MfaEnrollResponse> enrollMfa(JwtAuthenticationToken authentication) {
        AccountRef account = extractAccountRef(authentication);
        MfaEnrollmentResult enrollment = authService.enrollMfa(account);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MfaEnrollResponse.from(enrollment));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response) {
        AccountRef account = extractAccountRef(authentication);
        AuthOutcome outcome = authService.verifyMfa(account, request.code());
        return buildAuthResponse(outcome, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = extractRefreshCookie(request);
        try {
            AuthOutcome outcome = authService.refresh(cookieValue);
            return buildAuthResponse(outcome, response);
        } catch (AuthenticationFailedException ex) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = extractRefreshCookie(request);
        authService.logout(cookieValue);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        String token = csrfToken.getToken();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(
                        csrfToken.getHeaderName(),
                        csrfToken.getParameterName(),
                        token));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(JwtAuthenticationToken authentication) {
        AccountRef account = extractAccountRef(authentication);
        UserProfile profile = authService.getCurrentUser(account);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AuthenticatedUserResponse.from(profile));
    }

    private ResponseEntity<AuthResponse> buildAuthResponse(AuthOutcome outcome, HttpServletResponse response) {
        if (outcome.refreshCookieValue() != null) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.create(outcome.refreshCookieValue()).toString());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.from(outcome));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String targetName = refreshCookieFactory.cookieName();
        for (Cookie cookie : cookies) {
            if (targetName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static AccountRef extractAccountRef(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        UUID accountId = UUID.fromString(jwt.getSubject());
        String subjectType = jwt.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE);
        if (JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN.equals(subjectType)) {
            return AccountRef.platformAdmin(accountId);
        }
        UUID tenantId = UUID.fromString(jwt.getClaimAsString(JwtClaims.CLAIM_TENANT_ID));
        return AccountRef.user(accountId, tenantId);
    }
}
