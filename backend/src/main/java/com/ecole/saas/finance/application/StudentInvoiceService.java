package com.ecole.saas.finance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.family.StudentGuardianSummary;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import com.ecole.saas.finance.domain.FeeItem;
import com.ecole.saas.finance.domain.FinanceCodeFormatter;
import com.ecole.saas.finance.domain.FinanceConflictException;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.FinanceSequenceType;
import com.ecole.saas.finance.domain.FinanceValidationException;
import com.ecole.saas.finance.domain.InvoiceStatus;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import com.ecole.saas.finance.domain.MoneyMath;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.domain.StudentInvoiceLine;
import com.ecole.saas.finance.infrastructure.FeeAdjustmentRepository;
import com.ecole.saas.finance.infrastructure.FeeItemRepository;
import com.ecole.saas.finance.infrastructure.FinanceSequenceRepository;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceLineRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de gestion de la facturation élève et des échéanciers.
 */
@Service
public class StudentInvoiceService {

    public record InvoiceLineInput(UUID feeItemId, int quantity, Long unitAmountXofOverride, String labelOverride) {
    }

    public record ScheduleItemInput(String label, LocalDate dueDate, long amountXof) {
    }

    public record InvoiceDetailView(
            StudentInvoice invoice,
            List<StudentInvoiceLine> lines,
            List<PaymentSchedule> schedules) {
    }

