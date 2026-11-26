import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/interceptors/problem-detail.interceptor';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendPageResponse,
  FinancialReportViewDto,
  InvoiceResponseDto,
  PaymentResponseDto,
  ReceiptVerificationResponseDto,
} from '../models/finances.model';
import { FinancesApiService } from './finances-api.service';
import { FINANCES_DEMO_FALLBACK } from './finances-demo-fallback.token';
import { FinancesDevFallbackService } from './finances-dev-fallback.service';

const SAMPLE_INVOICE_DTO: InvoiceResponseDto = {
  id: '22222222-2222-4222-8222-222222222222',
  invoiceNumber: 'FAC-2026-0142',
  studentId: '11111111-1111-4111-8111-111111111111',
  academicYearId: '33333333-3333-4333-8333-333333333333',
  enrollmentId: null,
  payerGuardianId: null,
  familyId: null,
  title: 'Aminata Diallo',
  currency: 'XOF',
  grossAmountXof: 120000,
  discountAmountXof: 0,
  netAmountXof: 120000,
  paidAmountXof: 85000,
  balanceXof: 35000,
  status: 'PARTIALLY_PAID',
  issueDate: '2026-11-01',
  dueDate: '2026-11-30',
  issuedAt: '2026-11-01T09:00:00Z',
  issuedBy: null,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  notes: null,
  createdAt: '2026-11-01T09:00:00Z',
  updatedAt: '2026-11-26T10:42:00Z',
};

const SAMPLE_PAYMENT_DTO: PaymentResponseDto = {
  id: '44444444-4444-4444-8444-444444444444',
  invoiceId: SAMPLE_INVOICE_DTO.id,
  studentId: SAMPLE_INVOICE_DTO.studentId,
  payerGuardianId: null,
  requestedScheduleId: null,
  amountXof: 85000,
  currency: 'XOF',
  paymentMethod: 'MOBILE_MONEY_WAVE',
  externalReference: 'WV-2611-88412',
  confirmationSource: 'MANUAL_DESK',
  idempotencyKey: 'pay-idem-001',
  paidAt: '2026-11-26T10:42:00Z',
  recordedBy: '55555555-5555-4555-8555-555555555555',
  notes: 'Mensualité novembre',
  reversed: false,
  reversedAt: null,
  receipt: {
    id: '66666666-6666-4666-8666-666666666666',
    paymentId: '44444444-4444-4444-8444-444444444444',
    invoiceId: SAMPLE_INVOICE_DTO.id,
    studentId: SAMPLE_INVOICE_DTO.studentId,
    receiptNumber: 'REC-2026-1089',
    verificationToken: 'tok-verify-1089',
    amountXof: 85000,
    currency: 'XOF',
    paymentMethod: 'MOBILE_MONEY_WAVE',
    issuedAt: '2026-11-26T10:42:00Z',
    issuedBy: '55555555-5555-4555-8555-555555555555',
  },
  allocations: [],
};

const SAMPLE_REPORT_DTO: FinancialReportViewDto = {
  schoolCode: 'LYC-DKR',
  schoolName: 'Lycée Démo',
  currency: 'XOF',
  totalInvoiceCount: 40,
  issuedInvoiceCount: 10,
  partiallyPaidInvoiceCount: 8,
  paidInvoiceCount: 22,
  cancelledInvoiceCount: 0,
  totalNetXof: 8000000,
  totalInvoicePaidXof: 6450000,
  totalOutstandingXof: 1550000,
  collectionRatePercent: 80.6,
  overdueInstallmentCount: 7,
  overdueAmountXof: 640000,
  collectedPaymentsCount: 54,
  collectedPaymentsXof: 6500000,
  reversalsCount: 1,
  reversalsXof: 50000,
  netCollectedXof: 6450000,
  generatedAt: '2026-11-26T11:00:00Z',
};

