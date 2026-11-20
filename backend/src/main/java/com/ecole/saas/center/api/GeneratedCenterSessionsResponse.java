package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterSessionService;
import java.util.List;

public record GeneratedCenterSessionsResponse(
        int createdCount,
        int skippedCount,
        List<CenterSessionResponse> sessions) {

    static GeneratedCenterSessionsResponse from(CenterSessionService.GeneratedSessionsResult result) {
        List<CenterSessionResponse> responses = result.sessions().stream()
                .map(CenterSessionResponse::from)
                .toList();
        return new GeneratedCenterSessionsResponse(responses.size(), result.skippedCount(), responses);
    }
}
