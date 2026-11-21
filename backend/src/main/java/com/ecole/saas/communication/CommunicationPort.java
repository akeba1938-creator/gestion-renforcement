package com.ecole.saas.communication;

import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code communication}.
 * Permet aux modules applicatifs et orchestrateurs de déclencher des notifications multicanaux
 * idempotentes (absence, reçu de paiement, publication de bulletin, rappel d'échéance, etc.)
 * et de consulter l'état d'une campagne ou le compteur de messages non lus in-app,
 * sans exposer les entités internes ni les tables du module.
 */
public interface CommunicationPort {

    NotificationDispatchResult sendNotification(TriggerNotificationCommand command);

    NotificationDispatchResult notifyStudentAbsence(AbsenceNotificationCommand command);

    NotificationDispatchResult notifyPaymentReceipt(PaymentReceiptNotificationCommand command);

    NotificationDispatchResult notifyReportCardPublished(ReportCardPublishedNotificationCommand command);

    NotificationDispatchResult notifyPaymentDueReminder(PaymentDueReminderNotificationCommand command);

    Optional<CampaignSummary> findCampaignById(UUID campaignId);

    long countUnreadInAppNotifications(UUID userId);
}
