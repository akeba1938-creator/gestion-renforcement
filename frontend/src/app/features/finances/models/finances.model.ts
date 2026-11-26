/**
 * Strictly typed contracts aligned with backend `com.ecole.saas.finance.api`
 * and `com.ecole.saas.reporting.api` DTOs.
 */

export type BackendPaymentMethod =
  | 'CASH'
  | 'CHECK'
  | 'BANK_TRANSFER'
  | 'MOBILE_MONEY_WAVE'
  | 'MOBILE_MONEY_OM'
  | 'MOBILE_MONEY_FREE'
  | 'OTHER';

export type PrimaryPaymentMethod = 'MOBILE_MONEY_WAVE' | 'MOBILE_MONEY_OM' | 'CASH';

export type BackendInvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export type PaymentConfirmationSource = 'MANUAL_DESK' | 'VERIFIED_WEBHOOK';

export interface BackendPageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface ReceiptResponseDto {
  readonly id: string;
  readonly paymentId: string;
  readonly invoiceId: string;
  readonly studentId: string;
  readonly receiptNumber: string;
  readonly verificationToken: string;
  readonly amountXof: number;
  readonly currency: string;
  readonly paymentMethod: BackendPaymentMethod;
  readonly issuedAt: string;
  readonly issuedBy: string | null;
}

export interface PaymentAllocationResponseDto {
  readonly id: string;
  readonly scheduleId: string;
  readonly amountXof: number;
}

export interface PaymentResponseDto {
  readonly id: string;
  readonly invoiceId: string;
  readonly studentId: string;
  readonly payerGuardianId: string | null;
  readonly requestedScheduleId: string | null;
  readonly amountXof: number;
  readonly currency: string;
  readonly paymentMethod: BackendPaymentMethod;
  readonly externalReference: string | null;
  readonly confirmationSource: PaymentConfirmationSource;
  readonly idempotencyKey: string | null;
  readonly paidAt: string;
  readonly recordedBy: string | null;
  readonly notes: string | null;
  readonly reversed: boolean;
  readonly reversedAt: string | null;
  readonly receipt: ReceiptResponseDto | null;
  readonly allocations: readonly PaymentAllocationResponseDto[];
}

export interface RecordPaymentRequestDto {
  readonly invoiceId: string;
  readonly paymentScheduleId?: string | null;
  readonly payerGuardianId?: string | null;
  readonly amountXof: number;
  readonly paymentMethod: BackendPaymentMethod;
  readonly externalReference?: string | null;
  readonly idempotencyKey?: string | null;
  readonly notes?: string | null;
}

export interface ReceiptVerificationResponseDto {
  readonly receiptId: string;
  readonly receiptNumber: string;
  readonly amountXof: number;
  readonly currency: string;
  readonly paymentMethod: BackendPaymentMethod;
  readonly issuedAt: string;
  readonly invoiceNumber: string;
  readonly studentMatricule: string;
  readonly reversed: boolean;
  readonly reversedAt: string | null;
}

