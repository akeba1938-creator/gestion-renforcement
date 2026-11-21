package com.ecole.saas.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.communication.CommunicationChannel;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemplateRendererAndSmsSegmentTest {

    @Test
    void shouldExtractAndRenderTemplateVariablesInSinglePassWithoutReEvaluation() {
        String template = "Bonjour {{ recipientName }}, reçu {{receiptNumber}} de {{amountXof}} FCFA. — {{schoolName}}";
        Set<String> vars = TemplateRenderer.extractVariables(template);
        assertThat(vars).containsExactly("recipientName", "receiptNumber", "amountXof", "schoolName");

        // Vérifie l'absence de double substitution (injection de placeholder ou de $1)
        String rendered = TemplateRenderer.render(
                template,
                Map.of(
                        "recipientName", "M. {{amountXof}} $1",
                        "receiptNumber", "REC-2026-000001",
                        "amountXof", "50000",
                        "schoolName", "École Indépendance"),
                true);

        assertThat(rendered)
                .isEqualTo("Bonjour M. {{amountXof}} $1, reçu REC-2026-000001 de 50000 FCFA. — École Indépendance");
    }

    @Test
    void shouldRejectMissingVariablesInStrictModeAndPreserveUnknownWhenRequested() {
        String template = "Bonjour {{recipientName}}, élève {{studentName}}";

        assertThatThrownBy(() -> TemplateRenderer.render(template, Map.of("studentName", "Awa"), true))
                .isInstanceOf(CommunicationValidationException.class)
                .hasMessageContaining("recipientName");

        String preserved = TemplateRenderer.renderPreservingUnknown(template, Map.of("studentName", "Awa"));
        assertThat(preserved).isEqualTo("Bonjour {{recipientName}}, élève Awa");
    }

    @Test
    void shouldComputeGsm7AndUnicodeSmsSegmentsAndExactXofCosts() {
        String shortGsm = "Bonjour, rappel de paiement 25000 FCFA.";
        assertThat(SmsSegmentCalculator.computeSegments(shortGsm)).isEqualTo(1);
        assertThat(SmsSegmentCalculator.computeDeliveryCostXof(CommunicationChannel.SMS, shortGsm, 25L))
                .isEqualTo(25L);

        String longGsm = "A".repeat(161);
        assertThat(SmsSegmentCalculator.computeSegments(longGsm)).isEqualTo(2);
        assertThat(SmsSegmentCalculator.computeDeliveryCostXof(CommunicationChannel.SMS, longGsm, 25L))
                .isEqualTo(50L);

        // Caractère Unicode hors GSM-7 (ex. emoji ou œ) -> limite 70 / 67
        String unicodeMsg = "Rappel urgent 😀 " + "a".repeat(70);
        assertThat(SmsSegmentCalculator.computeSegments(unicodeMsg)).isEqualTo(2);
        assertThat(SmsSegmentCalculator.computeDeliveryCostXof(CommunicationChannel.SMS, unicodeMsg, 25L))
                .isEqualTo(50L);

        // IN_APP et EMAIL ne multiplient pas par les segments
        assertThat(SmsSegmentCalculator.computeDeliveryCostXof(CommunicationChannel.IN_APP, longGsm, 0L))
                .isEqualTo(0L);
        assertThat(SmsSegmentCalculator.computeDeliveryCostXof(CommunicationChannel.WHATSAPP, longGsm, 15L))
                .isEqualTo(15L);

        // Protection contre le débordement entier FCFA
        assertThatThrownBy(() -> SmsSegmentCalculator.safeMultiply(Long.MAX_VALUE, 2))
                .isInstanceOf(CommunicationValidationException.class);
        assertThatThrownBy(() -> SmsSegmentCalculator.computeTotalCostXof(-1L, 10))
                .isInstanceOf(CommunicationValidationException.class);
    }

    @Test
    void shouldFormatSequentialReferencesAndComputeDeterministicHash() {
        assertThat(CommunicationReferenceFormatter.format("ind", 2026, 42))
                .isEqualTo("MSG-IND-2026-000042");
        assertThatThrownBy(() -> CommunicationReferenceFormatter.format("IND", 2026, 0))
                .isInstanceOf(CommunicationValidationException.class);

        String hash1 = CommunicationHashSupport.sha256Hex("payload-canonique");
        String hash2 = CommunicationHashSupport.sha256Hex("payload-canonique");
        assertThat(hash1).hasSize(64).isEqualTo(hash2);
    }
}
