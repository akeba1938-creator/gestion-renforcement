package com.ecole.saas.reporting.application;

import com.ecole.saas.finance.PaymentMethodBreakdown;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportingModels.AcademicClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.AcademicReportView;
import com.ecole.saas.reporting.application.ReportingModels.AttendanceClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.AttendanceReportView;
import com.ecole.saas.reporting.application.ReportingModels.ExecutiveReportView;
import com.ecole.saas.reporting.application.ReportingModels.FinancialClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.FinancialReportView;
import com.ecole.saas.reporting.application.ReportingModels.OperationsReportView;
import com.ecole.saas.reporting.application.ReportingModels.StudentOverviewReportView;
import com.ecole.saas.reporting.domain.CsvFormulaSanitizer;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schoolimport.ImportType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Sérialiseur applicatif des rapports consolidés vers JSON (Jackson 3) ou CSV sécurisé anti-CSV-Injection,
 * et générateur de résumé JSON sans PII sensible pour les métadonnées de snapshots.
 */
@Component
class ReportPayloadSerializer {

    private final ObjectMapper objectMapper;

    ReportPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    byte[] serialize(ReportType reportType, ReportFormat format, Object reportPayload) {
        if (format == ReportFormat.JSON) {
            return objectMapper.writeValueAsString(reportPayload).getBytes(StandardCharsets.UTF_8);
        }
        return switch (reportType) {
            case EXECUTIVE -> serializeExecutiveCsv((ExecutiveReportView) reportPayload);
            case ACADEMIC -> serializeAcademicCsv((AcademicReportView) reportPayload);
            case ATTENDANCE -> serializeAttendanceCsv((AttendanceReportView) reportPayload);
            case FINANCIAL -> serializeFinancialCsv((FinancialReportView) reportPayload);
            case OPERATIONS -> serializeOperationsCsv((OperationsReportView) reportPayload);
            case STUDENT_OVERVIEW -> serializeStudentOverviewCsv((StudentOverviewReportView) reportPayload);
        };
    }

    /**
     * Construit un résumé compact JSON ne contenant aucune PII sensible (uniquement compteurs/agrégats).
     */
    String buildSummaryJson(ReportType reportType, Object reportPayload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportType", reportType.name());
        switch (reportType) {
            case EXECUTIVE -> {
                ExecutiveReportView v = (ExecutiveReportView) reportPayload;
                summary.put("classCount", v.academic().classCount());
                summary.put("totalEnrolled", v.academic().totalEnrolledInScope());
                summary.put("passRatePercent", v.academic().passRatePercent());
                summary.put("attendanceRatePercent", v.attendance().attendanceRatePercent());
                summary.put("totalNetXof", v.financial().totalNetXof());
                summary.put("totalOutstandingXof", v.financial().totalOutstandingXof());
                summary.put("totalDocuments", v.operations().totalDocuments());
                summary.put("totalImportJobs", v.operations().totalImportJobs());
            }
            case ACADEMIC -> {
                AcademicReportView v = (AcademicReportView) reportPayload;
                summary.put("classCount", v.classCount());
                summary.put("totalEnrolled", v.totalEnrolledInScope());
                summary.put("reportCardCount", v.reportCardCount());
                summary.put("overallAverage", v.overallAverage());
                summary.put("passRatePercent", v.passRatePercent());
            }
            case ATTENDANCE -> {
                AttendanceReportView v = (AttendanceReportView) reportPayload;
                summary.put("sessionCount", v.sessionCount());
                summary.put("totalRecords", v.totalRecords());
                summary.put("absentCount", v.absentCount());
                summary.put("attendanceRatePercent", v.attendanceRatePercent());
            }
            case FINANCIAL -> {
                FinancialReportView v = (FinancialReportView) reportPayload;
                summary.put("totalInvoiceCount", v.totalInvoiceCount());
                summary.put("totalNetXof", v.totalNetXof());
                summary.put("totalInvoicePaidXof", v.totalInvoicePaidXof());
                summary.put("totalOutstandingXof", v.totalOutstandingXof());
                summary.put("netCollectedXof", v.netCollectedXof());
                summary.put("ledgerConsistentWithInvoices", v.ledgerConsistentWithInvoices());
                summary.put("cashClosingDiscrepancyXof", v.cashClosingDiscrepancyXof());
            }
            case OPERATIONS -> {
                OperationsReportView v = (OperationsReportView) reportPayload;
                summary.put("totalDocuments", v.totalDocuments());
                summary.put("totalDocumentFeesXof", v.totalDocumentFeesXof());
                summary.put("totalImportJobs", v.totalImportJobs());
                summary.put("totalImportedCreatedRows", v.totalImportedCreatedRows());
            }
            case STUDENT_OVERVIEW -> {
                StudentOverviewReportView v = (StudentOverviewReportView) reportPayload;
                summary.put("studentId", v.studentId());
                summary.put("gradePeriodsCount", v.gradeSummaries().size());
                summary.put("attendanceRatePercent",
                        v.attendanceSummary() != null ? v.attendanceSummary().attendanceRatePercent() : null);
                summary.put("issuedDocumentsCount", v.issuedDocumentsCount());
                summary.put("financeIncluded", v.financialSummary() != null);
            }
        }
        return objectMapper.writeValueAsString(summary);
    }

