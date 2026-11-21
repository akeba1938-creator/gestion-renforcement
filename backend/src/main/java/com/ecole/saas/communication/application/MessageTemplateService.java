package com.ecole.saas.communication.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CommunicationConflictException;
import com.ecole.saas.communication.domain.CommunicationForbiddenException;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.MessageTemplate;
import com.ecole.saas.communication.domain.SmsSegmentCalculator;
import com.ecole.saas.communication.domain.TemplateRenderer;
import com.ecole.saas.communication.infrastructure.MessageTemplateRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageTemplateService {

    private final MessageTemplateRepository templateRepository;
    private final CommunicationSubscriptionGuard subscriptionGuard;
    private final CommunicationScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;

    public MessageTemplateService(
            MessageTemplateRepository templateRepository,
            CommunicationSubscriptionGuard subscriptionGuard,
            CommunicationScopeGuard scopeGuard,
            AuditLogPort auditLogPort) {
        this.templateRepository = templateRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public MessageTemplate createTemplate(
            String code,
            String name,
            String description,
            MessageCategory category,
            CommunicationChannel channel,
            String language,
            String subjectTemplate,
            String bodyTemplate,
            long estimatedUnitCostXof) {
        subscriptionGuard.requireWriteAccess();
        String normalizedCode = MessageTemplate.normalizeCode(code);
        if (templateRepository.existsByCode(normalizedCode)) {
            throw new CommunicationConflictException(
                    "template-already-exists",
                    "Un modèle de message avec le code " + normalizedCode + " existe déjà.");
        }
        MessageTemplate template = new MessageTemplate(
                normalizedCode,
                name,
                description,
                category,
                channel,
                language,
                subjectTemplate,
                bodyTemplate,
                estimatedUnitCostXof);
        MessageTemplate saved = templateRepository.save(template);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_TEMPLATE_CREATED,
                "MESSAGE_TEMPLATE",
                saved.getId(),
                "Création du modèle de message " + saved.getCode() + " (" + saved.getChannel() + ").",
                Map.of(
                        "code", saved.getCode(),
                        "channel", saved.getChannel().name(),
                        "category", saved.getCategory().name(),
                        "estimatedUnitCostXof", saved.getEstimatedUnitCostXof())));
        return saved;
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public MessageTemplate updateTemplate(
            UUID templateId,
            String name,
            String description,
            MessageCategory category,
            CommunicationChannel channel,
            String language,
            String subjectTemplate,
            String bodyTemplate,
            long estimatedUnitCostXof) {
        subscriptionGuard.requireWriteAccess();
        MessageTemplate template = requireTemplate(templateId);
        template.update(
                name,
                description,
                category,
                channel,
                language,
                subjectTemplate,
                bodyTemplate,
                estimatedUnitCostXof);
        MessageTemplate saved = templateRepository.save(template);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_TEMPLATE_UPDATED,
                "MESSAGE_TEMPLATE",
                saved.getId(),
                "Mise à jour du modèle de message " + saved.getCode() + ".",
                Map.of(
                        "code", saved.getCode(),
                        "channel", saved.getChannel().name(),
                        "category", saved.getCategory().name())));
        return saved;
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public MessageTemplate setActive(UUID templateId, boolean active) {
        subscriptionGuard.requireWriteAccess();
        MessageTemplate template = requireTemplate(templateId);
        if (active) {
            template.activate();
        } else {
            template.deactivate();
        }
        MessageTemplate saved = templateRepository.save(template);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_TEMPLATE_STATUS_CHANGED,
                "MESSAGE_TEMPLATE",
                saved.getId(),
                (active ? "Activation" : "Désactivation") + " du modèle de message " + saved.getCode() + ".",
                Map.of("code", saved.getCode(), "active", active)));
        return saved;
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<MessageTemplate> listTemplates(
            CommunicationChannel channel,
            MessageCategory category,
            Boolean active,
            String query,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        Boolean effectiveActive = scopeGuard.hasFullAdminScope() ? active : Boolean.TRUE;
        Specification<MessageTemplate> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (effectiveActive != null) {
                predicates.add(cb.equal(root.get("active"), effectiveActive));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return templateRepository.findAll(spec, pageable);
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public MessageTemplate getTemplate(UUID templateId) {
        subscriptionGuard.requireReadAccess();
        MessageTemplate template = requireTemplate(templateId);
        if (!scopeGuard.hasFullAdminScope() && !template.isActive()) {
            throw new CommunicationForbiddenException(
                    "Vous ne pouvez consulter que des modèles de messages actifs.");
        }
        return template;
    }

    @PreAuthorize(CommunicationRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public TemplatePreview previewTemplate(UUID templateId, Map<String, String> variables, boolean strict) {
        subscriptionGuard.requireReadAccess();
        MessageTemplate template = requireTemplate(templateId);
        if (!scopeGuard.hasFullAdminScope() && !template.isActive()) {
            throw new CommunicationForbiddenException(
                    "Vous ne pouvez prévisualiser que des modèles de messages actifs.");
        }
        String renderedSubject = TemplateRenderer.render(template.getSubjectTemplate(), variables, strict);
        String renderedBody = TemplateRenderer.render(template.getBodyTemplate(), variables, strict);
        int smsSegments = template.getChannel() == CommunicationChannel.SMS
                ? SmsSegmentCalculator.computeSegments(renderedBody)
                : 0;
        long estimatedCostXof = SmsSegmentCalculator.computeDeliveryCostXof(
                template.getChannel(), renderedBody, template.getEstimatedUnitCostXof());
        return new TemplatePreview(
                template.getId(),
                template.getCode(),
                template.getChannel(),
                renderedSubject,
                renderedBody,
                smsSegments,
                estimatedCostXof);
    }

    MessageTemplate requireTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new CommunicationNotFoundException("Modèle de message introuvable."));
    }

    public record TemplatePreview(
            UUID templateId,
            String code,
            CommunicationChannel channel,
            String renderedSubject,
            String renderedBody,
            int smsSegments,
            long estimatedCostXof) {
    }
}
