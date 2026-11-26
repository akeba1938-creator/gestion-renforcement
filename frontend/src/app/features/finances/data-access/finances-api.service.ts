import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendPageResponse,
  FinancePayableInvoice,
  FinanceTransactionRecord,
  FinancesListQuery,
  FinancesPageResult,
  FinancialReportViewDto,
  InvoiceResponseDto,
  PaymentResponseDto,
  ReceiptVerificationResponseDto,
  ReceiptVerificationResult,
  RecordPaymentInput,
  buildKpisFromFinancialReport,
  buildPeriodDateRange,
  computeFallbackKpisFromTransactions,
  generatePaymentIdempotencyKey,
  mapInvoiceDtoToPayable,
  mapPaymentDtoToTransaction,
  mapRecordPaymentInputToDto,
  mapVerificationDtoToResult,
} from '../models/finances.model';
import { FINANCES_DEMO_FALLBACK } from './finances-demo-fallback.token';

const BACKEND_UNAVAILABLE_STATUSES = new Set([0, 200, 404, 502, 503, 504]);

/**
 * Strictly typed HTTP client aligned with backend:
 * - `PaymentController` (`/api/v1/finance/payments`, `/api/v1/finance/receipts/verify/{token}`)
 * - `StudentInvoiceController` (`/api/v1/finance/invoices`)
 * - `ReportingController` (`/api/v1/reporting/financial`)
 *
 * Tenant isolation is enforced strictly server-side from the authenticated Bearer token;
 * no `tenantId` header or query param is ever sent by the client.
 *
 * In local development with an active demo login session and no backend running,
 * delegates transparently to the optional dev-only `FINANCES_DEMO_FALLBACK` provider
 * (which is stripped completely from production builds via `fileReplacements`).
 */
