import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, of, switchMap, throwError, timer } from 'rxjs';
import { MOCK_API_OPTIONS, MockApiOptions, MockScenario } from '../../../core/api/mock-api.util';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  DashboardApi,
  DashboardPaymentRecord,
  DashboardPeriod,
  DashboardSnapshot,
  MonthlyCollectionPoint,
  PriorityAlert,
} from '../models/dashboard.model';

const MONTHLY_EVOLUTION: readonly MonthlyCollectionPoint[] = [
  { monthKey: '2026-06', monthLabel: 'Juin', targetFcfa: 4600000, collectedFcfa: 4900000 },
  { monthKey: '2026-07', monthLabel: 'Juil', targetFcfa: 5100000, collectedFcfa: 5300000 },
  { monthKey: '2026-08', monthLabel: 'Août', targetFcfa: 3000000, collectedFcfa: 3150000 },
  {
    monthKey: '2026-09',
    monthLabel: 'Sept*',
    targetFcfa: 7050000,
    collectedFcfa: 7800000,
    isPeak: true,
  },
  { monthKey: '2026-10', monthLabel: 'Oct', targetFcfa: 5900000, collectedFcfa: 6050000 },
  { monthKey: '2026-11', monthLabel: 'Nov', targetFcfa: 6150000, collectedFcfa: 6300000 },
];

const PRIORITY_ALERTS: readonly PriorityAlert[] = [
  {
    id: 'alert-overdue',
    title: '19 factures échues > 15j',
    subtitle: 'Relances automatiques SMS prêtes à être envoyées',
    tone: 'warning',
    route: '/finance',
    actionLabel: 'Voir',
  },
  {
    id: 'alert-attendance',
    title: '2 classes sans appel saisi',
    subtitle: '3ème B et Terminale S2 en attente de validation',
    tone: 'info',
    route: '/attendance',
    actionLabel: 'Voir',
  },
  {
    id: 'alert-import',
    title: 'Import CSV terminé',
    subtitle: '3 matricules en doublon détectés à vérifier',
    tone: 'success',
    route: '/import',
    actionLabel: 'Voir',
  },
];

/**
 * Purely fictional payments dataset for frontend development and unit tests.
 */
const FICTIONAL_PAYMENTS: readonly DashboardPaymentRecord[] = [
  {
    id: 'pay-demo-01',
    receiptRef: 'REC-2026-1089',
    studentName: 'Aminata Diallo-Démo',
    studentInitials: 'AD',
    matricule: 'DEMO-2026-089',
    className: 'Terminale S2',
    recordedAtUtc: '2026-11-25T10:42:00Z',
    method: 'WAVE',
    status: 'VALIDATED',
    amountFcfa: 85000,
  },
  {
    id: 'pay-demo-02',
    receiptRef: 'REC-2026-1112',
    studentName: 'Cheikh Tidiane Ndiaye-Démo',
    studentInitials: 'CN',
    matricule: 'DEMO-2026-112',
    className: '3ème A',
    recordedAtUtc: '2026-11-25T09:15:00Z',
    method: 'ORANGE_MONEY',
    status: 'VALIDATED',
    amountFcfa: 50000,
  },
  {
    id: 'pay-demo-03',
    receiptRef: 'REC-2026-1045',
    studentName: 'Fatou Bintou Sow-Démo',
    studentInitials: 'FS',
    matricule: 'DEMO-2026-045',
    className: '6ème B',
    recordedAtUtc: '2026-11-24T16:30:00Z',
    method: 'CASH',
    status: 'VALIDATED',
    amountFcfa: 45000,
  },
  {
    id: 'pay-demo-04',
    receiptRef: 'REC-2026-1210',
    studentName: 'Mouhamadou Fall-Démo',
    studentInitials: 'MF',
    matricule: 'DEMO-2026-210',
    className: '1ère L',
    recordedAtUtc: '2026-11-24T14:05:00Z',
    method: 'WAVE',
    status: 'PENDING',
    amountFcfa: 60000,
  },
  {
    id: 'pay-demo-05',
    receiptRef: 'REC-2026-1004',
    studentName: 'Aïssatou Ba-Démo',
    studentInitials: 'AB',
    matricule: 'DEMO-2026-004',
    className: 'CM2 (Primaire)',
    recordedAtUtc: '2026-11-24T11:20:00Z',
    method: 'ORANGE_MONEY',
    status: 'VALIDATED',
    amountFcfa: 40000,
  },
];

