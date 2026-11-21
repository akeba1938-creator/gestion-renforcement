package com.ecole.saas.communication.application;

import com.ecole.saas.communication.AbsenceNotificationCommand;
import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CampaignSummary;
import com.ecole.saas.communication.CommunicationPort;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.NotificationDispatchResult;
import com.ecole.saas.communication.PaymentDueReminderNotificationCommand;
import com.ecole.saas.communication.PaymentReceiptNotificationCommand;
import com.ecole.saas.communication.ReportCardPublishedNotificationCommand;
import com.ecole.saas.communication.TriggerNotificationCommand;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique Spring Modulith {@link CommunicationPort}.
 */
@Service
public class CommunicationPortService implements CommunicationPort {

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CommunicationCampaignService campaignService;
    private final InAppInboxService inboxService;
    private final StudentDirectoryPort studentDirectoryPort;

    public CommunicationPortService(
            CommunicationCampaignService campaignService,
            InAppInboxService inboxService,
            StudentDirectoryPort studentDirectoryPort) {
        this.campaignService = campaignService;
        this.inboxService = inboxService;
        this.studentDirectoryPort = studentDirectoryPort;
    }

    @Override
    public NotificationDispatchResult sendNotification(TriggerNotificationCommand command) {
        TenantContext.requireCurrent();
        CommunicationCampaignService.CampaignDispatchView view =
                campaignService.createAndDispatchSystemNotification(
                        command.idempotencyKey(),
                        command.subject() != null && !command.subject().isBlank()
                                ? command.subject()
                                : "Notification " + command.category().name(),
                        command.category(),
                        command.channel(),
                        command.templateCode(),
                        command.audienceType(),
                        command.audienceTargetId(),
                        command.guardianTargetingMode(),
                        command.subject(),
                        command.body(),
                        command.variables(),
                        command.respectMarketingOptOut(),
                        command.sourceModule(),
                        command.sourceResourceType(),
                        command.sourceResourceId());
        return toDispatchResult(view);
    }

    @Override
    public NotificationDispatchResult notifyStudentAbsence(AbsenceNotificationCommand command) {
        TenantContext.requireCurrent();
        StudentSummary student = requireStudent(command.studentId());
        String studentName = student.firstName() + " " + student.lastName();
        String dateFormatted = FR_DATE.format(command.sessionDate());

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("studentName", studentName);
        vars.put("studentMatricule", student.matricule());
        vars.put("sessionDate", dateFormatted);
        vars.put("attendanceStatus", command.attendanceStatus());
        if (command.lateMinutes() != null) {
            vars.put("lateMinutes", String.valueOf(command.lateMinutes()));
        }
        if (command.className() != null && !command.className().isBlank()) {
            vars.put("className", command.className().trim());
        }

        String subject = "Alerte assiduité - " + studentName + " (" + dateFormatted + ")";
        String detail = "LATE".equalsIgnoreCase(command.attendanceStatus()) && command.lateMinutes() != null
                ? "a été signalé(e) en retard de " + command.lateMinutes() + " min"
                : "a été signalé(e) absent(e)";
        String classPart = (command.className() != null && !command.className().isBlank())
                ? " en classe de " + command.className().trim()
                : "";
        String fallbackBody = "Bonjour {{recipientName}}, l'élève " + studentName + classPart
                + " " + detail + " le " + dateFormatted + ". - {{schoolName}}";

        return sendNotification(new TriggerNotificationCommand(
                command.idempotencyKey(),
                MessageCategory.ABSENCE_ALERT,
                command.channel(),
                AudienceType.STUDENT_GUARDIANS,
                command.studentId(),
                GuardianTargetingMode.ALL_ACTIVE,
                command.templateCode(),
                command.templateCode() == null ? subject : null,
                command.templateCode() == null ? fallbackBody : null,
                vars,
                false,
                "attendance",
                "ATTENDANCE_RECORD",
                command.attendanceRecordId()));
    }

    @Override
    public NotificationDispatchResult notifyPaymentReceipt(PaymentReceiptNotificationCommand command) {
        TenantContext.requireCurrent();
        StudentSummary student = requireStudent(command.studentId());
        String studentName = student.firstName() + " " + student.lastName();

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("studentName", studentName);
        vars.put("studentMatricule", student.matricule());
        vars.put("receiptNumber", command.receiptNumber());
        vars.put("amountXof", String.valueOf(command.amountXof()));
        vars.put("remainingBalanceXof", String.valueOf(command.remainingBalanceXof()));
        if (command.paymentMethod() != null && !command.paymentMethod().isBlank()) {
            vars.put("paymentMethod", command.paymentMethod().trim());
        }

        String subject = "Reçu de paiement " + command.receiptNumber() + " - " + studentName;
        String fallbackBody = "Bonjour {{recipientName}}, nous confirmons l'encaissement de "
                + command.amountXof() + " FCFA pour " + studentName
                + " (Reçu " + command.receiptNumber() + "). Solde restant : "
                + command.remainingBalanceXof() + " FCFA. - {{schoolName}}";

        return sendNotification(new TriggerNotificationCommand(
                command.idempotencyKey(),
                MessageCategory.PAYMENT_RECEIPT,
                command.channel(),
                AudienceType.STUDENT_GUARDIANS,
                command.studentId(),
                GuardianTargetingMode.FINANCIAL_RESPONSIBLE_ONLY,
                command.templateCode(),
                command.templateCode() == null ? subject : null,
                command.templateCode() == null ? fallbackBody : null,
                vars,
                false,
                "finance",
                "PAYMENT",
                command.paymentId()));
    }

