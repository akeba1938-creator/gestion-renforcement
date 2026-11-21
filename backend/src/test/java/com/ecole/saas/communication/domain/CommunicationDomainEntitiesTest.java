package com.ecole.saas.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunicationDomainEntitiesTest {

    @Test
    void shouldValidateTemplateRulesAndLifecycle() {
        MessageTemplate smsTemplate = new MessageTemplate(
                "abs_alert",
                "Alerte absence",
                "Modèle SMS d'absence",
                MessageCategory.ABSENCE_ALERT,
                CommunicationChannel.SMS,
                "fr",
                null,
                "Bonjour {{recipientName}}, {{studentName}} est absent(e).",
                25L);
        assertThat(smsTemplate.getCode()).isEqualTo("ABS_ALERT");
        assertThat(smsTemplate.isActive()).isTrue();
        assertThat(smsTemplate.requiredVariables()).containsExactly("recipientName", "studentName");

        smsTemplate.deactivate();
        assertThatThrownBy(smsTemplate::assertActive)
                .isInstanceOf(CommunicationConflictException.class);

        // EMAIL et IN_APP exigent un objet
        assertThatThrownBy(() -> new MessageTemplate(
                "MAIL_1",
                "Mail sans objet",
                null,
                MessageCategory.GENERAL,
                CommunicationChannel.EMAIL,
                "fr",
                " ",
                "Contenu",
                0L)).isInstanceOf(CommunicationValidationException.class);
    }

    @Test
    void shouldEnforceConsentPolicyForChannelOptOutMarketingAndMissingAddress() {
        Instant now = Instant.now();
        UUID guardianId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ContactPreference pref = new ContactPreference(
                RecipientType.GUARDIAN,
                guardianId,
                true,
                false, // SMS désactivé
                true,
                true,
                false, // Opt-out marketing
                CommunicationChannel.IN_APP,
                "fr",
                LocalTime.of(21, 0),
                LocalTime.of(7, 0),
                ConsentSource.SELF_SERVICE,
                now,
                userId);

        // SMS désactivé -> bloqué même sur alerte transactionnelle
        ConsentPolicy.ConsentDecision smsBlocked = ConsentPolicy.evaluate(
                CommunicationChannel.SMS,
                MessageCategory.ABSENCE_ALERT,
                false,
                pref,
                true,
                "+221770001122",
                "parent@ecole.sn",
                userId);
        assertThat(smsBlocked.allowed()).isFalse();
        assertThat(smsBlocked.failureCode()).isEqualTo(DeliveryFailureCode.CHANNEL_OPTED_OUT);

        // Opt-out marketing bloque les annonces
        ConsentPolicy.ConsentDecision announcementBlocked = ConsentPolicy.evaluate(
                CommunicationChannel.IN_APP,
                MessageCategory.ANNOUNCEMENT,
                true,
                pref,
                true,
                "+221770001122",
                "parent@ecole.sn",
                userId);
        assertThat(announcementBlocked.allowed()).isFalse();
        assertThat(announcementBlocked.failureCode()).isEqualTo(DeliveryFailureCode.MARKETING_OPTED_OUT);

        // Notification transactionnelle autorisée sur IN_APP malgré marketingOptIn = false
        ConsentPolicy.ConsentDecision receiptAllowed = ConsentPolicy.evaluate(
                CommunicationChannel.IN_APP,
                MessageCategory.PAYMENT_RECEIPT,
                false,
                pref,
                true,
                "+221770001122",
                "parent@ecole.sn",
                userId);
        assertThat(receiptAllowed.allowed()).isTrue();
        assertThat(receiptAllowed.resolvedAddress()).isEqualTo("in-app:" + userId);

        // Coordonnée manquante -> bloqué proprement
        ConsentPolicy.ConsentDecision missingEmail = ConsentPolicy.evaluate(
                CommunicationChannel.EMAIL,
                MessageCategory.GENERAL,
                false,
                pref,
                true,
                "+221770001122",
                null,
                userId);
        assertThat(missingEmail.allowed()).isFalse();
        assertThat(missingEmail.failureCode()).isEqualTo(DeliveryFailureCode.MISSING_CONTACT_ADDRESS);
    }

    @Test
    void shouldEnforceDeliveryStatusMachineAndImmutableDeliveryEvent() {
        Instant now = Instant.now();
        MessageDelivery delivery = new MessageDelivery(
                UUID.randomUUID(),
                RecipientType.GUARDIAN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Amadou Diallo",
                MessageCategory.ABSENCE_ALERT,
                CommunicationChannel.SMS,
                "+221770001122",
                null,
                "Absence signalée",
                25L,
                now);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        delivery.markSent("LOCAL_STUB_SMS", "sms-1", 25L, now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);

        // Échec technique -> relance autorisée
        delivery.markFailed("LOCAL_STUB_SMS", DeliveryFailureCode.GATEWAY_ERROR, "Timeout simulé", false, now);
        assertThat(delivery.isRetryableFailure()).isTrue();
        delivery.requeueForRetry(now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);

        // Passage vers DELIVERED puis READ (terminal et idempotent)
        delivery.markDelivered("LOCAL_STUB_SMS", "sms-2", 25L, now);
        delivery.markRead(now);
        delivery.markRead(now); // idempotent
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.READ);
        assertThatThrownBy(() -> delivery.markFailed(
                        "LOCAL_STUB_SMS", DeliveryFailureCode.GATEWAY_ERROR, "Impossible", false, now))
                .isInstanceOf(CommunicationConflictException.class);

        // Opt-out non relançable
        MessageDelivery optedOut = new MessageDelivery(
                UUID.randomUUID(),
                RecipientType.GUARDIAN,
                UUID.randomUUID(),
                null,
                null,
                "Fatou Ndiaye",
                MessageCategory.ANNOUNCEMENT,
                CommunicationChannel.SMS,
                null,
                null,
                "Annonce",
                0L,
                now);
        optedOut.markFailed(null, DeliveryFailureCode.CHANNEL_OPTED_OUT, "Opt-out", false, now);
        assertThat(optedOut.isRetryableFailure()).isFalse();
        assertThatThrownBy(() -> optedOut.requeueForRetry(now))
                .isInstanceOf(CommunicationConflictException.class);

        // Immutabilité de DeliveryEvent
        DeliveryEvent event = new DeliveryEvent(
                UUID.randomUUID(), 1, null, DeliveryStatus.QUEUED, null, "QUEUED", null, "Initial", now);
        assertThatThrownBy(event::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(event::onRemove).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldValidateCampaignAudienceTargetRulesAndQuietHours() {
        assertThatThrownBy(() -> new CommunicationCampaign(
                "MSG-IND-2026-000001",
                "idem-1",
                "a".repeat(64),
                CampaignOrigin.CAMPAIGN_MANUAL,
                MessageCategory.GENERAL,
                CommunicationChannel.SMS,
                null,
                "Titre",
                AudienceType.CLASS_ROOM_GUARDIANS,
                null, // cible manquante
                GuardianTargetingMode.ALL_ACTIVE,
                null,
                "Corps",
                false,
                null,
                25L,
                null,
                null,
                null,
                UUID.randomUUID())).isInstanceOf(CommunicationValidationException.class);

        assertThatThrownBy(() -> new ContactPreference(
                RecipientType.GUARDIAN,
                UUID.randomUUID(),
                true,
                true,
                true,
                true,
                true,
                CommunicationChannel.SMS,
                "fr",
                LocalTime.of(22, 0),
                null, // heure de fin manquante
                ConsentSource.ADMIN_BACKOFFICE,
                Instant.now(),
                UUID.randomUUID())).isInstanceOf(CommunicationValidationException.class);
    }
}
