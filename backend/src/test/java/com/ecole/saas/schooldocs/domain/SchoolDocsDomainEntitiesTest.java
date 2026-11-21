package com.ecole.saas.schooldocs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.schooldocs.DocumentEventType;
import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.DocumentValidityStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchoolDocsDomainEntitiesTest {

    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void shouldNormalizeTemplateCodeAndValidateFeeAndValidity() {
        DocumentTemplate template = new DocumentTemplate(
                "  cert_scol-2026  ",
                "Certificat de scolarité",
                "Modèle officiel",
                DocumentType.SCHOOLING_CERTIFICATE,
                "FR",
                "Certificat de scolarité {{academicYear.code}}",
                "Nous attestons que {{student.fullName}} est inscrit(e).",
                "Fait à Dakar le {{document.issueDate}}",
                90,
                1000L);

        assertThat(template.getCode()).isEqualTo("CERT_SCOL-2026");
        assertThat(template.getLanguage()).isEqualTo("fr");
        assertThat(template.getFeeAmountXof()).isEqualTo(1000L);
        assertThat(template.isActive()).isTrue();

        template.deactivate();
        assertThat(template.isActive()).isFalse();
        template.activate();
        assertThat(template.isActive()).isTrue();

        assertThatThrownBy(() -> new DocumentTemplate(
                "BAD CODE!",
                "Nom",
                null,
                DocumentType.SUMMONS,
                "fr",
                "Titre",
                "Corps",
                null,
                null,
                0L)).isInstanceOf(SchoolDocValidationException.class);

        assertThatThrownBy(() -> new DocumentTemplate(
                "CERT_NEG",
                "Nom",
                null,
                DocumentType.SUMMONS,
                "fr",
                "Titre",
                "Corps",
                null,
                null,
                -100L)).isInstanceOf(SchoolDocValidationException.class);

        assertThatThrownBy(() -> new DocumentTemplate(
                "CERT_DAYS",
                "Nom",
                null,
                DocumentType.SUMMONS,
                "fr",
                "Titre",
                "Corps",
                null,
                0,
                0L)).isInstanceOf(SchoolDocValidationException.class);
    }

    @Test
    void shouldManageIssuedDocumentValidityDuplicationAndRevocation() {
        UUID studentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        byte[] samplePdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        String sha256 = DocumentSecurityPolicy.sha256Hex(samplePdf);
        String storageKey = DocumentSecurityPolicy.buildTenantScopedStorageKey(tenantId, 2026, "DOC-IND-2026-000001");

        IssuedDocument original = IssuedDocument.issueOriginal(
                "DOC-IND-2026-000001",
                "idem-1",
                DocumentSecurityPolicy.sha256Hex("req-1"),
                DocumentSecurityPolicy.generateOpaqueVerificationToken(),
                null,
                DocumentType.SCHOOLING_CERTIFICATE,
                studentId,
                null,
                null,
                null,
                null,
                "Certificat de scolarité",
                "Corps officiel du certificat.",
                "Direction",
                DocumentSecurityPolicy.PDF_CONTENT_TYPE,
                "DOC-IND-2026-000001.pdf",
                storageKey,
                samplePdf.length,
                sha256,
                500L,
                T0,
                actorId,
                T0.plusSeconds(86400));

        assertThat(original.getStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(original.isDuplicate()).isFalse();
        assertThat(original.getEditionNumber()).isEqualTo(1);
        assertThat(original.evaluateValidity(T0.plusSeconds(3600))).isEqualTo(DocumentValidityStatus.VALID);
        assertThat(original.evaluateValidity(T0.plusSeconds(90000))).isEqualTo(DocumentValidityStatus.EXPIRED);

        // Révoquer avec motif trop court échoue
        assertThatThrownBy(() -> original.revoke(actorId, "non", T0.plusSeconds(100)))
                .isInstanceOf(SchoolDocValidationException.class);

        original.revoke(actorId, "Erreur de saisie sur la date de naissance", T0.plusSeconds(100));
        assertThat(original.getStatus()).isEqualTo(DocumentStatus.REVOKED);
        assertThat(original.evaluateValidity(T0.plusSeconds(200))).isEqualTo(DocumentValidityStatus.REVOKED);

        // Double révocation interdite
        assertThatThrownBy(() -> original.revoke(actorId, "Autre motif valide", T0.plusSeconds(300)))
                .isInstanceOf(SchoolDocConflictException.class);

        // Duplicata sur document révoqué interdit
        assertThatThrownBy(() -> IssuedDocument.issueDuplicate(
                original,
                "DOC-IND-2026-000002",
                "idem-2",
                DocumentSecurityPolicy.sha256Hex("req-2"),
                DocumentSecurityPolicy.generateOpaqueVerificationToken(),
                2,
                "Perte de l'original par la famille",
                "DOC-IND-2026-000002.pdf",
                DocumentSecurityPolicy.buildTenantScopedStorageKey(tenantId, 2026, "DOC-IND-2026-000002"),
                samplePdf.length,
                sha256,
                500L,
                T0.plusSeconds(400),
                actorId,
                null)).isInstanceOf(SchoolDocConflictException.class);
    }

    @Test
    void shouldEnforceImmutabilityOnDocumentContentAndDocumentEvent() {
        UUID docId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        String sha256 = DocumentSecurityPolicy.sha256Hex(pdf);
        String key = DocumentSecurityPolicy.buildTenantScopedStorageKey(tenantId, 2026, "DOC-IND-2026-000001");

        DocumentContent content = new DocumentContent(docId, key, DocumentSecurityPolicy.PDF_CONTENT_TYPE, sha256, pdf);
        // Copie défensive
        byte[] copy = content.getContent();
        copy[0] = 'X';
        assertThat(content.getContent()[0]).isEqualTo((byte) '%');

        assertThatThrownBy(content::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(content::onRemove).isInstanceOf(UnsupportedOperationException.class);

        // Empreinte invalide rejetée
        assertThatThrownBy(() -> new DocumentContent(
                docId, key, DocumentSecurityPolicy.PDF_CONTENT_TYPE, "0".repeat(64), pdf))
                .isInstanceOf(SchoolDocConflictException.class);

        DocumentEvent event = new DocumentEvent(docId, 1, DocumentEventType.ISSUED, UUID.randomUUID(), null, null, T0);
        assertThatThrownBy(event::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(event::onRemove).isInstanceOf(UnsupportedOperationException.class);
    }
}
