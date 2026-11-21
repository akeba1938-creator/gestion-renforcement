package com.ecole.saas.finance.application;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.finance.FamilyFinancialSummary;
import com.ecole.saas.finance.FinanceAccountPort;
import com.ecole.saas.finance.FinancialReportingOverview;
import com.ecole.saas.finance.PaymentMethodBreakdown;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.finance.domain.AdjustmentType;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.InvoiceStatus;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.MoneyMath;
import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.infrastructure.CashClosingRepository;
import com.ecole.saas.finance.infrastructure.FeeAdjustmentRepository;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import com.ecole.saas.finance.infrastructure.PaymentRepository;
import com.ecole.saas.finance.infrastructure.PaymentReversalRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link FinanceAccountPort} en lecture
 * seule
 * calculant les soldes financiers par élève, par famille et les synthèses
 * consolidées établissement
 * (confrontés au registre comptable, strictement en entiers FCFA {@code long}).
 */
@Service
public class FinanceAccountQueryService implements FinanceAccountPort {

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("2100-01-01T00:00:00Z");
    private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private final StudentInvoiceRepository invoiceRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReversalRepository paymentReversalRepository;
    private final FeeAdjustmentRepository feeAdjustmentRepository;
    private final CashClosingRepository cashClosingRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final FinanceSchoolContext schoolContext;

    public FinanceAccountQueryService(
            StudentInvoiceRepository invoiceRepository,
            PaymentScheduleRepository scheduleRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentRepository paymentRepository,
            PaymentReversalRepository paymentReversalRepository,
            FeeAdjustmentRepository feeAdjustmentRepository,
            CashClosingRepository cashClosingRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            FinanceSchoolContext schoolContext) {
        this.invoiceRepository = invoiceRepository;
        this.scheduleRepository = scheduleRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentRepository = paymentRepository;
        this.paymentReversalRepository = paymentReversalRepository;
        this.feeAdjustmentRepository = feeAdjustmentRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.schoolContext = schoolContext;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentFinancialSummary getStudentFinancialSummary(UUID studentId, UUID academicYearId) {
        TenantContext.requireCurrent();
        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "student-not-found", "Élève introuvable", "L'élève demandé est introuvable."));

        List<StudentInvoice> invoices = academicYearId != null
                ? invoiceRepository.findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(studentId, academicYearId)
                : invoiceRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        List<StudentInvoice> activeInvoices = invoices.stream()
                .filter(StudentInvoice::isIssuedOrActive)
                .toList();

        List<UUID> activeInvoiceIds = activeInvoices.stream().map(StudentInvoice::getId).toList();
        List<PaymentSchedule> schedules = activeInvoiceIds.isEmpty()
                ? List.of()
                : scheduleRepository.findByInvoiceIdIn(activeInvoiceIds);

        List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByStudentIdOrderBySequenceNoAsc(studentId);
        Set<UUID> scopedInvoiceIds = invoices.stream().map(StudentInvoice::getId).collect(Collectors.toSet());
        long ledgerBalance = ledgerEntries.stream()
                .filter(e -> academicYearId == null || scopedInvoiceIds.contains(e.getInvoiceId()))
                .map(LedgerEntry::signedBalanceDeltaXof)
                .reduce(0L, MoneyMath::addExact);

