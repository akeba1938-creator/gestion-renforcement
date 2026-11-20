package com.ecole.saas.finance.application;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.finance.FamilyFinancialSummary;
import com.ecole.saas.finance.FinanceAccountPort;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.finance.domain.FinanceNotFoundException;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.MoneyMath;
import com.ecole.saas.finance.domain.PaymentSchedule;
import com.ecole.saas.finance.domain.StudentInvoice;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import com.ecole.saas.finance.infrastructure.PaymentScheduleRepository;
import com.ecole.saas.finance.infrastructure.StudentInvoiceRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link FinanceAccountPort} en lecture seule
 * calculant les soldes financiers par élève et par famille (confrontés au registre comptable).
 */
@Service
public class FinanceAccountQueryService implements FinanceAccountPort {

    private final StudentInvoiceRepository invoiceRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final FinanceSchoolContext schoolContext;

    public FinanceAccountQueryService(
            StudentInvoiceRepository invoiceRepository,
            PaymentScheduleRepository scheduleRepository,
            LedgerEntryRepository ledgerEntryRepository,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            FinanceSchoolContext schoolContext) {
        this.invoiceRepository = invoiceRepository;
        this.scheduleRepository = scheduleRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
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
        List<StudentFinancialSummary> studentSummaries = new ArrayList<>(students.size());
        long familyGross = 0L;
        long familyDiscount = 0L;
        long familyNet = 0L;
        long familyPaid = 0L;
        long familyBalance = 0L;
        long familyLedgerBalance = 0L;

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

            StudentFinancialSummary summary =
                    aggregateStudent(student, academicYearId, active, studentSchedules, ledgerBalance, today);
            studentSummaries.add(summary);

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