@Injectable({ providedIn: 'root' })
export class FinancesApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly demoFallback = inject(FINANCES_DEMO_FALLBACK, { optional: true });

  private get financeBaseUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/finance`;
  }

  private get reportingFinancialUrl(): string {
    return `${this.apiBaseUrl.replace(/\/+$/, '')}/reporting/financial`;
  }

  list(query: FinancesListQuery): Observable<FinancesPageResult> {
    let paymentParams = new HttpParams()
      .set('page', String(Math.max(0, Math.trunc(query.page))))
      .set('size', String(Math.max(1, Math.min(100, Math.trunc(query.size)))))
      .set('sortBy', query.sortBy ?? 'paidAt')
      .set('direction', query.direction ?? 'DESC');

    if (query.paymentMethod && query.paymentMethod !== 'ALL') {
      paymentParams = paymentParams.set('paymentMethod', query.paymentMethod);
    }

    const { fromDate, toDate } = buildPeriodDateRange(query.period);
    const reportParams = new HttpParams().set('fromDate', fromDate).set('toDate', toDate);

    const invoiceParams = new HttpParams()
      .set('page', '0')
      .set('size', '50')
      .set('sortBy', 'dueDate')
      .set('direction', 'ASC');

    const payments$ = this.http.get<BackendPageResponse<PaymentResponseDto>>(
      `${this.financeBaseUrl}/payments`,
      { params: paymentParams }
    );

    const invoices$ = this.http
      .get<BackendPageResponse<InvoiceResponseDto>>(`${this.financeBaseUrl}/invoices`, {
        params: invoiceParams,
      })
      .pipe(catchError(() => of<BackendPageResponse<InvoiceResponseDto> | null>(null)));

    const report$ = this.http
      .get<FinancialReportViewDto>(this.reportingFinancialUrl, { params: reportParams })
      .pipe(catchError(() => of<FinancialReportViewDto | null>(null)));

    return forkJoin({
      payments: payments$,
      invoices: invoices$,
      report: report$,
    }).pipe(
      map(({ payments, invoices, report }) => {
        const invoiceById = new Map<string, InvoiceResponseDto>();
        const payableInvoices: FinancePayableInvoice[] = [];

        if (invoices) {
          for (const inv of invoices.content) {
            invoiceById.set(inv.id, inv);
            if (
              inv.balanceXof > 0 &&
              (inv.status === 'ISSUED' || inv.status === 'PARTIALLY_PAID')
            ) {
              payableInvoices.push(mapInvoiceDtoToPayable(inv));
            }
          }
        }

        const mapped = payments.content.map((dto) => {
          const invoice = invoiceById.get(dto.invoiceId);
          return mapPaymentDtoToTransaction(dto, {
            invoiceNumber: invoice?.invoiceNumber,
            studentName: invoice?.title,
          });
        });

        const searchLower = query.search?.trim().toLowerCase() ?? '';
        const statusFilter = query.status ?? 'ALL';

        const filtered = mapped.filter((tx) => {
          if (statusFilter !== 'ALL' && tx.status !== statusFilter) {
            return false;
          }
          if (!searchLower) {
            return true;
          }
          return (
            tx.studentName.toLowerCase().includes(searchLower) ||
            tx.matricule.toLowerCase().includes(searchLower) ||
            tx.receiptNumber.toLowerCase().includes(searchLower) ||
            tx.invoiceNumber.toLowerCase().includes(searchLower) ||
            (tx.externalReference?.toLowerCase().includes(searchLower) ?? false)
          );
        });

        const pendingTransactions = mapped.filter((tx) => tx.status === 'PENDING');
        const kpis = report
          ? buildKpisFromFinancialReport(report, query.period, pendingTransactions)
          : computeFallbackKpisFromTransactions(mapped, query.period);

        const clientFiltered = Boolean(searchLower) || statusFilter !== 'ALL';

        return {
          content: filtered,
          page: payments.page,
          size: payments.size,
          totalElements: clientFiltered ? filtered.length : payments.totalElements,
          totalPages: clientFiltered
            ? filtered.length > 0
              ? 1
              : 0
            : payments.totalPages,
          first: payments.first,
          last: clientFiltered ? true : payments.last,
          kpis,
          payableInvoices,
        };
      }),
      catchError((error: unknown) => {
        if (this.shouldFallbackToDemo(error) && this.demoFallback) {
          return this.demoFallback.list(query);
        }
        return throwError(() => error);
      })
    );
  }

  recordPayment(input: RecordPaymentInput): Observable<FinanceTransactionRecord> {
    const idempotencyKey = input.idempotencyKey?.trim() || generatePaymentIdempotencyKey();
    const payload = mapRecordPaymentInputToDto(input, idempotencyKey);
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });

    return this.http
      .post<PaymentResponseDto>(`${this.financeBaseUrl}/payments`, payload, { headers })
      .pipe(
        map((dto) => mapPaymentDtoToTransaction(dto)),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.recordPayment(input);
          }
          return throwError(() => error);
        })
      );
  }

  verifyReceipt(verificationToken: string): Observable<ReceiptVerificationResult> {
    const encodedToken = encodeURIComponent(verificationToken.trim());
    return this.http
      .get<ReceiptVerificationResponseDto>(
        `${this.financeBaseUrl}/receipts/verify/${encodedToken}`
      )
      .pipe(
        map((dto) => mapVerificationDtoToResult(dto)),
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.verifyReceipt(verificationToken);
          }
          return throwError(() => error);
        })
      );
  }

  getPaymentById(paymentId: string): Observable<FinanceTransactionRecord> {
    const encodedId = encodeURIComponent(paymentId.trim());
    return this.http
      .get<PaymentResponseDto>(`${this.financeBaseUrl}/payments/${encodedId}`)
      .pipe(map((dto) => mapPaymentDtoToTransaction(dto)));
  }

  confirmPendingTransaction(transaction: FinanceTransactionRecord): Observable<FinanceTransactionRecord> {
    if (transaction.paymentId) {
      return this.getPaymentById(transaction.paymentId).pipe(
        catchError((error: unknown) => {
          if (this.shouldFallbackToDemo(error) && this.demoFallback) {
            return this.demoFallback.confirmPendingTransaction(transaction.id);
          }
          return throwError(() => error);
        })
      );
    }

    if (this.demoFallback?.isDemoSessionActive()) {
      return this.demoFallback.confirmPendingTransaction(transaction.id);
    }

    return of(transaction);
  }

  private shouldFallbackToDemo(error: unknown): boolean {
    if (!this.demoFallback?.isDemoSessionActive()) {
      return false;
    }

    if (error instanceof ProblemDetailError) {
      if (error.problem.requestId) {
        return false;
      }
      return BACKEND_UNAVAILABLE_STATUSES.has(error.problem.status);
    }

    if (error instanceof HttpErrorResponse) {
      if (error.headers?.get('X-Request-Id')) {
        return false;
      }
      return BACKEND_UNAVAILABLE_STATUSES.has(error.status);
    }

    return false;
  }
}