const SNAPSHOTS_BY_PERIOD: Readonly<Record<DashboardPeriod, DashboardSnapshot>> = {
  today: {
    period: 'today',
    generatedAtUtc: '2026-11-25T11:30:00Z',
    kpis: {
      enrolledStudents: 482,
      enrolledDeltaLabel: '+2 aujourd’hui',
      enrolledComparisonLabel: 'vs hier (480)',
      collectedFcfa: 425000,
      collectedTrendLabel: '+12,4 %',
      collectedComparisonLabel: 'vs moyenne journalière',
      overdueFcfa: 1280000,
      overdueFamiliesCount: 19,
      overdueSubLabel: 'relances en cours',
      attendanceRatePercent: 95.2,
      attendanceTrendLabel: '+0,6 pt',
      absencesTodayCount: 23,
    },
    monthlyEvolution: MONTHLY_EVOLUTION,
    evolutionNote: 'Pic de rentrée constaté en septembre (+114 % vs moyenne annuelle)',
    totalCumulativeFcfa: 33500000,
    priorityAlerts: PRIORITY_ALERTS,
    recentPayments: FICTIONAL_PAYMENTS.slice(0, 3),
    totalTransactionsCount: 18,
  },
  month: {
    period: 'month',
    generatedAtUtc: '2026-11-25T11:30:00Z',
    kpis: {
      enrolledStudents: 482,
      enrolledDeltaLabel: '+14 ce mois',
      enrolledComparisonLabel: 'vs oct. (468)',
      collectedFcfa: 6450000,
      collectedTrendLabel: '+8,2 %',
      collectedComparisonLabel: 'vs objectif fixé',
      overdueFcfa: 1280000,
      overdueFamiliesCount: 19,
      overdueSubLabel: 'relances en cours',
      attendanceRatePercent: 94.6,
      attendanceTrendLabel: 'Stable',
      absencesTodayCount: 42,
    },
    monthlyEvolution: MONTHLY_EVOLUTION,
    evolutionNote: 'Pic de rentrée constaté en septembre (+114 % vs moyenne annuelle)',
    totalCumulativeFcfa: 33500000,
    priorityAlerts: PRIORITY_ALERTS,
    recentPayments: FICTIONAL_PAYMENTS,
    totalTransactionsCount: 142,
  },
  term: {
    period: 'term',
    generatedAtUtc: '2026-11-25T11:30:00Z',
    kpis: {
      enrolledStudents: 482,
      enrolledDeltaLabel: '+38 ce trimestre',
      enrolledComparisonLabel: 'vs T3 préc. (444)',
      collectedFcfa: 20150000,
      collectedTrendLabel: '+5,9 %',
      collectedComparisonLabel: 'vs objectif trimestriel',
      overdueFcfa: 1280000,
      overdueFamiliesCount: 19,
      overdueSubLabel: 'relances en cours',
      attendanceRatePercent: 94.1,
      attendanceTrendLabel: 'Stable',
      absencesTodayCount: 42,
    },
    monthlyEvolution: MONTHLY_EVOLUTION,
    evolutionNote: 'Progression régulière sur le premier trimestre (96 % de recouvrement cible)',
    totalCumulativeFcfa: 33500000,
    priorityAlerts: PRIORITY_ALERTS,
    recentPayments: FICTIONAL_PAYMENTS,
    totalTransactionsCount: 418,
  },
};

function createEmptySnapshot(period: DashboardPeriod): DashboardSnapshot {
  return {
    period,
    generatedAtUtc: '2026-11-25T11:30:00Z',
    kpis: {
      enrolledStudents: 0,
      enrolledDeltaLabel: '0 inscrit',
      enrolledComparisonLabel: 'nouvel établissement',
      collectedFcfa: 0,
      collectedTrendLabel: '0 %',
      collectedComparisonLabel: 'aucun encaissement',
      overdueFcfa: 0,
      overdueFamiliesCount: 0,
      overdueSubLabel: 'aucun impayé',
      attendanceRatePercent: 0,
      attendanceTrendLabel: '—',
      absencesTodayCount: 0,
    },
    monthlyEvolution: [],
    evolutionNote: '',
    totalCumulativeFcfa: 0,
    priorityAlerts: [],
    recentPayments: [],
    totalTransactionsCount: 0,
  };
}

@Injectable({ providedIn: 'root' })
export class DashboardMockApi implements DashboardApi {
  private readonly defaultOptions = inject(MOCK_API_OPTIONS);
  private options: MockApiOptions = { ...this.defaultOptions };

  configureMock(partial: Partial<MockApiOptions>): void {
    this.options = { ...this.options, ...partial };
  }

  setScenario(scenario: MockScenario): void {
    this.options = { ...this.options, scenario };
  }

  getSnapshot(period: DashboardPeriod): Observable<DashboardSnapshot> {
    const trigger$ = this.options.delayMs > 0 ? timer(this.options.delayMs) : of(0);

    return trigger$.pipe(
      switchMap(() => {
        if (this.options.scenario === 'error') {
          return throwError(
            () =>
              new ProblemDetailError({
                type: 'urn:ecole:problem:dashboard-unavailable',
                title: 'Tableau de bord indisponible',
                status: 503,
                detail:
                  'Impossible de récupérer les indicateurs de pilotage pour le moment. Veuillez réessayer.',
                instance: `/api/v1/dashboard?period=${period}`,
                requestId: 'req-dash-mock-503',
              })
          );
        }

        if (this.options.scenario === 'empty') {
          return of(createEmptySnapshot(period));
        }

        return of(SNAPSHOTS_BY_PERIOD[period]);
      })
    );
  }
}

export const DASHBOARD_API = new InjectionToken<DashboardApi>('DASHBOARD_API', {
  providedIn: 'root',
  factory: () => inject(DashboardMockApi),
});