        LocalDate today = schoolContext.todayInSchoolZone();
        return aggregateStudent(student, academicYearId, activeInvoices, schedules, ledgerBalance, today);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentFinancialSummary> getStudentFinancialSummariesByIds(
            Collection<UUID> studentIds, UUID academicYearId) {
        TenantContext.requireCurrent();
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> validIds = studentIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return List.of();
        }
        List<StudentSummary> students = studentDirectoryPort.findStudentsByIds(validIds);
        if (students.isEmpty()) {
            return List.of();
        }
        return aggregateStudentsBatch(students, academicYearId);
    }

    @Override
    @Transactional(readOnly = true)
    public FamilyFinancialSummary getFamilyFinancialSummary(UUID familyId, UUID academicYearId) {
        TenantContext.requireCurrent();
        FamilySummary family = familyDirectoryPort.findFamilyById(familyId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "family-not-found", "Famille introuvable", "La famille demandée est introuvable."));

        List<UUID> studentIds = familyDirectoryPort.findStudentIdsByFamilyId(family.id());
        if (studentIds.isEmpty()) {
            return new FamilyFinancialSummary(
                    family.id(), family.name(), academicYearId, 0L, 0L, 0L, 0L, 0L, 0L, 0, List.of());
        }

        List<StudentSummary> students = studentDirectoryPort.findStudentsByIds(studentIds);
        List<StudentFinancialSummary> studentSummaries = aggregateStudentsBatch(students, academicYearId);

        long familyGross = 0L;
        long familyDiscount = 0L;
        long familyNet = 0L;
        long familyPaid = 0L;
        long familyBalance = 0L;
        long familyLedgerBalance = 0L;
        for (StudentFinancialSummary summary : studentSummaries) {
            familyGross = MoneyMath.addExact(familyGross, summary.totalGrossXof());
            familyDiscount = MoneyMath.addExact(familyDiscount, summary.totalDiscountXof());
            familyNet = MoneyMath.addExact(familyNet, summary.totalNetXof());
            familyPaid = MoneyMath.addExact(familyPaid, summary.totalPaidXof());
            familyBalance = MoneyMath.addExact(familyBalance, summary.totalBalanceXof());
            familyLedgerBalance = MoneyMath.addExact(familyLedgerBalance, summary.ledgerBalanceXof());
        }

        return new FamilyFinancialSummary(
                family.id(),
                family.name(),
                academicYearId,
                familyGross,
                familyDiscount,
                familyNet,
                familyPaid,
                familyBalance,
                familyLedgerBalance,
                studentSummaries.size(),
                studentSummaries);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findStudentLedgerBalanceXof(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null || studentDirectoryPort.findStudentById(studentId).isEmpty()) {
            return Optional.empty();
        }
        long balance = ledgerEntryRepository.findByStudentIdOrderBySequenceNoAsc(studentId).stream()
                .map(LedgerEntry::signedBalanceDeltaXof)
                .reduce(0L, MoneyMath::addExact);
        return Optional.of(balance);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialReportingOverview getFinancialReportingOverview(
            UUID academicYearId, LocalDate fromDate, LocalDate toDate) {
        TenantContext.requireCurrent();
        SchoolProfileSnapshot profile = schoolContext.requireSchoolProfile();
        ZoneId schoolZone = schoolContext.resolveSchoolZone(profile);
        LocalDate today = schoolContext.todayInSchoolZone();

        Instant startInclusive = fromDate != null ? fromDate.atStartOfDay(schoolZone).toInstant() : MIN_INSTANT;
        Instant endExclusive = toDate != null ? toDate.plusDays(1).atStartOfDay(schoolZone).toInstant() : MAX_INSTANT;

        List<StudentInvoice> invoices = academicYearId != null
                ? invoiceRepository.findByAcademicYearId(academicYearId)
                : invoiceRepository.findAll();

        long draftCount = 0L;
        long issuedCount = 0L;
        long partiallyPaidCount = 0L;
        long paidCount = 0L;
        long cancelledCount = 0L;
        long totalGrossXof = 0L;
        long totalDiscountXof = 0L;
        long totalNetXof = 0L;
        long totalInvoicePaidXof = 0L;
        long totalOutstandingXof = 0L;
        List<UUID> activeInvoiceIds = new ArrayList<>();

        for (StudentInvoice inv : invoices) {
            switch (inv.getStatus()) {
                case DRAFT -> draftCount++;
                case ISSUED -> issuedCount++;
                case PARTIALLY_PAID -> partiallyPaidCount++;
                case PAID -> paidCount++;
                case CANCELLED -> cancelledCount++;
            }
            if (inv.isIssuedOrActive()) {
                activeInvoiceIds.add(inv.getId());
                totalGrossXof = MoneyMath.addExact(totalGrossXof, inv.getGrossAmountXof());
                totalDiscountXof = MoneyMath.addExact(totalDiscountXof, inv.getDiscountAmountXof());
                totalNetXof = MoneyMath.addExact(totalNetXof, inv.getNetAmountXof());
                totalInvoicePaidXof = MoneyMath.addExact(totalInvoicePaidXof, inv.getPaidAmountXof());
                totalOutstandingXof = MoneyMath.addExact(totalOutstandingXof, inv.getBalanceXof());
            }
        }

        long overdueInstallmentCount = 0L;
        long overdueAmountXof = 0L;
        if (!activeInvoiceIds.isEmpty()) {
            for (PaymentSchedule schedule : scheduleRepository.findByInvoiceIdIn(activeInvoiceIds)) {
                if (schedule.isOverdueAt(today)) {
                    overdueInstallmentCount++;
                    overdueAmountXof = MoneyMath.addExact(overdueAmountXof, schedule.remainingAmountXof());
                }
            }
        }

        List<Object[]> adjustmentRows = academicYearId != null
                ? feeAdjustmentRepository.aggregateForYearAndRange(academicYearId, startInclusive, endExclusive)
                : feeAdjustmentRepository.aggregateForRange(startInclusive, endExclusive);
        long approvedDiscountCount = 0L;
        long approvedDiscountXof = 0L;
        long approvedScholarshipCount = 0L;
        long approvedScholarshipXof = 0L;
        long pendingAdjustmentCount = 0L;
        long pendingAdjustmentXof = 0L;
        for (Object[] row : adjustmentRows) {
            AdjustmentType type = (AdjustmentType) row[0];
            ApprovalStatus status = (ApprovalStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long amount = ((Number) row[3]).longValue();
            if (status == ApprovalStatus.APPROVED) {
                if (type == AdjustmentType.DISCOUNT) {
                    approvedDiscountCount = Math.addExact(approvedDiscountCount, count);
                    approvedDiscountXof = MoneyMath.addExact(approvedDiscountXof, amount);
                } else if (type == AdjustmentType.SCHOLARSHIP) {
                    approvedScholarshipCount = Math.addExact(approvedScholarshipCount, count);
                    approvedScholarshipXof = MoneyMath.addExact(approvedScholarshipXof, amount);
                }
            } else if (status == ApprovalStatus.PENDING_APPROVAL) {
                pendingAdjustmentCount = Math.addExact(pendingAdjustmentCount, count);
                pendingAdjustmentXof = MoneyMath.addExact(pendingAdjustmentXof, amount);
            }
        }

        Map<PaymentMethod, long[]> collectedByMethod = new EnumMap<>(PaymentMethod.class);
        List<Object[]> paymentRows = academicYearId != null
                ? paymentRepository.aggregateByPaymentMethodForYearAndRange(academicYearId, startInclusive,
                        endExclusive)
                : paymentRepository.aggregateByPaymentMethodForRange(startInclusive, endExclusive);
        long collectedPaymentsCount = 0L;
        long collectedPaymentsXof = 0L;
        for (Object[] row : paymentRows) {
            PaymentMethod method = (PaymentMethod) row[0];
            long amount = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            collectedByMethod.put(method, new long[] { amount, count });
            collectedPaymentsXof = MoneyMath.addExact(collectedPaymentsXof, amount);
            collectedPaymentsCount = Math.addExact(collectedPaymentsCount, count);
        }

        Map<PaymentMethod, long[]> reversedByMethod = new EnumMap<>(PaymentMethod.class);
        List<Object[]> reversalRows = academicYearId != null
                ? paymentReversalRepository.aggregateByPaymentMethodForYearAndRange(
                        academicYearId, startInclusive, endExclusive)
                : paymentReversalRepository.aggregateByPaymentMethodForRange(startInclusive, endExclusive);
        long reversalsCount = 0L;
        long reversalsXof = 0L;
        for (Object[] row : reversalRows) {
            PaymentMethod method = (PaymentMethod) row[0];
            long amount = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            reversedByMethod.put(method, new long[] { amount, count });
            reversalsXof = MoneyMath.addExact(reversalsXof, amount);
            reversalsCount = Math.addExact(reversalsCount, count);
        }

        List<PaymentMethodBreakdown> methodBreakdowns = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            long[] col = collectedByMethod.get(method);
            long[] rev = reversedByMethod.get(method);
            if (col == null && rev == null) {
                continue;
            }
            long colAmt = col != null ? col[0] : 0L;
            long colCnt = col != null ? col[1] : 0L;
            long revAmt = rev != null ? rev[0] : 0L;
            long revCnt = rev != null ? rev[1] : 0L;
            methodBreakdowns.add(new PaymentMethodBreakdown(
                    method.name(),
                    colAmt,
                    colCnt,
                    revAmt,
                    revCnt,
                    MoneyMath.subtractExact(colAmt, revAmt)));
        }

        long netCollectedXof = MoneyMath.subtractExact(collectedPaymentsXof, reversalsXof);
        long ledgerNetBalanceXof = academicYearId != null
                ? ledgerEntryRepository.sumSignedNetBalanceByAcademicYear(academicYearId)
                : ledgerEntryRepository.sumSignedNetBalanceAll();
        boolean ledgerConsistent = ledgerNetBalanceXof == totalOutstandingXof;

        LocalDate closingFrom = fromDate != null ? fromDate : MIN_DATE;
        LocalDate closingTo = toDate != null ? toDate : MAX_DATE;
        List<Object[]> closingRows = cashClosingRepository.aggregateForDateRange(closingFrom, closingTo);
        long closingTotal = 0L;
        long closingSubmitted = 0L;
        long closingValidated = 0L;
        long closingExpectedXof = 0L;
        long closingCountedXof = 0L;
        long closingDiscrepancyXof = 0L;
        if (!closingRows.isEmpty() && closingRows.getFirst() != null) {
            Object[] row = closingRows.getFirst();
            closingTotal = ((Number) row[0]).longValue();
            closingSubmitted = ((Number) row[1]).longValue();
            closingValidated = ((Number) row[2]).longValue();
            closingExpectedXof = ((Number) row[3]).longValue();
            closingCountedXof = ((Number) row[4]).longValue();
            closingDiscrepancyXof = ((Number) row[5]).longValue();
        }

        return new FinancialReportingOverview(
                academicYearId,
                fromDate,
                toDate,
                invoices.size(),
                draftCount,
                issuedCount,
                partiallyPaidCount,
                paidCount,
                cancelledCount,
                totalGrossXof,
                totalDiscountXof,
                totalNetXof,
                totalInvoicePaidXof,
                totalOutstandingXof,
                overdueInstallmentCount,
                overdueAmountXof,
                approvedDiscountCount,
                approvedDiscountXof,
                approvedScholarshipCount,
                approvedScholarshipXof,
                pendingAdjustmentCount,
                pendingAdjustmentXof,
                collectedPaymentsCount,
                collectedPaymentsXof,
                reversalsCount,
                reversalsXof,
                netCollectedXof,
                ledgerNetBalanceXof,
                ledgerConsistent,
                closingTotal,
                closingSubmitted,
                closingValidated,
                closingExpectedXof,
                closingCountedXof,
                closingDiscrepancyXof,
                List.copyOf(methodBreakdowns));
    }

    private List<StudentFinancialSummary> aggregateStudentsBatch(
            List<StudentSummary> students, UUID academicYearId) {
        List<UUID> studentIds = students.stream().map(StudentSummary::id).toList();
        List<StudentInvoice> invoices = academicYearId != null
                ? invoiceRepository.findByStudentIdInAndAcademicYearId(studentIds, academicYearId)
                : invoiceRepository.findByStudentIdIn(studentIds);
        Map<UUID, List<StudentInvoice>> invoicesByStudent = new HashMap<>();
        for (StudentInvoice invoice : invoices) {
            invoicesByStudent.computeIfAbsent(invoice.getStudentId(), k -> new ArrayList<>()).add(invoice);
        }

        List<UUID> activeInvoiceIds = invoices.stream()
                .filter(StudentInvoice::isIssuedOrActive)
                .map(StudentInvoice::getId)
                .toList();
        Map<UUID, List<PaymentSchedule>> schedulesByInvoice = new HashMap<>();
        if (!activeInvoiceIds.isEmpty()) {
            for (PaymentSchedule schedule : scheduleRepository.findByInvoiceIdIn(activeInvoiceIds)) {
                schedulesByInvoice.computeIfAbsent(schedule.getInvoiceId(), k -> new ArrayList<>()).add(schedule);
            }
        }

        Map<UUID, List<LedgerEntry>> ledgerByStudent = new HashMap<>();
        for (LedgerEntry entry : ledgerEntryRepository.findByStudentIdIn(studentIds)) {
            ledgerByStudent.computeIfAbsent(entry.getStudentId(), k -> new ArrayList<>()).add(entry);
        }

        LocalDate today = schoolContext.todayInSchoolZone();
        List<StudentFinancialSummary> summaries = new ArrayList<>(students.size());
        for (StudentSummary student : students) {
            List<StudentInvoice> studentInvoices = invoicesByStudent.getOrDefault(student.id(), List.of());
            List<StudentInvoice> active = studentInvoices.stream()
                    .filter(StudentInvoice::isIssuedOrActive)
                    .toList();
            List<PaymentSchedule> studentSchedules = new ArrayList<>();
            for (StudentInvoice inv : active) {
                studentSchedules.addAll(schedulesByInvoice.getOrDefault(inv.getId(), List.of()));
            }
            Set<UUID> scopedIds = studentInvoices.stream().map(StudentInvoice::getId).collect(Collectors.toSet());
            long ledgerBalance = ledgerByStudent.getOrDefault(student.id(), List.of()).stream()
                    .filter(e -> academicYearId == null || scopedIds.contains(e.getInvoiceId()))
                    .map(LedgerEntry::signedBalanceDeltaXof)
                    .reduce(0L, MoneyMath::addExact);

            summaries.add(aggregateStudent(student, academicYearId, active, studentSchedules, ledgerBalance, today));
        }
        return summaries;
    }

    private static StudentFinancialSummary aggregateStudent(
            StudentSummary student,
            UUID academicYearId,
            List<StudentInvoice> activeInvoices,
            List<PaymentSchedule> schedules,
            long ledgerBalance,
            LocalDate today) {
        long gross = 0L;
        long discount = 0L;
        long net = 0L;
        long paid = 0L;
        long balance = 0L;
        for (StudentInvoice inv : activeInvoices) {
            gross = MoneyMath.addExact(gross, inv.getGrossAmountXof());
            discount = MoneyMath.addExact(discount, inv.getDiscountAmountXof());
            net = MoneyMath.addExact(net, inv.getNetAmountXof());
            paid = MoneyMath.addExact(paid, inv.getPaidAmountXof());
            balance = MoneyMath.addExact(balance, inv.getBalanceXof());
        }
        int overdueCount = (int) schedules.stream().filter(s -> s.isOverdueAt(today)).count();
        return new StudentFinancialSummary(
                student.id(),
                student.matricule(),
                student.firstName(),
                student.lastName(),
                academicYearId,
                gross,
                discount,
                net,
                paid,
                balance,
                ledgerBalance,
                activeInvoices.size(),
                overdueCount);
    }
}
