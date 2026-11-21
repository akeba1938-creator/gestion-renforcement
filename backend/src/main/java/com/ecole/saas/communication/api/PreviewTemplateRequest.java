package com.ecole.saas.communication.api;

import java.util.Map;

public record PreviewTemplateRequest(
        Map<String, String> variables,
        Boolean strict) {
}
