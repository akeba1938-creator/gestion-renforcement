package com.ecole.saas.identity.api;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token) {
}