    private final StudentInvoiceRepository invoiceRepository;
    private final StudentInvoiceLineRepository lineRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final FeeAdjustmentRepository feeAdjustmentRepository;
    private final FeeItemRepository feeItemRepository;
    private final FinanceSequenceRepository sequenceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerPostingHelper ledgerPostingHelper;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final FinanceSchoolContext schoolContext;
    private final FinanceSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public StudentInvoiceService(
            StudentInvoiceRepository invoiceRepository,
            StudentInvoiceLineRepository lineRepository,
            PaymentScheduleRepository scheduleRepository,
            FeeAdjustmentRepository feeAdjustmentRepository,
            FeeItemRepository feeItemRepository,
            FinanceSequenceRepository sequenceRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerPostingHelper ledgerPostingHelper,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            FinanceSchoolContext schoolContext,
            FinanceSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.lineRepository = lineRepository;
        this.scheduleRepository = scheduleRepository;
        this.feeAdjustmentRepository = feeAdjustmentRepository;
        this.feeItemRepository = feeItemRepository;
        this.sequenceRepository = sequenceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerPostingHelper = ledgerPostingHelper;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<StudentInvoice> listInvoices(
            UUID studentId,
            UUID academicYearId,
            UUID familyId,
            InvoiceStatus status,
            String search,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<StudentInvoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (familyId != null) {
                predicates.add(cb.equal(root.get("familyId"), familyId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(root.get("title")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return invoiceRepository.findAll(spec, pageable);
    }

    @PreAuthorize(FinanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public InvoiceDetailView getInvoiceDetail(UUID invoiceId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        StudentInvoice invoice = requireInvoice(invoiceId);
        List<StudentInvoiceLine> lines = lineRepository.findByInvoiceIdOrderByLineOrderAsc(invoice.getId());
        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        return new InvoiceDetailView(invoice, lines, schedules);
    }

    @PreAuthorize(FinanceRoles.INVOICE_WRITE_ROLES)
    @Transactional
    public InvoiceDetailView createDraftInvoice(
            UUID studentId,
            UUID academicYearId,
            UUID payerGuardianId,
            UUID familyId,
            String title,
            LocalDate dueDate,
            String notes,
            List<InvoiceLineInput> lineInputs,
            List<ScheduleItemInput> scheduleInputs,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "student-not-found", "Élève introuvable", "L'élève référencé est introuvable."));
        if (!student.active()) {
            throw new FinanceConflictException(
                    "student-not-active",
                    "Élève inactif",
                    "Impossible de créer une facture pour un élève qui n'est pas actif.");
        }

        AcademicYearSummary academicYear = requireMutableAcademicYear(academicYearId);
        Optional<StudentEnrollmentSummary> enrollmentOpt =
                studentDirectoryPort.findEnrollmentByStudentAndYear(student.id(), academicYear.id());
        UUID enrollmentId = enrollmentOpt.map(StudentEnrollmentSummary::id).orElse(null);
        UUID studentGradeLevelId = resolveStudentGradeLevel(enrollmentOpt.orElse(null));

        PayerResolution payer = resolvePayerAndFamily(student.id(), payerGuardianId, familyId);

        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        LocalDate today = LocalDate.now(clock.withZone(schoolContext.resolveSchoolZone(school)));
        long seqValue = sequenceRepository.nextSequenceValue(FinanceSequenceType.INVOICE, today.getYear());
        String invoiceNumber = FinanceCodeFormatter.formatInvoiceNumber(school.code(), today.getYear(), seqValue);

        StudentInvoice invoice = new StudentInvoice(
                invoiceNumber,
                student.id(),
                academicYear.id(),
                enrollmentId,
                payer.guardianId(),
                payer.familyId(),
                title,
                dueDate,
                notes,
                actorId);
        invoice = invoiceRepository.save(invoice);

        List<StudentInvoiceLine> savedLines = buildAndSaveLines(
                invoice, academicYear.id(), studentGradeLevelId, lineInputs);
        long grossTotal = savedLines.stream()
                .map(StudentInvoiceLine::getLineTotalXof)
                .reduce(0L, MoneyMath::addExact);
        invoice.recalculateDraftGross(grossTotal);
        invoice = invoiceRepository.save(invoice);

        List<PaymentSchedule> savedSchedules = List.of();
        if (scheduleInputs != null && !scheduleInputs.isEmpty()) {
            savedSchedules = replaceDraftSchedules(invoice, scheduleInputs);
        }
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FINANCE,
                AuditAction.INVOICE_CREATED,
                "STUDENT_INVOICE",
                invoice.getId(),
                "Création d'une facture élève en brouillon.",
                Map.of(
                        "invoiceNumber", invoice.getInvoiceNumber(),
                        "studentId", invoice.getStudentId().toString(),
                        "netAmountXof", invoice.getNetAmountXof())));
        return new InvoiceDetailView(invoice, savedLines, savedSchedules);
    }

    @PreAuthorize(FinanceRoles.INVOICE_WRITE_ROLES)
    @Transactional
    public InvoiceDetailView updateDraftInvoice(
            UUID invoiceId,
            UUID payerGuardianId,
            UUID familyId,
            String title,
            LocalDate dueDate,
            String notes,
            List<InvoiceLineInput> lineInputs,
            List<ScheduleItemInput> scheduleInputs) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentInvoice invoice = requireInvoiceForUpdate(invoiceId);
        requireMutableAcademicYear(invoice.getAcademicYearId());
        if (!invoice.isDraft()) {
            throw new FinanceConflictException(
                    "invoice-not-draft",
                    "Facture verrouillée",
                    "Seule une facture en brouillon peut être modifiée.");
        }

        PayerResolution payer = resolvePayerAndFamily(invoice.getStudentId(), payerGuardianId, familyId);
        invoice.updateDraft(payer.guardianId(), payer.familyId(), title, dueDate, notes);

        Optional<StudentEnrollmentSummary> enrollmentOpt = studentDirectoryPort.findEnrollmentByStudentAndYear(
                invoice.getStudentId(), invoice.getAcademicYearId());
        UUID studentGradeLevelId = resolveStudentGradeLevel(enrollmentOpt.orElse(null));

        lineRepository.deleteByInvoiceId(invoice.getId());
        lineRepository.flush();
        List<StudentInvoiceLine> savedLines = buildAndSaveLines(
                invoice, invoice.getAcademicYearId(), studentGradeLevelId, lineInputs);
        long grossTotal = savedLines.stream()
                .map(StudentInvoiceLine::getLineTotalXof)
                .reduce(0L, MoneyMath::addExact);
        invoice.recalculateDraftGross(grossTotal);
        invoice = invoiceRepository.save(invoice);

        List<PaymentSchedule> savedSchedules;
        if (scheduleInputs != null) {
            scheduleRepository.deleteByInvoiceId(invoice.getId());
            scheduleRepository.flush();
            savedSchedules = scheduleInputs.isEmpty()
                    ? List.of()
                    : replaceDraftSchedules(invoice, scheduleInputs);
        } else {
            savedSchedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        }

        return new InvoiceDetailView(invoice, savedLines, savedSchedules);
    }

    @PreAuthorize(FinanceRoles.INVOICE_WRITE_ROLES)
    @Transactional
    public InvoiceDetailView generateEqualSchedules(
            UUID invoiceId,
            int installmentCount,
            LocalDate firstDueDate,
            int intervalMonths,
            String labelPrefix) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        if (intervalMonths < 1 || intervalMonths > 12) {
            throw new IllegalArgumentException("L'intervalle en mois doit être compris entre 1 et 12.");
        }
        Objects.requireNonNull(firstDueDate, "La date de première échéance est obligatoire.");

        StudentInvoice invoice = requireInvoiceForUpdate(invoiceId);
        requireMutableAcademicYear(invoice.getAcademicYearId());
        if (!invoice.isDraft()) {
            throw new FinanceConflictException(
                    "invoice-not-draft",
                    "Facture verrouillée",
                    "L'échéancier ne peut être régénéré que sur une facture en brouillon.");
        }
        if (invoice.getNetAmountXof() <= 0L) {
            throw new FinanceValidationException(
                    "invoice-empty-amount",
                    "Montant nul",
                    "Ajoutez au moins une ligne à montant positif avant de générer un échéancier.");
        }

        List<Long> shares = MoneyMath.splitEvenly(invoice.getNetAmountXof(), installmentCount);
        String prefix = (labelPrefix == null || labelPrefix.isBlank()) ? "Échéance" : labelPrefix.trim();

        scheduleRepository.deleteByInvoiceId(invoice.getId());
        scheduleRepository.flush();

        List<PaymentSchedule> created = new ArrayList<>(installmentCount);
        for (int i = 0; i < installmentCount; i++) {
            LocalDate dueDate = firstDueDate.plusMonths((long) i * intervalMonths);
            created.add(new PaymentSchedule(
                    invoice.getId(),
                    i + 1,
                    prefix + " " + (i + 1) + "/" + installmentCount,
                    dueDate,
                    shares.get(i)));
        }
        List<PaymentSchedule> savedSchedules = scheduleRepository.saveAll(created);
        List<StudentInvoiceLine> lines = lineRepository.findByInvoiceIdOrderByLineOrderAsc(invoice.getId());
        return new InvoiceDetailView(invoice, lines, savedSchedules);
    }

