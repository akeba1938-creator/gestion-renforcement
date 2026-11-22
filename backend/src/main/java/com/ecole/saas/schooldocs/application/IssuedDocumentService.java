package com.ecole.saas.schooldocs.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.family.StudentGuardianSummary;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.schooldocs.DocumentEventType;
import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.DocumentValidityStatus;
import com.ecole.saas.schooldocs.application.SchoolDocsAccessScopeGuard.CallerReadScope;
import com.ecole.saas.schooldocs.application.SchoolDocsAccessScopeGuard.ScopeMode;
import com.ecole.saas.schooldocs.domain.DocumentBinaryStorage;
import com.ecole.saas.schooldocs.domain.DocumentEvent;
import com.ecole.saas.schooldocs.domain.DocumentNumberFormatter;
import com.ecole.saas.schooldocs.domain.DocumentRenderPayload;
import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import com.ecole.saas.schooldocs.domain.DocumentTemplate;
import com.ecole.saas.schooldocs.domain.DocumentTemplateRenderer;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import com.ecole.saas.schooldocs.domain.OfficialDocumentRenderer;
import com.ecole.saas.schooldocs.domain.SchoolDocConflictException;
import com.ecole.saas.schooldocs.domain.SchoolDocNotFoundException;
import com.ecole.saas.schooldocs.domain.SchoolDocValidationException;
import com.ecole.saas.schooldocs.infrastructure.DocumentEventRepository;
import com.ecole.saas.schooldocs.infrastructure.DocumentSequenceRepository;
import com.ecole.saas.schooldocs.infrastructure.DocumentTemplateRepository;
import com.ecole.saas.schooldocs.infrastructure.IssuedDocumentRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestration applicative du cycle de vie des documents scolaires officiels :
 * émission séquentielle idempotente, génération PDF 1.4 + stockage binaire privé isolé,
 * réédition/duplicatas verrouillés en {@code PESSIMISTIC_WRITE}, révocation, vérification
 * d'authenticité par jeton opaque, téléchargement sécurisé et synthèses agrégées SQL.
 */
@Service
public class IssuedDocumentService {

    private static final int MAX_VALIDITY_DAYS = 3650;
    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private final IssuedDocumentRepository documentRepository;
    private final DocumentTemplateRepository templateRepository;
    private final DocumentSequenceRepository sequenceRepository;
    private final DocumentEventRepository eventRepository;
    private final DocumentBinaryStorage binaryStorage;
    private final OfficialDocumentRenderer documentRenderer;
    private final SchoolDocsSubscriptionGuard subscriptionGuard;
    private final SchoolDocsAccessScopeGuard scopeGuard;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Clock clock;