    private static byte[] serializeAcademicCsv(AcademicReportView view) {
        List<String> headers = List.of(
                "classe_code",
                "classe_nom",
                "niveau_code",
                "capacite",
                "inscrits_actifs",
                "taux_remplissage_pct",
                "bulletins",
                "eleves_classes",
                "eleves_admis",
                "taux_reussite_pct",
                "moyenne_classe",
                "moyenne_min",
                "moyenne_max",
                "bulletins_brouillon",
                "bulletins_valides",
                "bulletins_verrouilles");
        List<List<String>> rows = new ArrayList<>();
        for (AcademicClassMetrics c : view.classes()) {
            rows.add(List.of(
                    str(c.classRoomCode()),
                    str(c.classRoomName()),
                    str(c.gradeLevelCode()),
                    String.valueOf(c.capacity()),
                    String.valueOf(c.activeEnrollmentCount()),
                    str(c.occupancyRatePercent()),
                    String.valueOf(c.reportCardCount()),
                    String.valueOf(c.rankedStudentCount()),
                    String.valueOf(c.passedStudentCount()),
                    str(c.passRatePercent()),
                    str(c.classAverage()),
                    str(c.minAverage()),
                    str(c.maxAverage()),
                    String.valueOf(c.draftReportCardCount()),
                    String.valueOf(c.validatedReportCardCount()),
                    String.valueOf(c.lockedReportCardCount())));
        }
        rows.add(List.of(
                "TOTAL",
                str(view.schoolName()),
                "",
                String.valueOf(view.totalCapacityInScope()),
                String.valueOf(view.totalEnrolledInScope()),
                str(view.occupancyRatePercent()),
                String.valueOf(view.reportCardCount()),
                String.valueOf(view.rankedStudentCount()),
                String.valueOf(view.passedStudentCount()),
                str(view.passRatePercent()),
                str(view.overallAverage()),
                str(view.minAverage()),
                str(view.maxAverage()),
                String.valueOf(view.draftReportCardCount()),
                String.valueOf(view.validatedReportCardCount()),
                String.valueOf(view.lockedReportCardCount())));
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static byte[] serializeAttendanceCsv(AttendanceReportView view) {
        List<String> headers = List.of(
                "classe_code",
                "classe_nom",
                "seances",
                "pointages_total",
                "presents",
                "retards",
                "minutes_retard_total",
                "absences_total",
                "absences_justifiees",
                "absences_injustifiees",
                "excuses",
                "taux_presence_pct");
        List<List<String>> rows = new ArrayList<>();
        for (AttendanceClassMetrics c : view.classes()) {
            rows.add(List.of(
                    str(c.classRoomCode()),
                    str(c.classRoomName()),
                    String.valueOf(c.sessionCount()),
                    String.valueOf(c.totalRecords()),
                    String.valueOf(c.presentCount()),
                    String.valueOf(c.lateCount()),
                    String.valueOf(c.totalLateMinutes()),
                    String.valueOf(c.absentCount()),
                    String.valueOf(c.justifiedAbsenceCount()),
                    String.valueOf(c.unjustifiedAbsenceCount()),
                    String.valueOf(c.excusedCount()),
                    str(c.attendanceRatePercent())));
        }
        rows.add(List.of(
                "TOTAL",
                str(view.schoolName()),
                String.valueOf(view.sessionCount()),
                String.valueOf(view.totalRecords()),
                String.valueOf(view.presentCount()),
                String.valueOf(view.lateCount()),
                String.valueOf(view.totalLateMinutes()),
                String.valueOf(view.absentCount()),
                String.valueOf(view.justifiedAbsenceCount()),
                String.valueOf(view.unjustifiedAbsenceCount()),
                String.valueOf(view.excusedCount()),
                str(view.attendanceRatePercent())));
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static byte[] serializeFinancialCsv(FinancialReportView view) {
        List<String> headers = List.of(
                "section",
                "cle",
                "libelle",
                "nombre",
                "brut_fcfa",
                "remises_fcfa",
                "net_fcfa",
                "encaisse_fcfa",
                "impaye_fcfa",
                "taux_recouvrement_pct");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "SYNTHESE",
                str(view.schoolCode()),
                str(view.schoolName()),
                String.valueOf(view.totalInvoiceCount()),
                String.valueOf(view.totalGrossXof()),
                String.valueOf(view.totalDiscountXof()),
                String.valueOf(view.totalNetXof()),
                String.valueOf(view.totalInvoicePaidXof()),
                String.valueOf(view.totalOutstandingXof()),
                str(view.collectionRatePercent())));
        rows.add(List.of(
                "CLOTURES_CAISSE",
                "CAISSE",
                "Clôtures de caisse (écart=" + view.cashClosingDiscrepancyXof() + " FCFA)",
                String.valueOf(view.cashClosingTotalCount()),
                String.valueOf(view.cashClosingExpectedXof()),
                "0",
                String.valueOf(view.cashClosingExpectedXof()),
                String.valueOf(view.cashClosingCountedXof()),
                String.valueOf(view.cashClosingDiscrepancyXof()),
                ""));
        for (PaymentMethodBreakdown pm : view.paymentMethods()) {
            rows.add(List.of(
                    "MODE_PAIEMENT",
                    str(pm.paymentMethod()),
                    "Encaissements " + str(pm.paymentMethod()),
                    String.valueOf(pm.paymentCount()),
                    String.valueOf(pm.collectedAmountXof()),
                    String.valueOf(pm.reversedAmountXof()),
                    String.valueOf(pm.netCollectedAmountXof()),
                    String.valueOf(pm.netCollectedAmountXof()),
                    "0",
                    ""));
        }
        for (FinancialClassMetrics c : view.classes()) {
            rows.add(List.of(
                    "CLASSE",
                    str(c.classRoomCode()),
                    str(c.classRoomName()),
                    String.valueOf(c.enrolledStudentCount()),
                    String.valueOf(c.totalGrossXof()),
                    String.valueOf(c.totalDiscountXof()),
                    String.valueOf(c.totalNetXof()),
                    String.valueOf(c.totalPaidXof()),
                    String.valueOf(c.totalOutstandingXof()),
                    str(c.collectionRatePercent())));
        }
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static byte[] serializeOperationsCsv(OperationsReportView view) {
        List<String> headers = List.of("domaine", "indicateur", "valeur", "unite");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("SCHOOLDOCS", "total_documents", String.valueOf(view.totalDocuments()), "count"));
        rows.add(List.of("SCHOOLDOCS", "documents_emis", String.valueOf(view.issuedDocumentsCount()), "count"));
        rows.add(List.of("SCHOOLDOCS", "documents_duplicatas", String.valueOf(view.duplicateDocumentsCount()), "count"));
        rows.add(List.of("SCHOOLDOCS", "documents_revoques", String.valueOf(view.revokedDocumentsCount()), "count"));
        rows.add(List.of("SCHOOLDOCS", "frais_documents_fcfa", String.valueOf(view.totalDocumentFeesXof()), "XOF"));
        rows.add(List.of("SCHOOLDOCS", "stockage_octets", String.valueOf(view.documentsStorageBytes()), "bytes"));
        for (Map.Entry<DocumentType, Long> entry : view.documentsByType().entrySet()) {
            rows.add(List.of("SCHOOLDOCS_TYPE", entry.getKey().name(), String.valueOf(entry.getValue()), "count"));
        }
        rows.add(List.of("SCHOOLIMPORT", "total_lots", String.valueOf(view.totalImportJobs()), "count"));
        rows.add(List.of("SCHOOLIMPORT", "lots_termines", String.valueOf(view.completedImportsCount()), "count"));
        rows.add(List.of("SCHOOLIMPORT", "lots_termines_avec_erreurs", String.valueOf(view.completedWithErrorsImportsCount()), "count"));
        rows.add(List.of("SCHOOLIMPORT", "lignes_traitees", String.valueOf(view.totalImportedRowsProcessed()), "rows"));
        rows.add(List.of("SCHOOLIMPORT", "lignes_creees", String.valueOf(view.totalImportedCreatedRows()), "rows"));
        rows.add(List.of("SCHOOLIMPORT", "lignes_mises_a_jour", String.valueOf(view.totalImportedUpdatedRows()), "rows"));
        rows.add(List.of("SCHOOLIMPORT", "lignes_en_erreur", String.valueOf(view.totalImportedErrorRows()), "rows"));
        rows.add(List.of("SCHOOLIMPORT", "stockage_octets", String.valueOf(view.importsStorageBytes()), "bytes"));
        for (Map.Entry<ImportType, Long> entry : view.importsByType().entrySet()) {
            rows.add(List.of("SCHOOLIMPORT_TYPE", entry.getKey().name(), String.valueOf(entry.getValue()), "count"));
        }
        rows.add(List.of("REPORTING", "snapshots_total", String.valueOf(view.reportingSnapshotsCount()), "count"));
        rows.add(List.of("REPORTING", "stockage_octets", String.valueOf(view.reportingStorageBytes()), "bytes"));
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static byte[] serializeExecutiveCsv(ExecutiveReportView view) {
        List<String> headers = List.of("domaine", "indicateur", "valeur", "unite");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("ETABLISSEMENT", "code", str(view.schoolCode()), "text"));
        rows.add(List.of("ETABLISSEMENT", "nom", str(view.schoolName()), "text"));
        rows.add(List.of("ACADEMIQUE", "classes", String.valueOf(view.academic().classCount()), "count"));
        rows.add(List.of("ACADEMIQUE", "inscrits_actifs", String.valueOf(view.academic().totalEnrolledInScope()), "count"));
        rows.add(List.of("ACADEMIQUE", "moyenne_generale", str(view.academic().overallAverage()), "score"));
        rows.add(List.of("ACADEMIQUE", "taux_reussite_pct", str(view.academic().passRatePercent()), "percent"));
        rows.add(List.of("ASSIDUITE", "seances_appel", String.valueOf(view.attendance().sessionCount()), "count"));
        rows.add(List.of("ASSIDUITE", "absences_total", String.valueOf(view.attendance().absentCount()), "count"));
        rows.add(List.of("ASSIDUITE", "taux_presence_pct", str(view.attendance().attendanceRatePercent()), "percent"));
        rows.add(List.of("FINANCE", "net_facture_fcfa", String.valueOf(view.financial().totalNetXof()), "XOF"));
        rows.add(List.of("FINANCE", "encaisse_net_fcfa", String.valueOf(view.financial().netCollectedXof()), "XOF"));
        rows.add(List.of("FINANCE", "impayes_fcfa", String.valueOf(view.financial().totalOutstandingXof()), "XOF"));
        rows.add(List.of("FINANCE", "remises_approuvees_fcfa", String.valueOf(view.financial().approvedDiscountXof()), "XOF"));
        rows.add(List.of("FINANCE", "bourses_approuvees_fcfa", String.valueOf(view.financial().approvedScholarshipXof()), "XOF"));
        rows.add(List.of("FINANCE", "taux_recouvrement_pct", str(view.financial().collectionRatePercent()), "percent"));
        rows.add(List.of("FINANCE", "ecart_clotures_caisse_fcfa", String.valueOf(view.financial().cashClosingDiscrepancyXof()), "XOF"));
        rows.add(List.of("OPERATIONS", "documents_officiels", String.valueOf(view.operations().totalDocuments()), "count"));
        rows.add(List.of("OPERATIONS", "imports_massifs", String.valueOf(view.operations().totalImportJobs()), "count"));
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static byte[] serializeStudentOverviewCsv(StudentOverviewReportView view) {
        List<String> headers = List.of("section", "indicateur", "valeur");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("ELEVE", "matricule", str(view.studentMatricule())));
        rows.add(List.of("ELEVE", "prenom", str(view.studentFirstName())));
        rows.add(List.of("ELEVE", "nom", str(view.studentLastName())));
        rows.add(List.of("ELEVE", "classe", str(view.classRoomName())));
        for (StudentPeriodGradeSummary grade : view.gradeSummaries()) {
            rows.add(List.of(
                    "BULLETIN",
                    "moyenne_generale_" + grade.evaluationPeriodId(),
                    str(grade.generalAverage())));
            rows.add(List.of(
                    "BULLETIN",
                    "mention_" + grade.evaluationPeriodId(),
                    str(grade.mention())));
            rows.add(List.of(
                    "BULLETIN",
                    "rang_" + grade.evaluationPeriodId(),
                    str(grade.rankInClass())));
        }
        if (view.attendanceSummary() != null) {
            rows.add(List.of("ASSIDUITE", "seances", String.valueOf(view.attendanceSummary().totalSessions())));
            rows.add(List.of("ASSIDUITE", "absences", String.valueOf(view.attendanceSummary().absentCount())));
            rows.add(List.of("ASSIDUITE", "taux_presence_pct", str(view.attendanceSummary().attendanceRatePercent())));
        }
        if (view.financialSummary() != null) {
            rows.add(List.of("FINANCE", "net_fcfa", String.valueOf(view.financialSummary().totalNetXof())));
            rows.add(List.of("FINANCE", "paye_fcfa", String.valueOf(view.financialSummary().totalPaidXof())));
            rows.add(List.of("FINANCE", "solde_impaye_fcfa", String.valueOf(view.financialSummary().totalBalanceXof())));
        }
        rows.add(List.of("DOCUMENTS", "documents_emis", String.valueOf(view.issuedDocumentsCount())));
        return CsvFormulaSanitizer.renderTable(headers, rows);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