export interface InvoiceResponseDto {
  readonly id: string;
  readonly invoiceNumber: string;
  readonly studentId: string;
  readonly academicYearId: string;
  readonly enrollmentId: string | null;
  readonly payerGuardianId: string | null;
  readonly familyId: string | null;
  readonly title: string;
  readonly currency: string;
  readonly grossAmountXof: number;
  readonly discountAmountXof: number;
  readonly netAmountXof: number;
  readonly paidAmountXof: number;
  readonly balanceXof: number;
  readonly status: BackendInvoiceStatus;
  readonly issueDate: string | null;
  readonly dueDate: string | null;
  readonly issuedAt: string | null;
  readonly issuedBy: string | null;
  readonly cancelledAt: string | null;
  readonly cancelledBy: string | null;
  readonly cancellationReason: string | null;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface FinancialReportViewDto {
  readonly schoolCode: string;
  readonly schoolName: string;
  readonly currency: string;
  readonly totalInvoiceCount: number;
  readonly issuedInvoiceCount: number;
  readonly partiallyPaidInvoiceCount: number;
  readonly paidInvoiceCount: number;
  readonly cancelledInvoiceCount: number;
  readonly totalNetXof: number;
  readonly totalInvoicePaidXof: number;
  readonly totalOutstandingXof: number;
  readonly collectionRatePercent: number | string;
  readonly overdueInstallmentCount: number;
  readonly overdueAmountXof: number;
  readonly collectedPaymentsCount: number;
  readonly collectedPaymentsXof: number;
  readonly reversalsCount: number;
  readonly reversalsXof: number;
  readonly netCollectedXof: number;
  readonly generatedAt: string;
}

export type FinancePeriod = 'today' | 'month' | 'term';

export type FinancePaymentMethodFilter = 'ALL' | PrimaryPaymentMethod;

export type FinanceTransactionStatus = 'VALIDATED' | 'PENDING' | 'OVERDUE' | 'CANCELLED';

export type FinanceStatusFilter = 'ALL' | FinanceTransactionStatus;

export type FinanceSortField = 'paidAt' | 'amountXof' | 'paymentMethod' | 'createdAt';
export type FinanceSortDirection = 'ASC' | 'DESC';

export interface FinancesListQuery {
  readonly search?: string;
  readonly paymentMethod?: FinancePaymentMethodFilter;
  readonly status?: FinanceStatusFilter;
  readonly period: FinancePeriod;
  readonly page: number;
  readonly size: number;
  readonly sortBy?: FinanceSortField;
  readonly direction?: FinanceSortDirection;
}

export interface FinanceTransactionRecord {
  readonly id: string;
  readonly paymentId: string | null;
  readonly invoiceId: string;
  readonly invoiceNumber: string;
  readonly receiptNumber: string;
  readonly verificationToken: string | null;
  readonly externalReference: string | null;
  readonly studentId: string;
  readonly studentName: string;
  readonly studentInitials: string;
  readonly matricule: string;
  readonly className: string;
  readonly recordedAtUtc: string;
  readonly method: BackendPaymentMethod;
  readonly status: FinanceTransactionStatus;
  readonly amountFcfa: number;
  readonly notes: string | null;
  readonly confirmationSource: PaymentConfirmationSource | null;
  readonly reversedAtUtc: string | null;
}

export interface FinancePayableInvoice {
  readonly id: string;
  readonly invoiceNumber: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly matricule: string;
  readonly className: string;
  readonly title: string;
  readonly netAmountFcfa: number;
  readonly paidAmountFcfa: number;
  readonly balanceFcfa: number;
  readonly dueDate: string | null;
  readonly status: BackendInvoiceStatus;
}

export interface FinanceKpiSummary {
  readonly period: FinancePeriod;
  readonly collectedFcfa: number;
  readonly collectedTrendLabel: string;
  readonly collectedComparisonLabel: string;
  readonly overdueFcfa: number;
  readonly overdueCount: number;
  readonly overdueSubLabel: string;
  readonly collectionRatePercent: number;
  readonly collectionRateTrendLabel: string;
  readonly collectionRateComparisonLabel: string;
  readonly pendingFcfa: number;
  readonly pendingCount: number;
  readonly pendingSubLabel: string;
}

export interface FinancesPageResult {
  readonly content: readonly FinanceTransactionRecord[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly kpis: FinanceKpiSummary;
  readonly payableInvoices: readonly FinancePayableInvoice[];
}

export interface RecordPaymentInput {
  readonly invoiceId: string;
  readonly amountFcfa: number;
  readonly paymentMethod: PrimaryPaymentMethod;
  readonly externalReference?: string;
  readonly notes?: string;
  readonly idempotencyKey?: string;
}

export interface ReceiptVerificationResult {
  readonly receiptId: string;
  readonly receiptNumber: string;
  readonly amountFcfa: number;
  readonly paymentMethod: BackendPaymentMethod;
  readonly issuedAtUtc: string;
  readonly invoiceNumber: string;
  readonly studentMatricule: string;
  readonly reversed: boolean;
  readonly reversedAtUtc: string | null;
}

export function requiresExternalReference(method: BackendPaymentMethod): boolean {
  return (
    method === 'MOBILE_MONEY_WAVE' ||
    method === 'MOBILE_MONEY_OM' ||
    method === 'MOBILE_MONEY_FREE' ||
    method === 'CHECK' ||
    method === 'BANK_TRANSFER'
  );
}

export function buildStudentInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return 'EL';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0].charAt(0)}${parts[parts.length - 1].charAt(0)}`.toUpperCase();
}

export function generatePaymentIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `pay-${crypto.randomUUID()}`;
  }
  const randomPart = Math.random().toString(36).slice(2, 12);
  return `pay-${Date.now()}-${randomPart}`;
}

export function buildPeriodDateRange(
  period: FinancePeriod,
  referenceDate: Date = new Date()
): { readonly fromDate: string; readonly toDate: string } {
  const dakarFormatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dakar',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  const todayIso = dakarFormatter.format(referenceDate);
  const [yearStr, monthStr] = todayIso.split('-');
  const year = Number.parseInt(yearStr, 10);
  const month = Number.parseInt(monthStr, 10);

  if (period === 'today') {
    return { fromDate: todayIso, toDate: todayIso };
  }

  if (period === 'month') {
    return {
      fromDate: `${yearStr}-${monthStr}-01`,
      toDate: todayIso,
    };
  }

  // Academic term (~90 days window aligned to start of 3-month block)
  const termStartMonth = Math.max(1, month - 2);
  const termStartMonthStr = String(termStartMonth).padStart(2, '0');
  return {
    fromDate: `${year}-${termStartMonthStr}-01`,
    toDate: todayIso,
  };
}

export function mapRecordPaymentInputToDto(
  input: RecordPaymentInput,
  resolvedIdempotencyKey: string
): RecordPaymentRequestDto {
  const trimmedRef = input.externalReference?.trim() || null;
  const trimmedNotes = input.notes?.trim() || null;

  return {
    invoiceId: input.invoiceId.trim(),
    amountXof: Math.trunc(input.amountFcfa),
    paymentMethod: input.paymentMethod,
    externalReference: trimmedRef,
    idempotencyKey: resolvedIdempotencyKey,
    notes: trimmedNotes,
  };
}

export function mapPaymentDtoToTransaction(
  dto: PaymentResponseDto,
  enrichment?: {
    readonly invoiceNumber?: string;
    readonly studentName?: string;
    readonly matricule?: string;
    readonly className?: string;
  }
): FinanceTransactionRecord {
  const studentName = enrichment?.studentName?.trim() || `Élève #${dto.studentId.slice(0, 8).toUpperCase()}`;
  const matricule = enrichment?.matricule?.trim() || `ID-${dto.studentId.slice(0, 8).toUpperCase()}`;
  const invoiceNumber =
    enrichment?.invoiceNumber?.trim() || `FAC-${dto.invoiceId.slice(0, 8).toUpperCase()}`;
  const receiptNumber =
    dto.receipt?.receiptNumber?.trim() ||
    dto.externalReference?.trim() ||
    `PAY-${dto.id.slice(0, 8).toUpperCase()}`;

  return {
    id: dto.id,
    paymentId: dto.id,
    invoiceId: dto.invoiceId,
    invoiceNumber,
    receiptNumber,
    verificationToken: dto.receipt?.verificationToken ?? null,
    externalReference: dto.externalReference,
    studentId: dto.studentId,
    studentName,
    studentInitials: buildStudentInitials(studentName),
    matricule,
    className: enrichment?.className?.trim() || 'Non affecté',
    recordedAtUtc: dto.paidAt,
    method: dto.paymentMethod,
    status: dto.reversed ? 'CANCELLED' : 'VALIDATED',
    amountFcfa: Math.trunc(dto.amountXof),
    notes: dto.notes,
    confirmationSource: dto.confirmationSource,
    reversedAtUtc: dto.reversedAt,
  };
}