    public IssuedDocumentService(
            IssuedDocumentRepository documentRepository,
            DocumentTemplateRepository templateRepository,
            DocumentSequenceRepository sequenceRepository,
            DocumentEventRepository eventRepository,
            DocumentBinaryStorage binaryStorage,
            OfficialDocumentRenderer documentRenderer,
            SchoolDocsSubscriptionGuard subscriptionGuard,
            SchoolDocsAccessScopeGuard scopeGuard,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.sequenceRepository = sequenceRepository;
        this.eventRepository = eventRepository;
        this.binaryStorage = binaryStorage;
        this.documentRenderer = documentRenderer;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.auditLogPort = auditLogPort;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.clock = clock;
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_ISSUE_ROLES)
    @Transactional
    public DocumentView issueDocument(IssueDocumentCommand command, UUID actorId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        SchoolProfileSnapshot school = subscriptionGuard.requireWriteAccess();

        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        String requestHash = computeIssueRequestHash(command);

        Optional<IssuedDocument> existingOpt = documentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            IssuedDocument existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new SchoolDocConflictException(
                        "schooldocs-idempotency-conflict",
                        "Cette clé d'idempotence a déjà été utilisée avec des paramètres différents.");
            }
            scopeGuard.assertCanIssueDocumentType(existing.getDocumentType());
            return buildSingleView(existing, true);
        }

        DocumentTemplate template = resolveTemplateForIssue(command.templateId());
        DocumentType resolvedType = resolveDocumentType(command.documentType(), template);
        scopeGuard.assertCanIssueDocumentType(resolvedType);

        StudentSummary student = studentDirectoryPort.findStudentById(command.studentId())
                .orElseThrow(() -> new SchoolDocNotFoundException("Élève introuvable dans cet établissement."));

        AcademicContext academicContext = resolveAcademicContext(student.id(), command.academicYearId(), command.classRoomId());
        GuardianContactSummary guardian = resolveGuardianContext(student.id(), command.guardianId(), resolvedType);

        String titleTemplate = pickRequiredTemplate(
                command.title(),
                template != null ? template.getTitleTemplate() : resolvedType.defaultFrenchLabel(),
                "Le titre du document");
        String bodyTemplate = pickRequiredTemplate(
                command.body(),
                template != null ? template.getBodyTemplate() : null,
                "Le corps du document ou un modèle");
        String footerTemplate = command.footer() != null && !command.footer().isBlank()
                ? command.footer().trim()
                : (template != null ? template.getFooterTemplate() : null);

        Instant now = clock.instant();
        ZoneId schoolZone = subscriptionGuard.resolveSchoolZone(school);
        LocalDate issueDate = now.atZone(schoolZone).toLocalDate();
        int sequenceYear = issueDate.getYear();

        Integer effectiveValidityDays = resolveValidityDays(
                command.validityDays(), template != null ? template.getDefaultValidityDays() : null);
        Instant validUntil = effectiveValidityDays != null
                ? now.plus(effectiveValidityDays.longValue(), ChronoUnit.DAYS)
                : null;
        LocalDate validUntilDate = validUntil != null ? validUntil.atZone(schoolZone).toLocalDate() : null;

        long feeAmountXof = resolveFeeXof(command.feeAmountXof(), template != null ? template.getFeeAmountXof() : 0L);

        int nextSeq = sequenceRepository.nextSequenceValue(sequenceYear);
        String documentNumber = DocumentNumberFormatter.format(school.code(), sequenceYear, nextSeq);
        String verificationToken = DocumentSecurityPolicy.generateOpaqueVerificationToken();

        Map<String, String> variables = buildTemplateVariables(
                school,
                student,
                academicContext.academicYear(),
                academicContext.classRoom(),
                guardian,
                resolvedType,
                documentNumber,
                issueDate,
                validUntilDate,
                command.variables());

        String renderedTitle = DocumentTemplateRenderer.render(titleTemplate, variables, true);
        String renderedBody = DocumentTemplateRenderer.render(bodyTemplate, variables, true);
        String renderedFooter = DocumentTemplateRenderer.render(footerTemplate, variables, true);

        DocumentRenderPayload renderPayload = new DocumentRenderPayload(
                school.name(),
                school.code(),
                documentNumber,
                resolvedType,
                false,
                1,
                null,
                renderedTitle,
                renderedBody,
                renderedFooter,
                student.firstName() + " " + student.lastName(),
                student.matricule(),
                academicContext.academicYear() != null ? academicContext.academicYear().label() : null,
                academicContext.classRoom() != null ? academicContext.classRoom().name() : null,
                issueDate,
                validUntilDate,
                verificationToken);

        byte[] pdfBytes = documentRenderer.renderPdf(renderPayload);
        DocumentSecurityPolicy.assertValidPdfBinary(pdfBytes);
        String contentSha256 = DocumentSecurityPolicy.sha256Hex(pdfBytes);

        subscriptionGuard.assertStorageQuotaAvailable(pdfBytes.length);

        String fileName = DocumentSecurityPolicy.buildSafePdfFileName(documentNumber);
        String storageKey = DocumentSecurityPolicy.buildTenantScopedStorageKey(tenantId, sequenceYear, documentNumber);

        IssuedDocument document = IssuedDocument.issueOriginal(
                documentNumber,
                idempotencyKey,
                requestHash,
                verificationToken,
                template != null ? template.getId() : null,
                resolvedType,
                student.id(),
                academicContext.enrollment() != null ? academicContext.enrollment().id() : null,
                guardian != null ? guardian.id() : null,
                academicContext.academicYear() != null ? academicContext.academicYear().id() : null,
                academicContext.classRoom() != null ? academicContext.classRoom().id() : null,
                renderedTitle,
                renderedBody,
                renderedFooter,
                DocumentSecurityPolicy.PDF_CONTENT_TYPE,
                fileName,
                storageKey,
                pdfBytes.length,
                contentSha256,
                feeAmountXof,
                now,
                actorId,
                validUntil);
        IssuedDocument saved = documentRepository.save(document);

        binaryStorage.store(
                saved.getId(),
                storageKey,
                DocumentSecurityPolicy.PDF_CONTENT_TYPE,
                contentSha256,
                pdfBytes);

        eventRepository.save(new DocumentEvent(
                saved.getId(),
                1,
                DocumentEventType.ISSUED,
                actorId,
                null,
                null,
                now));

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_DOCUMENT_ISSUED,
                "ISSUED_DOCUMENT",
                saved.getId(),
                "Émission d'un document scolaire officiel.",
                Map.of(
                        "documentNumber", saved.getDocumentNumber(),
                        "documentType", saved.getDocumentType().name(),
                        "studentId", saved.getStudentId().toString(),
                        "editionNumber", saved.getEditionNumber(),
                        "feeAmountXof", saved.getFeeAmountXof(),
                        "fileSizeBytes", saved.getFileSizeBytes())));

        return buildViewFromContext(
                saved,
                template != null ? template.getCode() : null,
                student,
                guardian,
                academicContext.academicYear(),
                academicContext.classRoom(),
                false,
                now);
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_ISSUE_ROLES)
    @Transactional
    public DocumentView reissueDocument(
            UUID targetDocumentId,
            String reason,
            Integer validityDays,
            Long feeAmountXof,
            String idempotencyKey,
            UUID actorId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        SchoolProfileSnapshot school = subscriptionGuard.requireWriteAccess();

        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String trimmedReason = requireReissueReason(reason);
        String requestHash = computeReissueRequestHash(targetDocumentId, trimmedReason, validityDays, feeAmountXof);

        Optional<IssuedDocument> existingOpt = documentRepository.findByIdempotencyKey(normalizedKey);
        if (existingOpt.isPresent()) {
            IssuedDocument existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new SchoolDocConflictException(
                        "schooldocs-idempotency-conflict",
                        "Cette clé d'idempotence a déjà été utilisée avec des paramètres différents.");
            }
            scopeGuard.assertCanIssueDocumentType(existing.getDocumentType());
            return buildSingleView(existing, true);
        }

        // Verrouillage pessimiste PESSIMISTIC_WRITE sur le document cible (et son original racine si duplicata)
        IssuedDocument target = documentRepository.findByIdForUpdate(targetDocumentId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));
        IssuedDocument rootOriginal = target.getOriginalDocumentId() == null
                ? target
                : documentRepository.findByIdForUpdate(target.getOriginalDocumentId())
                        .orElseThrow(() -> new SchoolDocNotFoundException("Document d'origine introuvable."));

        scopeGuard.assertCanIssueDocumentType(rootOriginal.getDocumentType());

        if (target.getStatus() == DocumentStatus.REVOKED || rootOriginal.getStatus() == DocumentStatus.REVOKED) {
            throw new SchoolDocConflictException(
                    "document-revoked",
                    "Impossible d'émettre un duplicata pour un document révoqué.");
        }

        int nextEdition = documentRepository.findMaxEditionNumberForRoot(rootOriginal.getId()) + 1;

        Instant now = clock.instant();
        ZoneId schoolZone = subscriptionGuard.resolveSchoolZone(school);
        LocalDate issueDate = now.atZone(schoolZone).toLocalDate();
        int sequenceYear = issueDate.getYear();

        Integer effectiveValidityDays = validateOptionalValidityDays(validityDays);
        Instant validUntil;
        if (effectiveValidityDays != null) {
            validUntil = now.plus(effectiveValidityDays.longValue(), ChronoUnit.DAYS);
        } else if (rootOriginal.getValidUntil() != null) {
            long originalDurationSeconds = ChronoUnit.SECONDS.between(
                    rootOriginal.getIssuedAt(), rootOriginal.getValidUntil());
            validUntil = originalDurationSeconds > 0 ? now.plusSeconds(originalDurationSeconds) : null;
        } else {
            validUntil = null;
        }
        LocalDate validUntilDate = validUntil != null ? validUntil.atZone(schoolZone).toLocalDate() : null;

        long effectiveFee = resolveFeeXof(feeAmountXof, rootOriginal.getFeeAmountXof());

        int nextSeq = sequenceRepository.nextSequenceValue(sequenceYear);
        String documentNumber = DocumentNumberFormatter.format(school.code(), sequenceYear, nextSeq);
        String verificationToken = DocumentSecurityPolicy.generateOpaqueVerificationToken();

        StudentSummary student = studentDirectoryPort.findStudentById(rootOriginal.getStudentId()).orElse(null);
        AcademicYearSummary academicYear = rootOriginal.getAcademicYearId() != null
                ? academicStructurePort.findAcademicYearById(rootOriginal.getAcademicYearId()).orElse(null)
                : null;
        ClassRoomSummary classRoom = rootOriginal.getClassRoomId() != null
                ? academicStructurePort.findClassRoomById(rootOriginal.getClassRoomId()).orElse(null)
                : null;
        GuardianContactSummary guardian = rootOriginal.getGuardianId() != null
                ? familyDirectoryPort.findGuardianContactById(rootOriginal.getGuardianId()).orElse(null)
                : null;

        DocumentRenderPayload renderPayload = new DocumentRenderPayload(
                school.name(),
                school.code(),
                documentNumber,
                rootOriginal.getDocumentType(),
                true,
                nextEdition,
                trimmedReason,
                rootOriginal.getTitle(),
                rootOriginal.getRenderedBody(),
                rootOriginal.getRenderedFooter(),
                student != null ? student.firstName() + " " + student.lastName() : null,
                student != null ? student.matricule() : null,
                academicYear != null ? academicYear.label() : null,
                classRoom != null ? classRoom.name() : null,
                issueDate,
                validUntilDate,
                verificationToken);

        byte[] pdfBytes = documentRenderer.renderPdf(renderPayload);
        DocumentSecurityPolicy.assertValidPdfBinary(pdfBytes);
        String contentSha256 = DocumentSecurityPolicy.sha256Hex(pdfBytes);

        subscriptionGuard.assertStorageQuotaAvailable(pdfBytes.length);

        String fileName = DocumentSecurityPolicy.buildSafePdfFileName(documentNumber);
        String storageKey = DocumentSecurityPolicy.buildTenantScopedStorageKey(tenantId, sequenceYear, documentNumber);

        IssuedDocument duplicate = IssuedDocument.issueDuplicate(
                rootOriginal,
                documentNumber,
                normalizedKey,
                requestHash,
                verificationToken,
                nextEdition,
                trimmedReason,
                fileName,
                storageKey,
                pdfBytes.length,
                contentSha256,
                effectiveFee,
                now,
                actorId,
                validUntil);
        IssuedDocument savedDuplicate = documentRepository.save(duplicate);

        binaryStorage.store(
                savedDuplicate.getId(),
                storageKey,
                DocumentSecurityPolicy.PDF_CONTENT_TYPE,
                contentSha256,
                pdfBytes);

        eventRepository.save(new DocumentEvent(
                savedDuplicate.getId(),
                1,
                DocumentEventType.ISSUED,
                actorId,
                trimmedReason,
                rootOriginal.getId(),
                now));

        int nextRootEventSeq = eventRepository.findMaxSequenceNoByDocumentId(rootOriginal.getId()) + 1;
        eventRepository.save(new DocumentEvent(
                rootOriginal.getId(),
                nextRootEventSeq,
                DocumentEventType.DUPLICATE_ISSUED,
                actorId,
                trimmedReason,
                savedDuplicate.getId(),
                now));

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_DOCUMENT_REISSUED,
                "ISSUED_DOCUMENT",
                savedDuplicate.getId(),
                "Émission d'un duplicata officiel tracé.",
                Map.of(
                        "documentNumber", savedDuplicate.getDocumentNumber(),
                        "originalDocumentId", rootOriginal.getId().toString(),
                        "originalDocumentNumber", rootOriginal.getDocumentNumber(),
                        "editionNumber", savedDuplicate.getEditionNumber(),
                        "documentType", savedDuplicate.getDocumentType().name(),
                        "studentId", savedDuplicate.getStudentId().toString(),
                        "feeAmountXof", savedDuplicate.getFeeAmountXof())));

        String templateCode = rootOriginal.getTemplateId() != null
                ? templateRepository.findById(rootOriginal.getTemplateId()).map(DocumentTemplate::getCode).orElse(null)
                : null;
        return buildViewFromContext(savedDuplicate, templateCode, student, guardian, academicYear, classRoom, false, now);
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_REVOKE_ROLES)
    @Transactional
    public DocumentView revokeDocument(UUID documentId, String reason, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        // Verrouillage pessimiste PESSIMISTIC_WRITE sur le document cible
        IssuedDocument document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));

        Instant now = clock.instant();
        document.revoke(actorId, reason, now);
        IssuedDocument saved = documentRepository.save(document);

        int nextSeq = eventRepository.findMaxSequenceNoByDocumentId(saved.getId()) + 1;
        eventRepository.save(new DocumentEvent(
                saved.getId(),
                nextSeq,
                DocumentEventType.REVOKED,
                actorId,
                saved.getRevocationReason(),
                null,
                now));

        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_DOCUMENT_REVOKED,
                AuditOutcome.SUCCESS,
                "ISSUED_DOCUMENT",
                saved.getId(),
                "Révocation d'un document scolaire officiel.",
                Map.of(
                        "documentNumber", saved.getDocumentNumber(),
                        "documentType", saved.getDocumentType().name(),
                        "studentId", saved.getStudentId().toString(),
                        "editionNumber", saved.getEditionNumber(),
                        "reason", saved.getRevocationReason())));

        return buildSingleView(saved, false);
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<DocumentView> listDocuments(
            UUID studentId,
            UUID guardianId,
            UUID academicYearId,
            UUID classRoomId,
            DocumentType documentType,
            DocumentStatus status,
            Boolean duplicate,
            UUID actorId,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
        scopeGuard.assertFilterAllowed(scope, studentId, guardianId, classRoomId);
        if (scope.isEmptyScopedResult()) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        Specification<IssuedDocument> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (guardianId != null) {
                predicates.add(cb.equal(root.get("guardianId"), guardianId));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (classRoomId != null) {
                predicates.add(cb.equal(root.get("classRoomId"), classRoomId));
            }
            if (documentType != null) {
                predicates.add(cb.equal(root.get("documentType"), documentType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (duplicate != null) {
                predicates.add(cb.equal(root.get("duplicate"), duplicate));
            }
            Predicate scopePredicate = buildScopePredicate(scope, root, cb);
            if (scopePredicate != null) {
                predicates.add(scopePredicate);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<IssuedDocument> page = documentRepository.findAll(spec, pageable);
        List<DocumentView> views = buildBatchViews(page.getContent(), clock.instant());
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    @Transactional(readOnly = true)
    public DocumentView getDocument(UUID documentId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        IssuedDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));
        CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
        scopeGuard.assertCanAccessDocument(scope, document);
        return buildSingleView(document, false);
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    @Transactional(readOnly = true)
    public List<DocumentEvent> listDocumentEvents(UUID documentId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        IssuedDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));
        CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
        scopeGuard.assertCanAccessDocument(scope, document);
        return eventRepository.findByDocumentIdOrderBySequenceNoAsc(document.getId());
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    @Transactional(readOnly = true)
    public List<DocumentView> listDuplicates(UUID documentId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        IssuedDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));
        CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
        scopeGuard.assertCanAccessDocument(scope, document);

        UUID rootId = document.rootDocumentId();
        List<IssuedDocument> chain = new ArrayList<>();
        documentRepository.findById(rootId).ifPresent(chain::add);
        chain.addAll(documentRepository.findByOriginalDocumentIdOrderByEditionNumberAsc(rootId));
        return buildBatchViews(chain, clock.instant());
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    public DownloadableDocument downloadDocument(UUID documentId, UUID actorId) {
        TenantContext.requireCurrent();
        DocumentDownloadContext download = readOnlyTransactionTemplate.execute(status -> {
            subscriptionGuard.requireReadAccess();
            IssuedDocument document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new SchoolDocNotFoundException("Document officiel introuvable."));
            CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
            scopeGuard.assertCanAccessDocument(scope, document);

            DocumentBinaryStorage.StoredBinary stored = binaryStorage.load(document.getId())
                    .orElseThrow(() -> new SchoolDocNotFoundException("Contenu binaire du document introuvable."));
            return new DocumentDownloadContext(document, stored);
        });

        IssuedDocument document = download.document();
        DocumentBinaryStorage.StoredBinary stored = download.stored();
        DocumentSecurityPolicy.assertIntegrity(stored.content(), document.getContentSha256());

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLDOCS,
                AuditAction.SCHOOLDOCS_DOCUMENT_DOWNLOADED,
                "ISSUED_DOCUMENT",
                document.getId(),
                "Téléchargement sécurisé d'un document scolaire officiel.",
                Map.of(
                        "documentNumber", document.getDocumentNumber(),
                        "documentType", document.getDocumentType().name(),
                        "studentId", document.getStudentId().toString(),
                        "editionNumber", document.getEditionNumber())));

        return new DownloadableDocument(
                document.getId(),
                document.getDocumentNumber(),
                DocumentSecurityPolicy.buildSafePdfFileName(document.getDocumentNumber()),
                stored.contentType(),
                stored.fileSizeBytes(),
                document.getContentSha256(),
                stored.content());
    }

    private record DocumentDownloadContext(
            IssuedDocument document, DocumentBinaryStorage.StoredBinary stored) {
    }

    @PreAuthorize(SchoolDocsRoles.DOCUMENT_READ_ROLES)
    @Transactional(readOnly = true)
    public DocumentVerificationView verifyByToken(String verificationToken, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        if (verificationToken == null || verificationToken.isBlank()) {
            throw new SchoolDocValidationException("Le jeton de vérification est obligatoire.");
        }
        IssuedDocument document = documentRepository.findByVerificationToken(verificationToken.trim())
                .orElseThrow(() -> new SchoolDocNotFoundException(
                        "schooldocs-verification-not-found",
                        "Aucun document officiel correspondant à ce jeton de vérification."));
        CallerReadScope scope = scopeGuard.resolveReadScope(actorId);
        scopeGuard.assertCanAccessDocument(scope, document);

        Instant now = clock.instant();
        DocumentValidityStatus validityStatus = document.evaluateValidity(now);
        boolean integrityVerified = binaryStorage.load(document.getId())
                .map(stored -> stored.sha256Hash().equalsIgnoreCase(document.getContentSha256()))
                .orElse(false);

        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        StudentSummary student = studentDirectoryPort.findStudentById(document.getStudentId()).orElse(null);
        AcademicYearSummary academicYear = document.getAcademicYearId() != null
                ? academicStructurePort.findAcademicYearById(document.getAcademicYearId()).orElse(null)
                : null;
        String originalNumber = document.getOriginalDocumentId() != null
                ? documentRepository.findById(document.getOriginalDocumentId())
                        .map(IssuedDocument::getDocumentNumber)
                        .orElse(null)
                : null;

        return new DocumentVerificationView(
                document.getId(),
                document.getDocumentNumber(),
                document.getDocumentType(),
                document.getStatus(),
                validityStatus,
                validityStatus == DocumentValidityStatus.VALID && integrityVerified,
                integrityVerified,
                document.isDuplicate(),
                document.getEditionNumber(),
                document.getOriginalDocumentId(),
                originalNumber,
                document.getReissueReason(),
                school.code(),
                school.name(),
                student != null ? student.firstName() + " " + student.lastName() : null,
                student != null ? student.matricule() : null,
                academicYear != null ? academicYear.code() : null,
                document.getTitle(),
                document.getContentSha256(),
                document.getIssuedAt(),
                document.getValidUntil(),
                document.getRevokedAt(),
                document.getRevocationReason());
    }

    @PreAuthorize(SchoolDocsRoles.SUMMARY_READ_ROLES)
    @Transactional(readOnly = true)
    public SchoolDocsSummaryView getSummary() {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Instant now = clock.instant();
        List<Object[]> rows = documentRepository.aggregateSummaryTotals(now);
        Object[] totals = rows.isEmpty() ? new Object[] {0L, 0L, 0L, 0L, 0L, 0L, 0L} : rows.get(0);

        long totalDocuments = asLong(totals[0]);
        long issuedCount = asLong(totals[1]);
        long revokedCount = asLong(totals[2]);
        long expiredCount = asLong(totals[3]);
        long validCount = Math.max(0L, issuedCount - expiredCount);
        long duplicateCount = asLong(totals[4]);
        long totalStorageBytes = asLong(totals[5]);
        long totalFeeXof = asLong(totals[6]);

        Map<DocumentType, Long> countsByType = new EnumMap<>(DocumentType.class);
        for (DocumentType type : DocumentType.values()) {
            countsByType.put(type, 0L);
        }
        for (Object[] row : documentRepository.countGroupedByDocumentType()) {
            DocumentType type = (DocumentType) row[0];
            if (type != null) {
                countsByType.put(type, asLong(row[1]));
            }
        }

        long activeTemplates = templateRepository.countByActive(true);
        long inactiveTemplates = templateRepository.countByActive(false);

        return new SchoolDocsSummaryView(
                activeTemplates,
                inactiveTemplates,
                totalDocuments,
                validCount,
                expiredCount,
                revokedCount,
                duplicateCount,
                totalStorageBytes,
                DocumentSecurityPolicy.bytesToCeilingMiB(totalStorageBytes),
                totalFeeXof,
                countsByType);
    }

    private Predicate buildScopePredicate(
            CallerReadScope scope,
            jakarta.persistence.criteria.Root<IssuedDocument> root,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (scope.mode() == ScopeMode.FULL_SCHOOL) {
            return null;
        }
        if (scope.mode() == ScopeMode.TEACHER_CLASSES) {
            List<Predicate> allowed = new ArrayList<>();
            if (!scope.classRoomIds().isEmpty()) {
                allowed.add(root.get("classRoomId").in(scope.classRoomIds()));
            }
            if (!scope.studentIds().isEmpty()) {
                allowed.add(root.get("studentId").in(scope.studentIds()));
            }
            return allowed.isEmpty() ? cb.disjunction() : cb.or(allowed.toArray(Predicate[]::new));
        }
        if (scope.mode() == ScopeMode.PARENT_STUDENTS) {
            List<Predicate> allowed = new ArrayList<>();
            if (scope.guardianId() != null) {
                allowed.add(cb.equal(root.get("guardianId"), scope.guardianId()));
            }
            if (!scope.studentIds().isEmpty()) {
                allowed.add(root.get("studentId").in(scope.studentIds()));
            }
            return allowed.isEmpty() ? cb.disjunction() : cb.or(allowed.toArray(Predicate[]::new));
        }
        if (scope.mode() == ScopeMode.SELF_STUDENT) {
            return scope.studentIds().isEmpty()
                    ? cb.disjunction()
                    : root.get("studentId").in(scope.studentIds());
        }
        return cb.disjunction();
    }

    private List<DocumentView> buildBatchViews(List<IssuedDocument> documents, Instant now) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Set<UUID> studentIds = new LinkedHashSet<>();
        Set<UUID> guardianIds = new LinkedHashSet<>();
        Set<UUID> academicYearIds = new LinkedHashSet<>();
        Set<UUID> classRoomIds = new LinkedHashSet<>();
        Set<UUID> templateIds = new LinkedHashSet<>();
        for (IssuedDocument doc : documents) {
            studentIds.add(doc.getStudentId());
            if (doc.getGuardianId() != null) {
                guardianIds.add(doc.getGuardianId());
            }
            if (doc.getAcademicYearId() != null) {
                academicYearIds.add(doc.getAcademicYearId());
            }
            if (doc.getClassRoomId() != null) {
                classRoomIds.add(doc.getClassRoomId());
            }
            if (doc.getTemplateId() != null) {
                templateIds.add(doc.getTemplateId());
            }
        }

        Map<UUID, StudentSummary> studentsById = studentIds.isEmpty()
                ? Map.of()
                : studentDirectoryPort.findStudentsByIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentSummary::id, s -> s, (a, b) -> a));
        Map<UUID, GuardianContactSummary> guardiansById = guardianIds.isEmpty()
                ? Map.of()
                : familyDirectoryPort.findGuardianContactsByIds(guardianIds).stream()
                        .collect(Collectors.toMap(GuardianContactSummary::id, g -> g, (a, b) -> a));
        Map<UUID, AcademicYearSummary> yearsById = academicYearIds.isEmpty()
                ? Map.of()
                : academicStructurePort.findAcademicYearsByIds(academicYearIds);
        Map<UUID, ClassRoomSummary> classesById = classRoomIds.isEmpty()
                ? Map.of()
                : academicStructurePort.findClassRoomsByIds(classRoomIds);
        Map<UUID, String> templateCodesById = new HashMap<>();
        if (!templateIds.isEmpty()) {
            for (DocumentTemplate tpl : templateRepository.findAllById(templateIds)) {
                templateCodesById.put(tpl.getId(), tpl.getCode());
            }
        }

        List<DocumentView> results = new ArrayList<>(documents.size());
        for (IssuedDocument doc : documents) {
            results.add(buildViewFromContext(
                    doc,
                    doc.getTemplateId() != null ? templateCodesById.get(doc.getTemplateId()) : null,
                    studentsById.get(doc.getStudentId()),
                    doc.getGuardianId() != null ? guardiansById.get(doc.getGuardianId()) : null,
                    doc.getAcademicYearId() != null ? yearsById.get(doc.getAcademicYearId()) : null,
                    doc.getClassRoomId() != null ? classesById.get(doc.getClassRoomId()) : null,
                    false,
                    now));
        }
        return results;
    }

    private DocumentView buildSingleView(IssuedDocument document, boolean idempotentReplay) {
        Instant now = clock.instant();
        StudentSummary student = studentDirectoryPort.findStudentById(document.getStudentId()).orElse(null);
        GuardianContactSummary guardian = document.getGuardianId() != null
                ? familyDirectoryPort.findGuardianContactById(document.getGuardianId()).orElse(null)
                : null;
        AcademicYearSummary academicYear = document.getAcademicYearId() != null
                ? academicStructurePort.findAcademicYearById(document.getAcademicYearId()).orElse(null)
                : null;
        ClassRoomSummary classRoom = document.getClassRoomId() != null
                ? academicStructurePort.findClassRoomById(document.getClassRoomId()).orElse(null)
                : null;
        String templateCode = document.getTemplateId() != null
                ? templateRepository.findById(document.getTemplateId()).map(DocumentTemplate::getCode).orElse(null)
                : null;
        return buildViewFromContext(
                document, templateCode, student, guardian, academicYear, classRoom, idempotentReplay, now);
    }

    private DocumentView buildViewFromContext(
            IssuedDocument document,
            String templateCode,
            StudentSummary student,
            GuardianContactSummary guardian,
            AcademicYearSummary academicYear,
            ClassRoomSummary classRoom,
            boolean idempotentReplay,
            Instant referenceTime) {
        return new DocumentView(
                document,
                document.evaluateValidity(referenceTime),
                templateCode,
                student != null ? student.firstName() + " " + student.lastName() : null,
                student != null ? student.matricule() : null,
                guardian != null ? guardian.firstName() + " " + guardian.lastName() : null,
                academicYear != null ? academicYear.code() : null,
                academicYear != null ? academicYear.label() : null,
                classRoom != null ? classRoom.code() : null,
                classRoom != null ? classRoom.name() : null,
                idempotentReplay);
    }

    private DocumentTemplate resolveTemplateForIssue(UUID templateId) {
        if (templateId == null) {
            return null;
        }
        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Modèle de document introuvable."));
        if (!template.isActive()) {
            throw new SchoolDocConflictException(
                    "schooldocs-template-inactive",
                    "Ce modèle de document est désactivé et ne peut plus être utilisé pour une nouvelle émission.");
        }
        return template;
    }

    private DocumentType resolveDocumentType(DocumentType requested, DocumentTemplate template) {
        if (template != null && requested != null && template.getDocumentType() != requested) {
            throw new SchoolDocValidationException(
                    "Le type de document demandé ne correspond pas au type du modèle sélectionné.");
        }
        DocumentType resolved = requested != null ? requested : (template != null ? template.getDocumentType() : null);
        if (resolved == null) {
            throw new SchoolDocValidationException(
                    "Le type de document est obligatoire lorsqu'aucun modèle n'est fourni.");
        }
        return resolved;
    }

    private AcademicContext resolveAcademicContext(UUID studentId, UUID requestedYearId, UUID requestedClassRoomId) {
        AcademicYearSummary academicYear = null;
        ClassRoomSummary classRoom = null;
        StudentEnrollmentSummary enrollment = null;

        if (requestedYearId != null) {
            academicYear = academicStructurePort.findAcademicYearById(requestedYearId)
                    .orElseThrow(() -> new SchoolDocNotFoundException("Année scolaire introuvable."));
            enrollment = studentDirectoryPort.findEnrollmentByStudentAndYear(studentId, requestedYearId).orElse(null);
        } else {
            enrollment = studentDirectoryPort.findCurrentEnrollmentForStudent(studentId).orElse(null);
            if (enrollment != null) {
                academicYear = academicStructurePort.findAcademicYearById(enrollment.academicYearId()).orElse(null);
            }
        }

        UUID effectiveClassRoomId = requestedClassRoomId != null
                ? requestedClassRoomId
                : (enrollment != null ? enrollment.classRoomId() : null);

        if (effectiveClassRoomId != null) {
            classRoom = academicStructurePort.findClassRoomById(effectiveClassRoomId)
                    .orElseThrow(() -> new SchoolDocNotFoundException("Classe introuvable."));
            if (academicYear == null) {
                academicYear = academicStructurePort.findAcademicYearById(classRoom.academicYearId()).orElse(null);
            } else if (!classRoom.academicYearId().equals(academicYear.id())) {
                throw new SchoolDocValidationException(
                        "La classe sélectionnée n'appartient pas à l'année scolaire demandée.");
            }
        }

        return new AcademicContext(academicYear, classRoom, enrollment);
    }

    private GuardianContactSummary resolveGuardianContext(
            UUID studentId,
            UUID requestedGuardianId,
            DocumentType documentType) {
        if (requestedGuardianId != null) {
            GuardianContactSummary guardian = familyDirectoryPort.findGuardianContactById(requestedGuardianId)
                    .orElseThrow(() -> new SchoolDocNotFoundException("Parent/tuteur introuvable."));
            List<StudentGuardianSummary> links = familyDirectoryPort.findGuardiansByStudentId(studentId);
            boolean linked = links.stream()
                    .anyMatch(l -> l.active() && l.guardianId().equals(requestedGuardianId));
            if (!linked) {
                throw new SchoolDocValidationException(
                        "Le parent/tuteur spécifié n'est pas activement rattaché à cet élève.");
            }
            return guardian;
        }
        if (documentType == DocumentType.SUMMONS) {
            Optional<StudentGuardianSummary> primary = familyDirectoryPort.findPrimaryContactByStudentId(studentId);
            if (primary.isPresent()) {
                return familyDirectoryPort.findGuardianContactById(primary.get().guardianId()).orElse(null);
            }
        }
        return null;
    }

    private Map<String, String> buildTemplateVariables(
            SchoolProfileSnapshot school,
            StudentSummary student,
            AcademicYearSummary academicYear,
            ClassRoomSummary classRoom,
            GuardianContactSummary guardian,
            DocumentType documentType,
            String documentNumber,
            LocalDate issueDate,
            LocalDate validUntilDate,
            Map<String, String> customVariables) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (customVariables != null) {
            customVariables.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null) {
                    vars.put(k.trim(), v.trim());
                }
            });
        }
        // Champs officiels vérifiés côté serveur (non falsifiables par le client)
        vars.put("school.name", school.name());
        vars.put("school.code", school.code());
        vars.put("student.firstName", student.firstName());
        vars.put("student.lastName", student.lastName());
        vars.put("student.fullName", student.firstName() + " " + student.lastName());
        vars.put("student.matricule", student.matricule());
        if (student.birthDate() != null) {
            vars.put("student.birthDate", FRENCH_DATE.format(student.birthDate()));
        }
        if (academicYear != null) {
            vars.put("academicYear.code", academicYear.code());
            vars.put("academicYear.label", academicYear.label());
        }
        if (classRoom != null) {
            vars.put("classRoom.code", classRoom.code());
            vars.put("classRoom.name", classRoom.name());
        }
        if (guardian != null) {
            vars.put("guardian.firstName", guardian.firstName());
            vars.put("guardian.lastName", guardian.lastName());
            vars.put("guardian.fullName", guardian.firstName() + " " + guardian.lastName());
        }
        vars.put("document.number", documentNumber);
        vars.put("document.type", documentType.defaultFrenchLabel());
        vars.put("document.issueDate", FRENCH_DATE.format(issueDate));
        if (validUntilDate != null) {
            vars.put("document.validUntil", FRENCH_DATE.format(validUntilDate));
        }
        return vars;
    }

    private static String pickRequiredTemplate(String overrideValue, String fallbackValue, String label) {
        if (overrideValue != null && !overrideValue.isBlank()) {
            return overrideValue.trim();
        }
        if (fallbackValue != null && !fallbackValue.isBlank()) {
            return fallbackValue.trim();
        }
        throw new SchoolDocValidationException(label + " est obligatoire.");
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new SchoolDocValidationException(
                    "La clé d'idempotence est obligatoire (en-tête Idempotency-Key ou champ idempotencyKey).");
        }
        String trimmed = key.trim();
        if (trimmed.length() > 128) {
            throw new SchoolDocValidationException("La clé d'idempotence ne doit pas dépasser 128 caractères.");
        }
        return trimmed;
    }

    private static String requireReissueReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SchoolDocValidationException("Le motif de réédition/duplicata est obligatoire.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 500) {
            throw new SchoolDocValidationException(
                    "Le motif de réédition/duplicata doit contenir entre 5 et 500 caractères.");
        }
        return trimmed;
    }

    private static Integer resolveValidityDays(Integer requested, Integer templateDefault) {
        Integer effective = requested != null ? requested : templateDefault;
        return validateOptionalValidityDays(effective);
    }

    private static Integer validateOptionalValidityDays(Integer days) {
        if (days == null) {
            return null;
        }
        if (days < 1 || days > MAX_VALIDITY_DAYS) {
            throw new SchoolDocValidationException(
                    "La durée de validité doit être comprise entre 1 et " + MAX_VALIDITY_DAYS + " jours.");
        }
        return days;
    }

    private static long resolveFeeXof(Long requested, long fallback) {
        long effective = requested != null ? requested : fallback;
        if (effective < 0L) {
            throw new SchoolDocValidationException("Les frais de document en FCFA doivent être positifs ou nuls.");
        }
        return effective;
    }

    private static String computeIssueRequestHash(IssueDocumentCommand cmd) {
        StringBuilder canonical = new StringBuilder("ISSUE");
        canonical.append('|').append(cmd.templateId())
                .append('|').append(cmd.documentType())
                .append('|').append(cmd.studentId())
                .append('|').append(cmd.guardianId())
                .append('|').append(cmd.academicYearId())
                .append('|').append(cmd.classRoomId())
                .append('|').append(nullSafeTrim(cmd.title()))
                .append('|').append(nullSafeTrim(cmd.body()))
                .append('|').append(nullSafeTrim(cmd.footer()))
                .append('|').append(cmd.validityDays())
                .append('|').append(cmd.feeAmountXof());
        if (cmd.variables() != null && !cmd.variables().isEmpty()) {
            TreeMap<String, String> sorted = new TreeMap<>();
            cmd.variables().forEach((k, v) -> {
                if (k != null && v != null) {
                    sorted.put(k.trim(), v.trim());
                }
            });
            sorted.forEach((k, v) -> canonical.append('|').append(k).append('=').append(v));
        }
        return DocumentSecurityPolicy.sha256Hex(canonical.toString());
    }

    private static String computeReissueRequestHash(
            UUID targetDocumentId,
            String reason,
            Integer validityDays,
            Long feeAmountXof) {
        String canonical = "REISSUE|" + targetDocumentId + "|" + reason + "|" + validityDays + "|" + feeAmountXof;
        return DocumentSecurityPolicy.sha256Hex(canonical);
    }

    private static String nullSafeTrim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private record AcademicContext(
            AcademicYearSummary academicYear,
            ClassRoomSummary classRoom,
            StudentEnrollmentSummary enrollment) {
    }

    public record IssueDocumentCommand(
            UUID templateId,
            DocumentType documentType,
            UUID studentId,
            UUID guardianId,
            UUID academicYearId,
            UUID classRoomId,
            String title,
            String body,
            String footer,
            Map<String, String> variables,
            Integer validityDays,
            Long feeAmountXof,
            String idempotencyKey) {
    }

    public record DocumentView(
            IssuedDocument document,
            DocumentValidityStatus validityStatus,
            String templateCode,
            String studentFullName,
            String studentMatricule,
            String guardianFullName,
            String academicYearCode,
            String academicYearLabel,
            String classRoomCode,
            String classRoomName,
            boolean idempotentReplay) {
    }

    public record DownloadableDocument(
            UUID documentId,
            String documentNumber,
            String fileName,
            String contentType,
            long fileSizeBytes,
            String contentSha256,
            byte[] content) {
    }

    public record DocumentVerificationView(
            UUID documentId,
            String documentNumber,
            DocumentType documentType,
            DocumentStatus status,
            DocumentValidityStatus validityStatus,
            boolean authentic,
            boolean integrityVerified,
            boolean duplicate,
            int editionNumber,
            UUID originalDocumentId,
            String originalDocumentNumber,
            String reissueReason,
            String schoolCode,
            String schoolName,
            String studentFullName,
            String studentMatricule,
            String academicYearCode,
            String title,
            String contentSha256,
            Instant issuedAt,
            Instant validUntil,
            Instant revokedAt,
            String revocationReason) {
    }

    public record SchoolDocsSummaryView(
            long activeTemplates,
            long inactiveTemplates,
            long totalDocuments,
            long validDocuments,
            long expiredDocuments,
            long revokedDocuments,
            long duplicateDocuments,
            long totalStorageBytes,
            long totalStorageMiB,
            long totalFeeAmountXof,
            Map<DocumentType, Long> documentsByType) {
    }
}
