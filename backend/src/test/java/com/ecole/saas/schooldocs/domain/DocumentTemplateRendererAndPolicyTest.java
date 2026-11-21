package com.ecole.saas.schooldocs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.infrastructure.SimplePdfDocumentRenderer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTemplateRendererAndPolicyTest {

    @Test
    void shouldRenderSinglePassWithoutReEvaluatingInjectedPlaceholders() {
        String template = "Élève : {{student.fullName}} — Motif : {{reason}}";
        Map<String, String> vars = Map.of(
                "student.fullName", "{{reason}} Fatou Diop",
                "reason", "Assiduité exemplaire");

        Set<String> extracted = DocumentTemplateRenderer.extractVariables(template);
        assertThat(extracted).containsExactly("student.fullName", "reason");

        String rendered = DocumentTemplateRenderer.render(template, vars, true);
        assertThat(rendered).isEqualTo("Élève : {{reason}} Fatou Diop — Motif : Assiduité exemplaire");

        // Strict mode échoue si une variable manque
        assertThatThrownBy(() -> DocumentTemplateRenderer.render(template, Map.of("reason", "OK"), true))
                .isInstanceOf(SchoolDocValidationException.class)
                .hasMessageContaining("student.fullName");

        // Mode aperçu conserve les placeholders inconnus
        String preview = DocumentTemplateRenderer.renderPreservingUnknown(template, Map.of("reason", "OK"));
        assertThat(preview).isEqualTo("Élève : {{student.fullName}} — Motif : OK");
    }

    @Test
    void shouldFormatDocumentNumbersAndEnforceSecurityPolicy() {
        assertThat(DocumentNumberFormatter.format("ind", 2026, 7)).isEqualTo("DOC-IND-2026-000007");
        assertThatThrownBy(() -> DocumentNumberFormatter.format("IND", 1999, 1))
                .isInstanceOf(SchoolDocValidationException.class);
        assertThatThrownBy(() -> DocumentNumberFormatter.format("IND", 2026, 0))
                .isInstanceOf(SchoolDocValidationException.class);

        String token1 = DocumentSecurityPolicy.generateOpaqueVerificationToken();
        String token2 = DocumentSecurityPolicy.generateOpaqueVerificationToken();
        assertThat(token1).hasSizeGreaterThanOrEqualTo(42).doesNotContain("=").isNotEqualTo(token2);

        // Détection du type réel PDF
        byte[] fakePdf = "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DocumentSecurityPolicy.assertValidPdfBinary(fakePdf))
                .isInstanceOf(SchoolDocValidationException.class);

        // Anti-traversée de chemin
        assertThat(DocumentSecurityPolicy.buildSafePdfFileName("DOC-IND-2026-000001"))
                .isEqualTo("DOC-IND-2026-000001.pdf");
        assertThatThrownBy(() -> DocumentSecurityPolicy.validateStorageObjectKey("tenants/../etc/passwd"))
                .isInstanceOf(SchoolDocValidationException.class);
        assertThatThrownBy(() -> DocumentSecurityPolicy.validateStorageObjectKey("/absolute/path.pdf"))
                .isInstanceOf(SchoolDocValidationException.class);

        // Calcul exact du stockage en MiB entiers (plafond)
        assertThat(DocumentSecurityPolicy.bytesToCeilingMiB(0L)).isEqualTo(0L);
        assertThat(DocumentSecurityPolicy.bytesToCeilingMiB(1L)).isEqualTo(1L);
        assertThat(DocumentSecurityPolicy.bytesToCeilingMiB(1024L * 1024L)).isEqualTo(1L);
        assertThat(DocumentSecurityPolicy.bytesToCeilingMiB((1024L * 1024L) + 1L)).isEqualTo(2L);
    }

    @Test
    void shouldGenerateStructurallyValidPdf14WithFrenchAccentsAndDuplicateBanner() {
        SimplePdfDocumentRenderer renderer = new SimplePdfDocumentRenderer();
        String longBody = "Nous certifions que l'élève Aminata Ndiaye est régulièrement inscrite pour l'année "
                + "scolaire 2026-2027 et fait preuve d'une assiduité remarquable.\n\n"
                + "Paragraphe détaillé : ".repeat(35);

        DocumentRenderPayload payload = new DocumentRenderPayload(
                "Institution Notre-Dame de Dakar",
                "IND",
                "DOC-IND-2026-000042",
                DocumentType.SCHOOLING_CERTIFICATE,
                true,
                2,
                "Original égaré lors d'une démarche administrative",
                "Certificat de scolarité officiel",
                longBody,
                "Fait à Dakar pour servir et valoir ce que de droit.",
                "Aminata Ndiaye",
                "IND-2026-0001",
                "Année 2026-2027",
                "6ème A",
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2027, 1, 20),
                "opaqueTokenSample1234567890abcdef");

        byte[] pdf = renderer.renderPdf(payload);
        DocumentSecurityPolicy.assertValidPdfBinary(pdf);

        String ascii = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(ascii).startsWith("%PDF-1.4");
        assertThat(ascii).contains("/WinAnsiEncoding");
        assertThat(ascii).contains("DOC-IND-2026-000042");
        assertThat(ascii).contains("DUPLICATA");
        assertThat(ascii).contains("opaqueTokenSample1234567890abcdef");
        assertThat(ascii).contains("xref");
        assertThat(ascii).contains("startxref");
        assertThat(ascii).endsWith("%%EOF\n");
    }
}
