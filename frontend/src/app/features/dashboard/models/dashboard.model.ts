import { Observable } from 'rxjs';

export type DashboardPeriod = 'today' | 'month' | 'term';
export type DashboardPaymentMethod = 'WAVE' | 'ORANGE_MONEY' | 'CASH';
export type DashboardPaymentStatus = 'VALIDATED' | 'PENDING';
export type PriorityAlertTone = 'warning' | 'info' | 'success' | 'error';

export interface DashboardKpis {
  readonly enrolledStudents: number;
  readonly enrolledDeltaLabel: string;
  readonly enrolledComparisonLabel: string;
  readonly collectedFcfa: number;
  readonly collectedTrendLabel: string;
  readonly collectedComparisonLabel: string;
  readonly overdueFcfa: number;
  readonly overdueFamiliesCount: number;
  readonly overdueSubLabel: string;
  readonly attendanceRatePercent: number;
  readonly attendanceTrendLabel: string;
  readonly absencesTodayCount: number;
}

export interface MonthlyCollectionPoint {
  readonly monthKey: string;
  readonly monthLabel: string;
  readonly collectedFcfa: number;
  readonly targetFcfa: number;
  readonly isPeak?: boolean;
}

export interface PriorityAlert {
  readonly id: string;
  readonly title: string;
  readonly subtitle: string;
  readonly tone: PriorityAlertTone;
  readonly route: string;
  readonly actionLabel: string;
}

export interface DashboardPaymentRecord {
  readonly id: string;
  readonly receiptRef: string;
  readonly studentName: string;
  readonly studentInitials: string;
  readonly matricule: string;
  readonly className: string;
  readonly recordedAtUtc: string;
  readonly method: DashboardPaymentMethod;
  readonly status: DashboardPaymentStatus;
  readonly amountFcfa: number;
}

export interface DashboardSnapshot {
  readonly period: DashboardPeriod;
  readonly generatedAtUtc: string;
  readonly kpis: DashboardKpis;
  readonly monthlyEvolution: readonly MonthlyCollectionPoint[];
  readonly evolutionNote: string;
  readonly totalCumulativeFcfa: number;
  readonly priorityAlerts: readonly PriorityAlert[];
  readonly recentPayments: readonly DashboardPaymentRecord[];
  readonly totalTransactionsCount: number;
}

export interface QuickStudentDraft {
  readonly firstName: string;
  readonly lastName: string;
  readonly className: string;
  readonly guardianPhone: string;
}

export interface DashboardApi {
  getSnapshot(period: DashboardPeriod): Observable<DashboardSnapshot>;
}