    @Override
    public NotificationDispatchResult notifyReportCardPublished(ReportCardPublishedNotificationCommand command) {
        TenantContext.requireCurrent();
        StudentSummary student = requireStudent(command.studentId());
        String studentName = student.firstName() + " " + student.lastName();

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("studentName", studentName);
        vars.put("studentMatricule", student.matricule());
        vars.put("periodLabel", command.periodLabel());
        if (command.className() != null) {
            vars.put("className", command.className());
        }
        if (command.generalAverageDisplay() != null) {
            vars.put("generalAverage", command.generalAverageDisplay());
        }
        if (command.classRank() != null) {
            vars.put("classRank", String.valueOf(command.classRank()));
        }
        if (command.mention() != null) {
            vars.put("mention", command.mention());
        }

        String subject = "Bulletin disponible (" + command.periodLabel() + ") - " + studentName;
        String avgPart = command.generalAverageDisplay() != null
                ? " Moyenne générale : " + command.generalAverageDisplay() + "."
                : "";
        String fallbackBody = "Bonjour {{recipientName}}, le bulletin de " + studentName
                + " pour la période " + command.periodLabel() + " est disponible." + avgPart + " - {{schoolName}}";

        return sendNotification(new TriggerNotificationCommand(
                command.idempotencyKey(),
                MessageCategory.REPORT_CARD_PUBLISHED,
                command.channel(),
                AudienceType.STUDENT_GUARDIANS,
                command.studentId(),
                GuardianTargetingMode.ALL_ACTIVE,
                command.templateCode(),
                command.templateCode() == null ? subject : null,
                command.templateCode() == null ? fallbackBody : null,
                vars,
                false,
                "grade",
                "REPORT_CARD",
                command.reportCardId()));
    }

    @Override
    public NotificationDispatchResult notifyPaymentDueReminder(PaymentDueReminderNotificationCommand command) {
        TenantContext.requireCurrent();
        StudentSummary student = requireStudent(command.studentId());
        String studentName = student.firstName() + " " + student.lastName();
        String dueFormatted = FR_DATE.format(command.dueDate());

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("studentName", studentName);
        vars.put("studentMatricule", student.matricule());
        vars.put("invoiceNumber", command.invoiceNumber());
        vars.put("amountDueXof", String.valueOf(command.amountDueXof()));
        vars.put("dueDate", dueFormatted);

        String subject = "Rappel d'échéance " + command.invoiceNumber() + " - " + studentName;
        String fallbackBody = "Bonjour {{recipientName}}, rappel d'échéance au " + dueFormatted
                + " pour " + studentName + " (Facture " + command.invoiceNumber()
                + ") : montant dû " + command.amountDueXof() + " FCFA. - {{schoolName}}";

        return sendNotification(new TriggerNotificationCommand(
                command.idempotencyKey(),
                MessageCategory.PAYMENT_DUE_REMINDER,
                command.channel(),
                AudienceType.STUDENT_GUARDIANS,
                command.studentId(),
                GuardianTargetingMode.FINANCIAL_RESPONSIBLE_ONLY,
                command.templateCode(),
                command.templateCode() == null ? subject : null,
                command.templateCode() == null ? fallbackBody : null,
                vars,
                false,
                "finance",
                "STUDENT_INVOICE",
                command.invoiceId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CampaignSummary> findCampaignById(UUID campaignId) {
        TenantContext.requireCurrent();
        return campaignService.findByIdInternal(campaignId).map(CommunicationPortService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadInAppNotifications(UUID userId) {
        TenantContext.requireCurrent();
        return inboxService.countUnreadInternal(userId);
    }

    private StudentSummary requireStudent(UUID studentId) {
        return studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new CommunicationNotFoundException("Élève introuvable."));
    }

    private static NotificationDispatchResult toDispatchResult(CommunicationCampaignService.CampaignDispatchView view) {
        CommunicationCampaign c = view.campaign();
        return new NotificationDispatchResult(
                c.getId(),
                c.getReference(),
                c.getCategory(),
                c.getChannel(),
                c.getStatus(),
                c.getTotalRecipients(),
                c.getSentCount() + c.getDeliveredCount() + c.getReadCount(),
                c.getFailedCount(),
                c.getActualCostXof(),
                view.idempotentReplay(),
                c.getDispatchedAt());
    }

    private static CampaignSummary toSummary(CommunicationCampaign c) {
        return new CampaignSummary(
                c.getId(),
                c.getReference(),
                c.getCategory(),
                c.getChannel(),
                c.getAudienceType(),
                c.getAudienceTargetId(),
                c.getStatus(),
                c.getTotalRecipients(),
                c.getQueuedCount(),
                c.getSentCount(),
                c.getDeliveredCount(),
                c.getFailedCount(),
                c.getReadCount(),
                c.getEstimatedCostXof(),
                c.getActualCostXof(),
                c.getCreatedAt(),
                c.getDispatchedAt());
    }
}
