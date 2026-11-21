package com.ecole.saas.schooldocs.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.domain.DocumentTemplate;
import com.ecole.saas.schooldocs.domain.DocumentTemplateRenderer;
import com.ecole.saas.schooldocs.domain.SchoolDocConflictException;
import com.ecole.saas.schooldocs.domain.SchoolDocNotFoundException;
import com.ecole.saas.schooldocs.infrastructure.DocumentTemplateRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion CRUD, activation/désactivation logique et prévisualisation des modèles de documents scolaires.
 */
@Service
public class DocumentTemplateService {

    private final DocumentTemplateRepository templateRepository;
    private final SchoolDocsSubscriptionGuard subscriptionGuard;
    private final SchoolDocsAccessScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;

    public DocumentTemplateService(
            DocumentTemplateRepository templateRepository,
            SchoolDocsSubscriptionGuard subscriptionGuard,
            SchoolDocsAccessScopeGuard scopeGuard,
            AuditLogPort auditLogPort) {
        this.templateRepository = templateRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public DocumentTemplate createTemplate(
            String code,
            String name,
            String description,
            DocumentType documentType,
            String language,
            String titleTemplate,
            String bodyTemplate,
            String footerTemplate,
            Integer defaultValidityDays,
            long feeAmountXof) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        String normalizedCode = DocumentTemplate.normalizeCode(code);
        if (templateRepository.existsByCode(normalizedCode)) {
            throw new SchoolDocConflictException(
                    "schooldocs-template-already-exists",
                    "Un modèle de document avec le code '" + normalizedCode + "' existe déjà dans cet établissement.");
        }

        DocumentTemplate saved = templateRepository.save(new DocumentTemplate(
                normalizedCode,
                name,
                description,
                documentType,
                language,
                titleTemplate,
                bodyTemplate,
                footerTemplate,
                defaultValidityDays,
                feeAmountXof));

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_TEMPLATE_CREATED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                "Création d'un modèle de document scolaire.",
                Map.of(
                        "code", saved.getCode(),
                        "documentType", saved.getDocumentType().name(),
                        "feeAmountXof", saved.getFeeAmountXof())));
        return saved;
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public DocumentTemplate updateTemplate(
            UUID templateId,
            String name,
            String description,
            DocumentType documentType,
            String language,
            String titleTemplate,
            String bodyTemplate,
            String footerTemplate,
            Integer defaultValidityDays,
            long feeAmountXof) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Modèle de document introuvable."));

        template.update(
                name,
                description,
                documentType,
                language,
                titleTemplate,
                bodyTemplate,
                footerTemplate,
                defaultValidityDays,
                feeAmountXof);
        DocumentTemplate saved = templateRepository.save(template);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_TEMPLATE_UPDATED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                "Mise à jour d'un modèle de document scolaire.",
                Map.of(
                        "code", saved.getCode(),
                        "documentType", saved.getDocumentType().name(),
                        "feeAmountXof", saved.getFeeAmountXof())));
        return saved;
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public DocumentTemplate activateTemplate(UUID templateId) {
        return changeTemplateStatus(templateId, true);
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_WRITE_ROLES)
    @Transactional
    public DocumentTemplate deactivateTemplate(UUID templateId) {
        return changeTemplateStatus(templateId, false);
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public DocumentTemplate getTemplate(UUID templateId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Modèle de document introuvable."));
        if (!scopeGuard.isTemplateManager() && !template.isActive()) {
            throw new SchoolDocNotFoundException("Modèle de document introuvable.");
        }
        return template;
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<DocumentTemplate> listTemplates(
            DocumentType documentType,
            Boolean active,
            String search,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        // Forcer active = true hors rôles PROMOTER / DIRECTOR / CENTER_MANAGER / SECRETARY
        Boolean effectiveActive = scopeGuard.isTemplateManager() ? active : Boolean.TRUE;

        Specification<DocumentTemplate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (documentType != null) {
                predicates.add(cb.equal(root.get("documentType"), documentType));
            }
            if (effectiveActive != null) {
                predicates.add(cb.equal(root.get("active"), effectiveActive));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return templateRepository.findAll(spec, pageable);
    }

    @PreAuthorize(SchoolDocsRoles.TEMPLATE_READ_ROLES)
    @Transactional(readOnly = true)
    public TemplatePreviewResult previewTemplate(UUID templateId, Map<String, String> variables) {
        DocumentTemplate template = getTemplate(templateId);
        Set<String> declaredVariables = DocumentTemplateRenderer.extractVariables(
                template.getTitleTemplate(),
                template.getBodyTemplate(),
                template.getFooterTemplate());
        String renderedTitle = DocumentTemplateRenderer.renderPreservingUnknown(template.getTitleTemplate(), variables);
        String renderedBody = DocumentTemplateRenderer.renderPreservingUnknown(template.getBodyTemplate(), variables);
        String renderedFooter = DocumentTemplateRenderer.renderPreservingUnknown(template.getFooterTemplate(), variables);
        return new TemplatePreviewResult(template, renderedTitle, renderedBody, renderedFooter, declaredVariables);
    }

    private DocumentTemplate changeTemplateStatus(UUID templateId, boolean active) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Modèle de document introuvable."));
        if (active) {
            template.activate();
        } else {
            template.deactivate();
        }
        DocumentTemplate saved = templateRepository.save(template);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_TEMPLATE_STATUS_CHANGED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                active ? "Activation d'un modèle de document." : "Désactivation d'un modèle de document.",
                Map.of("code", saved.getCode(), "active", saved.isActive())));
        return saved;
    }

    public record TemplatePreviewResult(
            DocumentTemplate template,
            String renderedTitle,
            String renderedBody,
            String renderedFooter,
            Set<String> variables) {
    }
}
