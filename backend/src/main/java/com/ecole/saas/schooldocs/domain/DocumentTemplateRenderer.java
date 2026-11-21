package com.ecole.saas.schooldocs.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moteur de rendu déterministe en domaine pur pour les modèles de documents avec placeholders {@code {{variable}}}.
 * Utilise un remplacement en une seule passe afin d'empêcher toute ré-évaluation de contenu injecté.
 */
public final class DocumentTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private DocumentTemplateRenderer() {
    }

    public static Set<String> extractVariables(String... templates) {
        if (templates == null || templates.length == 0) {
            return Set.of();
        }
        Set<String> variables = new LinkedHashSet<>();
        for (String template : templates) {
            if (template == null || template.isBlank()) {
                continue;
            }
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        }
        return Collections.unmodifiableSet(variables);
    }

    public static String render(String template, Map<String, String> variables, boolean strict) {
        if (template == null) {
            return null;
        }
        Map<String, String> safeVars = variables != null ? variables : Map.of();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder output = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeVars.get(key);
            if (value == null) {
                if (strict) {
                    missing.add(key);
                }
                matcher.appendReplacement(output, Matcher.quoteReplacement(""));
            } else {
                matcher.appendReplacement(output, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(output);
        if (!missing.isEmpty()) {
            throw new SchoolDocValidationException(
                    "Variables obligatoires manquantes pour le modèle de document : "
                            + String.join(", ", missing) + ".");
        }
        return output.toString().trim();
    }

    public static String renderPreservingUnknown(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Map<String, String> safeVars = variables != null ? variables : Map.of();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeVars.get(key);
            if (value == null) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(output, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(output);
        return output.toString().trim();
    }
}