describe('FinancesApiService', () => {
  describe('production mode (no dev fallback provider)', () => {
    let service: FinancesApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
        ],
      });
      service = TestBed.inject(FinancesApiService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('requests GET /api/v1/finance/payments, invoices, and reporting/financial with exact backend params and zero tenantId', async () => {
      const promise = firstValueFrom(
        service.list({
          page: 1,
          size: 8,
          paymentMethod: 'MOBILE_MONEY_WAVE',
          period: 'month',
          sortBy: 'paidAt',
          direction: 'DESC',
        })
      );

      const paymentsReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/payments');
      expect(paymentsReq.request.method).toBe('GET');
      expect(paymentsReq.request.params.get('page')).toBe('1');
      expect(paymentsReq.request.params.get('size')).toBe('8');
      expect(paymentsReq.request.params.get('sortBy')).toBe('paidAt');
      expect(paymentsReq.request.params.get('direction')).toBe('DESC');
      expect(paymentsReq.request.params.get('paymentMethod')).toBe('MOBILE_MONEY_WAVE');
      expect(paymentsReq.request.params.has('tenantId')).toBe(false);
      expect(paymentsReq.request.headers.has('X-Tenant-Id')).toBe(false);

      const invoicesReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/invoices');
      expect(invoicesReq.request.method).toBe('GET');
      expect(invoicesReq.request.params.has('tenantId')).toBe(false);

      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/financial');
      expect(reportReq.request.method).toBe('GET');
      expect(reportReq.request.params.get('fromDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(reportReq.request.params.get('toDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(reportReq.request.params.has('tenantId')).toBe(false);

      const paymentsPage: BackendPageResponse<PaymentResponseDto> = {
        content: [SAMPLE_PAYMENT_DTO],
        page: 1,
        size: 8,
        totalElements: 9,
        totalPages: 2,
        first: false,
        last: true,
      };
      const invoicesPage: BackendPageResponse<InvoiceResponseDto> = {
        content: [SAMPLE_INVOICE_DTO],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      };

      paymentsReq.flush(paymentsPage);
      invoicesReq.flush(invoicesPage);
      reportReq.flush(SAMPLE_REPORT_DTO);

      const result = await promise;
      expect(result.page).toBe(1);
      expect(result.totalElements).toBe(9);
      expect(result.totalPages).toBe(2);
      expect(result.content.length).toBe(1);
      expect(result.content[0]?.receiptNumber).toBe('REC-2026-1089');
      expect(result.content[0]?.invoiceNumber).toBe('FAC-2026-0142');
      expect(result.content[0]?.amountFcfa).toBe(85000);
      expect(result.content[0]?.method).toBe('MOBILE_MONEY_WAVE');
      expect(result.kpis.collectedFcfa).toBe(6450000);
      expect(result.kpis.overdueFcfa).toBe(640000);
      expect(result.kpis.collectionRatePercent).toBe(80.6);
      expect(result.payableInvoices.length).toBe(1);
      expect(result.payableInvoices[0]?.balanceFcfa).toBe(35000);
    });

    it('posts strict RecordPaymentRequest payload with Idempotency-Key header to POST /api/v1/finance/payments', async () => {
      const promise = firstValueFrom(
        service.recordPayment({
          invoiceId: SAMPLE_INVOICE_DTO.id,
          amountFcfa: 50000,
          paymentMethod: 'MOBILE_MONEY_OM',
          externalReference: 'OM-2611-77812',
          notes: 'Acompte scolarité',
          idempotencyKey: 'pay-idem-fixed-01',
        })
      );

      const req = httpMock.expectOne('/api/v1/finance/payments');
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Idempotency-Key')).toBe('pay-idem-fixed-01');
      expect(req.request.headers.has('X-Tenant-Id')).toBe(false);
      expect(req.request.body).toEqual({
        invoiceId: SAMPLE_INVOICE_DTO.id,
        amountXof: 50000,
        paymentMethod: 'MOBILE_MONEY_OM',
        externalReference: 'OM-2611-77812',
        idempotencyKey: 'pay-idem-fixed-01',
        notes: 'Acompte scolarité',
      });

      req.flush(SAMPLE_PAYMENT_DTO, { status: 201, statusText: 'Created' });

      const created = await promise;
      expect(created.receiptNumber).toBe('REC-2026-1089');
      expect(created.status).toBe('VALIDATED');
    });

    it('verifies receipts via GET /api/v1/finance/receipts/verify/{token}', async () => {
      const promise = firstValueFrom(service.verifyReceipt('tok-verify-1089'));

      const req = httpMock.expectOne('/api/v1/finance/receipts/verify/tok-verify-1089');
      expect(req.request.method).toBe('GET');

      const verificationDto: ReceiptVerificationResponseDto = {
        receiptId: '66666666-6666-4666-8666-666666666666',
        receiptNumber: 'REC-2026-1089',
        amountXof: 85000,
        currency: 'XOF',
        paymentMethod: 'MOBILE_MONEY_WAVE',
        issuedAt: '2026-11-26T10:42:00Z',
        invoiceNumber: 'FAC-2026-0142',
        studentMatricule: '2026-0001',
        reversed: false,
        reversedAt: null,
      };
      req.flush(verificationDto);

      const result = await promise;
      expect(result.receiptNumber).toBe('REC-2026-1089');
      expect(result.amountFcfa).toBe(85000);
      expect(result.reversed).toBe(false);
    });

    it('propagates RFC 9457 ProblemDetailError when backend returns an error in production', async () => {
      const promise = firstValueFrom(service.list({ page: 0, size: 8, period: 'month' }));

      const paymentsReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/payments');
      const invoicesReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/invoices');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/financial');

      paymentsReq.flush(
        {
          type: 'urn:ecole:problem:service-unavailable',
          title: 'Service caisse indisponible',
          status: 503,
          detail: 'Le module finances est temporairement indisponible.',
          requestId: 'req-fin-prod-503',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );
      if (!invoicesReq.cancelled) {
        invoicesReq.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, first: true, last: true });
      }
      if (!reportReq.cancelled) {
        reportReq.flush(SAMPLE_REPORT_DTO);
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
      try {
        await promise;
      } catch (err) {
        const problem = (err as ProblemDetailError).problem;
        expect(problem.status).toBe(503);
        expect(problem.requestId).toBe('req-fin-prod-503');
      }
    });
  });

  describe('development fallback guard', () => {
    let service: FinancesApiService;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([problemDetailInterceptor])),
          provideHttpClientTesting(),
          { provide: FINANCES_DEMO_FALLBACK, useExisting: FinancesDevFallbackService },
        ],
      });
      service = TestBed.inject(FinancesApiService);
      httpMock = TestBed.inject(HttpTestingController);
      authService = TestBed.inject(AuthService);
    });

    afterEach(() => {
      authService.clearSession();
      httpMock.verify();
    });

    it('falls back to demo dataset ONLY when dev demo session is active and backend is unreachable', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Direction Démo',
        email: 'direction@ecole-demo.invalid',
        role: 'DIRECTOR',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const listPromise = firstValueFrom(service.list({ page: 0, size: 8, period: 'month' }));
      const paymentsReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/payments');
      const invoicesReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/invoices');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/financial');

      paymentsReq.error(new ProgressEvent('error'));
      if (!invoicesReq.cancelled) {
        invoicesReq.error(new ProgressEvent('error'));
      }
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
      }

      const result = await listPromise;
      expect(result.totalElements).toBeGreaterThan(0);
      expect(result.kpis.collectedFcfa).toBeGreaterThan(0);
      expect(result.payableInvoices.length).toBeGreaterThan(0);

      // Recording a payment in dev fallback updates the in-memory ledger
      const recordPromise = firstValueFrom(
        service.recordPayment({
          invoiceId: result.payableInvoices[0].id,
          amountFcfa: 25000,
          paymentMethod: 'CASH',
          notes: 'Test espèces démo',
        })
      );
      const postReq = httpMock.expectOne('/api/v1/finance/payments');
      postReq.flush('Not Found', { status: 404, statusText: 'Not Found' });

      const created = await recordPromise;
      expect(created.amountFcfa).toBe(25000);
      expect(created.receiptNumber).toMatch(/^REC-2026-/);
    });

    it('does NOT fall back when authenticated with a real user token', async () => {
      authService.setSession('real-jwt-access-token', {
        id: 'usr-real',
        fullName: 'Caissier Réel',
        email: 'caisse@lycee.sn',
        role: 'CASHIER',
        tenantId: 'tenant-01',
        tenantName: 'Lycée Test',
      });

      const promise = firstValueFrom(service.list({ page: 0, size: 8, period: 'month' }));
      const paymentsReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/payments');
      const invoicesReq = httpMock.expectOne((req) => req.url === '/api/v1/finance/invoices');
      const reportReq = httpMock.expectOne((req) => req.url === '/api/v1/reporting/financial');

      paymentsReq.error(new ProgressEvent('error'));
      if (!invoicesReq.cancelled) {
        invoicesReq.error(new ProgressEvent('error'));
      }
      if (!reportReq.cancelled) {
        reportReq.error(new ProgressEvent('error'));
      }

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });

    it('does NOT swallow structured RFC 9457 business errors even in dev demo session', async () => {
      authService.setSession('demo-memory-token', {
        id: 'usr-demo',
        fullName: 'Caisse Démo',
        email: 'caisse@ecole-demo.invalid',
        role: 'CASHIER',
        tenantId: 'tenant-demo-01',
        tenantName: 'Groupe Scolaire Démo',
      });

      const promise = firstValueFrom(
        service.recordPayment({
          invoiceId: SAMPLE_INVOICE_DTO.id,
          amountFcfa: 50000,
          paymentMethod: 'MOBILE_MONEY_WAVE',
          externalReference: 'WV-DUP-001',
        })
      );

      const req = httpMock.expectOne('/api/v1/finance/payments');
      req.flush(
        {
          type: 'urn:ecole:problem:payment-reference-duplicate',
          title: 'Référence déjà enregistrée',
          status: 409,
          detail: 'Un encaissement avec cette référence externe existe déjà.',
          requestId: 'req-fin-dup-409',
          errors: [{ field: 'externalReference', message: 'Référence en doublon.' }],
        },
        { status: 409, statusText: 'Conflict' }
      );

      await expect(promise).rejects.toBeInstanceOf(ProblemDetailError);
    });
  });
});