export function mapInvoiceDtoToPayable(
  dto: InvoiceResponseDto,
  enrichment?: {
    readonly studentName?: string;
    readonly matricule?: string;
    readonly className?: string;
  }
): FinancePayableInvoice {
  return {
    id: dto.id,
    invoiceNumber: dto.invoiceNumber,
    studentId: dto.studentId,
    studentName: enrichment?.studentName?.trim() || dto.title,
    matricule: enrichment?.matricule?.trim() || dto.invoiceNumber,
    className: enrichment?.className?.trim() || 'Scolarité',
    title: dto.title,
    netAmountFcfa: Math.trunc(dto.netAmountXof),
    paidAmountFcfa: Math.trunc(dto.paidAmountXof),
    balanceFcfa: Math.trunc(dto.balanceXof),
    dueDate: dto.dueDate,
    status: dto.status,
  };
}

export function mapVerificationDtoToResult(
  dto: ReceiptVerificationResponseDto
): ReceiptVerificationResult {
  return {
    receiptId: dto.receiptId,
    receiptNumber: dto.receiptNumber,
    amountFcfa: Math.trunc(dto.amountXof),
    paymentMethod: dto.paymentMethod,
    issuedAtUtc: dto.issuedAt,
    invoiceNumber: dto.invoiceNumber,
    studentMatricule: dto.studentMatricule,
    reversed: dto.reversed,
    reversedAtUtc: dto.reversedAt,
  };
}