    @PreAuthorize(FinanceRoles.INVOICE_WRITE_ROLES)
    @Transactional
    public InvoiceDetailView issueInvoice(UUID invoiceId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentInvoice invoice = requireInvoiceForUpdate(invoiceId);
        requireMutableAcademicYear(invoice.getAcademicYearId());
        List<StudentInvoiceLine> lines = lineRepository.findByInvoiceIdOrderByLineOrderAsc(invoice.getId());
        if (lines.isEmpty()) {
            throw new FinanceValidationException(
                    "invoice-no-lines",
                    "Facture sans lignes",
                    "Une facture doit contenir au moins une ligne avant émission.");
        }

        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        if (schedules.isEmpty()) {
            PaymentSchedule singleSchedule = new PaymentSchedule(
                    invoice.getId(), 1, "Échéance unique", invoice.getDueDate(), invoice.getNetAmountXof());
            schedules = List.of(scheduleRepository.save(singleSchedule));
        } else {
            long scheduleSum = schedules.stream()
                    .map(PaymentSchedule::getAmountXof)
                    .reduce(0L, MoneyMath::addExact);
            if (scheduleSum != invoice.getNetAmountXof()) {
                throw new FinanceValidationException(
                        "schedule-sum-mismatch",
                        "Échéancier incohérent",
                        "La somme des échéances (" + scheduleSum
                                + " FCFA) doit être exactement égale au montant net de la facture ("
                                + invoice.getNetAmountXof() + " FCFA).");
            }
        }

        Instant now = clock.instant();
        LocalDate issueDate = schoolContext.todayInSchoolZone();
        invoice.issue(issueDate, now, actorId);
        invoice = invoiceRepository.save(invoice);

        ledgerPostingHelper.appendEntry(
                invoice.getStudentId(),
                invoice.getId(),
                LedgerEntryType.CHARGE,
                invoice.getGrossAmountXof(),
                LedgerReferenceType.INVOICE,
                invoice.getId(),
                null,
                "Émission facture " + invoice.getInvoiceNumber(),
                actorId,
                now);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.FINANCE,
                AuditAction.INVOICE_ISSUED,
                "STUDENT_INVOICE",
                invoice.getId(),
                "Émission officielle d'une facture élève.",
                Map.of(
                        "invoiceNumber", invoice.getInvoiceNumber(),
                        "studentId", invoice.getStudentId().toString(),
                        "netAmountXof", invoice.getNetAmountXof())));

        return new InvoiceDetailView(invoice, lines, schedules);
    }

    @PreAuthorize(FinanceRoles.APPROVAL_ROLES)
    @Transactional
    public InvoiceDetailView cancelInvoice(UUID invoiceId, String reason, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        StudentInvoice invoice = requireInvoiceForUpdate(invoiceId);
        long netBeforeCancel = invoice.getNetAmountXof();
        Instant now = clock.instant();
        boolean wasIssued = invoice.cancel(reason, now, actorId);
        invoice = invoiceRepository.save(invoice);

        List<PaymentSchedule> schedules = scheduleRepository.findByInvoiceIdOrderByInstallmentNoAsc(invoice.getId());
        for (PaymentSchedule schedule : schedules) {
            schedule.cancel();
        }
        schedules = scheduleRepository.saveAll(schedules);

        List<FeeAdjustment> pendingAdjustments =
                feeAdjustmentRepository.findByInvoiceIdAndStatus(invoice.getId(), ApprovalStatus.PENDING_APPROVAL);
        if (!pendingAdjustments.isEmpty()) {
            String autoRejectNote = "Rejet automatique suite à l'annulation de la facture "
                    + invoice.getInvoiceNumber() + ".";
            for (FeeAdjustment pending : pendingAdjustments) {
                pending.reject(actorId, now, autoRejectNote);
            }
            feeAdjustmentRepository.saveAll(pendingAdjustments);
        }

        if (wasIssued && netBeforeCancel > 0L) {
            UUID chargeEntryId = ledgerEntryRepository
                    .findFirstByReferenceTypeAndReferenceIdAndEntryType(
                            LedgerReferenceType.INVOICE, invoice.getId(), LedgerEntryType.CHARGE)
                    .map(LedgerEntry::getId)
                    .orElse(null);

            ledgerPostingHelper.appendEntry(
                    invoice.getStudentId(),
                    invoice.getId(),
                    LedgerEntryType.CHARGE_REVERSAL,
                    netBeforeCancel,
                    LedgerReferenceType.INVOICE,
                    invoice.getId(),
                    chargeEntryId,
                    "Annulation facture " + invoice.getInvoiceNumber() + " : " + invoice.getCancellationReason(),
                    actorId,
                    now);
        }

        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.FINANCE,
                AuditAction.INVOICE_CANCELLED,
                AuditOutcome.SUCCESS,
                "STUDENT_INVOICE",
                invoice.getId(),
                "Annulation d'une facture élève.",
                Map.of(
                        "invoiceNumber", invoice.getInvoiceNumber(),
                        "wasIssued", wasIssued,
                        "netBeforeCancelXof", netBeforeCancel)));

        List<StudentInvoiceLine> lines = lineRepository.findByInvoiceIdOrderByLineOrderAsc(invoice.getId());
        return new InvoiceDetailView(invoice, lines, schedules);
    }

    private List<StudentInvoiceLine> buildAndSaveLines(
            StudentInvoice invoice,
            UUID academicYearId,
            UUID studentGradeLevelId,
            List<InvoiceLineInput> lineInputs) {
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new FinanceValidationException(
                    "invoice-no-lines",
                    "Lignes obligatoires",
                    "Une facture doit contenir au moins une ligne de frais.");
        }
        Set<UUID> feeItemIds = lineInputs.stream()
                .map(InvoiceLineInput::feeItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, FeeItem> feeItemsById = new HashMap<>();
        for (FeeItem item : feeItemRepository.findByIdIn(feeItemIds)) {
            feeItemsById.put(item.getId(), item);
        }

        List<StudentInvoiceLine> entities = new ArrayList<>(lineInputs.size());
        int order = 1;
        for (InvoiceLineInput input : lineInputs) {
            FeeItem feeItem = feeItemsById.get(input.feeItemId());
            if (feeItem == null) {
                throw new FinanceNotFoundException(
                        "fee-item-not-found", "Frais introuvable", "Un élément de frais référencé est introuvable.");
            }
            if (!feeItem.isActive()) {
                throw new FinanceConflictException(
                        "fee-item-inactive",
                        "Frais inactif",
                        "Le frais '" + feeItem.getCode() + "' est désactivé et ne peut pas être facturé.");
            }
            if (!feeItem.getAcademicYearId().equals(academicYearId)) {
                throw new FinanceValidationException(
                        "fee-item-year-mismatch",
                        "Année scolaire incohérente",
                        "Le frais '" + feeItem.getCode() + "' n'appartient pas à l'année scolaire de la facture.");
            }
            if (feeItem.getGradeLevelId() != null
                    && studentGradeLevelId != null
                    && !feeItem.getGradeLevelId().equals(studentGradeLevelId)) {
                throw new FinanceValidationException(
                        "fee-item-level-mismatch",
                        "Niveau scolaire incohérent",
                        "Le frais '" + feeItem.getCode() + "' ne correspond pas au niveau d'inscription de l'élève.");
            }
            long unitAmount = input.unitAmountXofOverride() != null
                    ? input.unitAmountXofOverride()
                    : feeItem.getAmountXof();
            String label = (input.labelOverride() != null && !input.labelOverride().isBlank())
                    ? input.labelOverride().trim()
                    : feeItem.getLabel();

            entities.add(new StudentInvoiceLine(
                    invoice.getId(),
                    feeItem.getId(),
                    order++,
                    feeItem.getCode(),
                    label,
                    feeItem.getCategory(),
                    input.quantity(),
                    unitAmount));
        }
        return lineRepository.saveAll(entities);
    }

    private List<PaymentSchedule> replaceDraftSchedules(
            StudentInvoice invoice, List<ScheduleItemInput> scheduleInputs) {
        List<PaymentSchedule> entities = new ArrayList<>(scheduleInputs.size());
        long total = 0L;
        int installmentNo = 1;
        for (ScheduleItemInput input : scheduleInputs) {
            PaymentSchedule schedule = new PaymentSchedule(
                    invoice.getId(),
                    installmentNo++,
                    input.label(),
                    input.dueDate(),
                    input.amountXof());
            total = MoneyMath.addExact(total, schedule.getAmountXof());
            entities.add(schedule);
        }
        if (total != invoice.getNetAmountXof()) {
            throw new FinanceValidationException(
                    "schedule-sum-mismatch",
                    "Échéancier incohérent",
                    "La somme des échéances (" + total
                            + " FCFA) doit correspondre au montant net de la facture ("
                            + invoice.getNetAmountXof() + " FCFA).");
        }
        return scheduleRepository.saveAll(entities);
    }

    private UUID resolveStudentGradeLevel(StudentEnrollmentSummary enrollment) {
        if (enrollment == null || enrollment.classRoomId() == null) {
            return null;
        }
        return academicStructurePort.findClassRoomById(enrollment.classRoomId())
                .map(ClassRoomSummary::gradeLevelId)
                .orElse(null);
    }

    private record PayerResolution(UUID guardianId, UUID familyId) {
    }

    private PayerResolution resolvePayerAndFamily(UUID studentId, UUID requestedGuardianId, UUID requestedFamilyId) {
        UUID resolvedGuardianId = requestedGuardianId;
        UUID resolvedFamilyId = requestedFamilyId;

        if (resolvedGuardianId != null) {
            GuardianSummary guardian = familyDirectoryPort.findGuardianById(resolvedGuardianId)
                    .orElseThrow(() -> new FinanceNotFoundException(
                            "guardian-not-found",
                            "Parent/tuteur introuvable",
                            "Le responsable payeur référencé est introuvable."));
            if (!guardian.active()) {
                throw new FinanceConflictException(
                        "guardian-inactive",
                        "Parent/tuteur archivé",
                        "Le responsable payeur référencé est archivé.");
            }
            if (resolvedFamilyId == null) {
                resolvedFamilyId = guardian.familyId();
            }
        } else {
            Optional<StudentGuardianSummary> financialResponsible =
                    familyDirectoryPort.findFinancialResponsibleByStudentId(studentId);
            if (financialResponsible.isPresent()) {
                resolvedGuardianId = financialResponsible.get().guardianId();
                if (resolvedFamilyId == null) {
                    resolvedFamilyId = financialResponsible.get().familyId();
                }
            }
        }

        if (resolvedFamilyId != null) {
            FamilySummary family = familyDirectoryPort.findFamilyById(resolvedFamilyId)
                    .orElseThrow(() -> new FinanceNotFoundException(
                            "family-not-found",
                            "Famille introuvable",
                            "La famille référencée est introuvable."));
            if (!family.active()) {
                throw new FinanceConflictException(
                        "family-inactive",
                        "Famille archivée",
                        "La famille référencée est archivée.");
            }
        }
        return new PayerResolution(resolvedGuardianId, resolvedFamilyId);
    }

    private StudentInvoice requireInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture demandée est introuvable."));
    }

    private StudentInvoice requireInvoiceForUpdate(UUID invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "invoice-not-found", "Facture introuvable", "La facture demandée est introuvable."));
    }

    private AcademicYearSummary requireMutableAcademicYear(UUID academicYearId) {
        AcademicYearSummary year = academicStructurePort.findAcademicYearById(academicYearId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "academic-year-not-found",
                        "Année scolaire introuvable",
                        "L'année scolaire référencée est introuvable."));
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new FinanceConflictException(
                    "academic-year-immutable",
                    "Année scolaire verrouillée",
                    "Impossible de modifier la facturation sur une année scolaire clôturée ou archivée.");
        }
        return year;
    }
}