export function buildKpisFromFinancialReport(
  report: FinancialReportViewDto,
  period: FinancePeriod,
  pendingTransactions: readonly FinanceTransactionRecord[]
): FinanceKpiSummary {
  const rateRaw =
    typeof report.collectionRatePercent === 'string'
      ? Number.parseFloat(report.collectionRatePercent)
      : report.collectionRatePercent;
  const collectionRatePercent = Number.isFinite(rateRaw) ? Math.round(rateRaw * 10) / 10 : 0;
  const pendingFcfa = pendingTransactions.reduce((sum, tx) => sum + tx.amountFcfa, 0);
  const pendingCount = pendingTransactions.length;

  return {
    period,
    collectedFcfa: Math.trunc(report.netCollectedXof),
    collectedTrendLabel: `${Math.trunc(report.collectedPaymentsCount)} encaissements`,
    collectedComparisonLabel:
      period === 'today'
        ? 'cumul net du jour'
        : period === 'month'
          ? 'cumul net du mois'
          : 'cumul net du trimestre',
    overdueFcfa: Math.trunc(report.overdueAmountXof),
    overdueCount: Math.trunc(report.overdueInstallmentCount),
    overdueSubLabel: 'échéances échues',
    collectionRatePercent,
    collectionRateTrendLabel:
      collectionRatePercent >= 85 ? 'En ligne' : collectionRatePercent >= 70 ? 'À suivre' : 'Tension',
    collectionRateComparisonLabel: 'sur facturation nette',
    pendingFcfa,
    pendingCount,
    pendingSubLabel: pendingCount > 0 ? 'à rapprocher' : 'aucun suspens',
  };
}

export function computeFallbackKpisFromTransactions(
  transactions: readonly FinanceTransactionRecord[],
  period: FinancePeriod
): FinanceKpiSummary {
  const validated = transactions.filter((t) => t.status === 'VALIDATED');
  const overdue = transactions.filter((t) => t.status === 'OVERDUE');
  const pending = transactions.filter((t) => t.status === 'PENDING');

  const collectedFcfa = validated.reduce((sum, t) => sum + t.amountFcfa, 0);
  const overdueFcfa = overdue.reduce((sum, t) => sum + t.amountFcfa, 0);
  const pendingFcfa = pending.reduce((sum, t) => sum + t.amountFcfa, 0);
  const totalExpected = collectedFcfa + overdueFcfa + pendingFcfa;
  const collectionRatePercent =
    totalExpected > 0 ? Math.round((collectedFcfa / totalExpected) * 1000) / 10 : 0;

  return {
    period,
    collectedFcfa,
    collectedTrendLabel:
      validated.length > 1 ? `${validated.length} reçus` : `${validated.length} reçu`,
    collectedComparisonLabel:
      period === 'today'
        ? 'caisse du jour'
        : period === 'month'
          ? 'période mensuelle'
          : 'cumul trimestriel',
    overdueFcfa,
    overdueCount: overdue.length,
    overdueSubLabel: overdue.length > 1 ? 'factures échues' : 'facture échue',
    collectionRatePercent,
    collectionRateTrendLabel: collectionRatePercent >= 80 ? 'Objectif tenu' : 'Suivi actif',
    collectionRateComparisonLabel: 'taux de recouvrement',
    pendingFcfa,
    pendingCount: pending.length,
    pendingSubLabel:
      pending.length > 0 ? 'validation mobile money' : 'aucune attente',
  };
}
