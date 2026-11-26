import { BadgeTone } from '../../../shared/components/status-badge/status-badge';

export type SettingsTab = 'school' | 'academic' | 'security' | 'preferences';

export const SETTINGS_TABS: readonly SettingsTab[] = [
  'school',
  'academic',
  'security',
  'preferences',
];

export function isValidSettingsTab(value: string | null | undefined): value is SettingsTab {
  if (!value) {
    return false;
  }
  return (SETTINGS_TABS as readonly string[]).includes(value);
}

export type GradingPeriodSystem = 'TRIMESTER' | 'SEMESTER' | 'CUSTOM';

export type AcademicYearStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED';

export type AcademicYearStatusFilter = 'ALL' | AcademicYearStatus;

export type EvaluationPeriodStatus = 'PLANNED' | 'OPEN' | 'CLOSED' | 'LOCKED';

export type CommunicationChannel = 'IN_APP' | 'SMS' | 'WHATSAPP' | 'EMAIL';

export const E164_PHONE_PATTERN = /^\+[1-9]\d{6,14}$/;

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const TOTP_CODE_PATTERN = /^\d{6}$/;

/**
 * Matches backend `com.ecole.saas.academic.api.PageResponse`.
 */
export interface BackendPageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

/**
 * Matches backend `com.ecole.saas.school.api.SchoolResponse`.
 */
export interface SchoolResponseDto {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly shortName: string | null;
  readonly schoolType: string;
  readonly status: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly address: string | null;
  readonly city: string | null;
  readonly region: string | null;
  readonly countryCode: string | null;
  readonly motto: string | null;
  readonly ministryCode: string | null;
  readonly website: string | null;
  readonly timezone: string | null;
  readonly currency: string | null;
  readonly gradingPeriodSystem: GradingPeriodSystem;
  readonly gradingScale: number;
  readonly statusReason: string | null;
  readonly suspendedAt: string | null;
  readonly archivedAt: string | null;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}

/**
 * Matches backend `com.ecole.saas.school.api.UpdateTenantSchoolRequest`.
 * Note: tenant is resolved exclusively from JWT server-side (never sent by client).
 */
export interface UpdateTenantSchoolRequestDto {
  readonly name: string;
  readonly shortName?: string | null;
  readonly phone?: string | null;
  readonly email?: string | null;
  readonly address?: string | null;
  readonly city?: string | null;
  readonly region?: string | null;
  readonly countryCode?: string | null;
  readonly motto?: string | null;
  readonly ministryCode?: string | null;
  readonly website?: string | null;
  readonly timezone?: string | null;
  readonly gradingPeriodSystem?: GradingPeriodSystem | null;
  readonly gradingScale?: number | null;
}

/**
 * Matches backend `com.ecole.saas.academic.api.AcademicYearResponse`.
 */
export interface AcademicYearResponseDto {
  readonly id: string;
  readonly code: string;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly periodSystem: GradingPeriodSystem;
  readonly status: AcademicYearStatus;
  readonly current: boolean;
  readonly closedAt: string | null;
  readonly archivedAt: string | null;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}

/**
 * Matches backend `com.ecole.saas.academic.api.UpdateAcademicYearRequest`.
 */
export interface UpdateAcademicYearRequestDto {
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly periodSystem?: GradingPeriodSystem;
}

/**
 * Matches backend `com.ecole.saas.academic.api.ActivateAcademicYearRequest`.
 */
export interface ActivateAcademicYearRequestDto {
  readonly makeCurrent: boolean;
}

/**
 * Matches backend `com.ecole.saas.academic.api.EvaluationPeriodResponse`.
 */
export interface EvaluationPeriodResponseDto {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly periodType: string;
  readonly sequenceOrder: number;
  readonly startDate: string;
  readonly endDate: string;
  readonly weight: number;
  readonly status: EvaluationPeriodStatus;
  readonly lockedAt: string | null;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}

/**
 * Matches backend `com.ecole.saas.communication.api.ContactPreferenceResponse`.
 */
export interface ContactPreferenceResponseDto {
  readonly id: string | null;
  readonly contactType: string;
  readonly contactId: string;
  readonly inAppEnabled: boolean;
  readonly smsEnabled: boolean;
  readonly whatsappEnabled: boolean;
  readonly emailEnabled: boolean;
  readonly marketingOptIn: boolean;
  readonly preferredChannel: CommunicationChannel;
  readonly preferredLanguage: string | null;
  readonly quietHoursStart: string | null;
  readonly quietHoursEnd: string | null;
  readonly consentSource: string | null;
  readonly consentRecordedAt: string | null;
  readonly customized: boolean;
}

/**
 * Matches backend `com.ecole.saas.communication.api.UpdateMyPreferenceRequest`.
 */
export interface UpdateMyPreferenceRequestDto {
  readonly inAppEnabled: boolean;
  readonly smsEnabled: boolean;
  readonly whatsappEnabled: boolean;
  readonly emailEnabled: boolean;
  readonly marketingOptIn: boolean;
  readonly preferredChannel: CommunicationChannel;
  readonly preferredLanguage: string;
  readonly quietHoursStart: string | null;
  readonly quietHoursEnd: string | null;
}

export interface SchoolSettings {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly shortName: string;
  readonly address: string;
  readonly city: string;
  readonly region: string;
  readonly countryCode: string;
  readonly phone: string;
  readonly email: string;
  readonly motto: string;
  readonly ministryCode: string;
  readonly website: string;
  readonly currency: string;
  readonly timezone: string;
  readonly gradingPeriodSystem: GradingPeriodSystem;
  readonly gradingScale: number;
  readonly updatedAtUtc: string | null;
}

export interface AcademicYearItem {
  readonly id: string;
  readonly code: string;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly periodSystem: GradingPeriodSystem;
  readonly status: AcademicYearStatus;
  readonly current: boolean;
}

export interface EvaluationPeriodItem {
  readonly id: string;
  readonly academicYearId: string;
  readonly code: string;
  readonly name: string;
  readonly periodType: string;
  readonly sequenceOrder: number;
  readonly startDate: string;
  readonly endDate: string;
  readonly weight: number;
  readonly status: EvaluationPeriodStatus;
}

export interface SecurityPolicySnapshot {
  readonly accountId: string;
  readonly fullName: string;
  readonly email: string;
  readonly roles: readonly string[];
  readonly mfaEnabled: boolean;
  readonly mfaRequiredByPolicy: boolean;
  readonly accessTokenMinutes: number;
  readonly refreshSessionDays: number;
  readonly mfaChallengeMinutes: number;
  readonly maxLoginFailures: number;
  readonly lockoutMinutes: number;
}

export interface NotificationPreferences {
  readonly receiptNotificationsEnabled: boolean;
  readonly absenceNotificationsEnabled: boolean;
  readonly inAppEnabled: boolean;
  readonly emailEnabled: boolean;
  readonly smsEnabled: boolean;
  readonly whatsappEnabled: boolean;
  readonly preferredChannel: CommunicationChannel;
  readonly preferredLanguage: string;
  readonly quietHoursStart: string;
  readonly quietHoursEnd: string;
}

export interface SettingsSnapshot {
  readonly school: SchoolSettings | null;
  readonly academicYears: readonly AcademicYearItem[];
  readonly periodsByYearId: Readonly<Record<string, readonly EvaluationPeriodItem[]>>;
  readonly security: SecurityPolicySnapshot;
  readonly preferences: NotificationPreferences;
}

export interface UpdateSchoolSettingsInput {
  readonly name: string;
  readonly address: string;
  readonly city: string;
  readonly region: string;
  readonly phone: string;
  readonly email: string;
  readonly currentSchool: SchoolSettings;
}

export interface UpdateAcademicSettingsInput {
  readonly academicYearId: string;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly periodSystem: GradingPeriodSystem;
  readonly setAsCurrent: boolean;
  readonly shouldActivateYear: boolean;
  readonly activePeriodId: string | null;
  readonly shouldOpenPeriod: boolean;
}

export interface UpdateAcademicSettingsResult {
  readonly year: AcademicYearItem;
  readonly openedPeriod: EvaluationPeriodItem | null;
}

export interface MfaEnrollmentPayload {
  readonly secret: string;
  readonly otpauthUri: string;
  readonly digits: number;
  readonly periodSeconds: number;
}

export interface MfaVerifyResult {
  readonly mfaEnabled: boolean;
}

export const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreferences = {
  receiptNotificationsEnabled: true,
  absenceNotificationsEnabled: true,
  inAppEnabled: true,
  emailEnabled: true,
  smsEnabled: true,
  whatsappEnabled: false,
  preferredChannel: 'IN_APP',
  preferredLanguage: 'fr',
  quietHoursStart: '21:00',
  quietHoursEnd: '07:00',
};

export const SENSITIVE_MFA_ROLES: ReadonlySet<string> = new Set([
  'PROMOTER',
  'DIRECTOR',
  'CENTER_MANAGER',
  'SAAS_ADMIN',
  'ADMIN_SAAS',
]);

export function mapSchoolDtoToSettings(dto: SchoolResponseDto): SchoolSettings {
  return {
    id: dto.id,
    code: dto.code?.trim() || '—',
    name: dto.name?.trim() || '',
    shortName: dto.shortName?.trim() || '',
    address: dto.address?.trim() || '',
    city: dto.city?.trim() || '',
    region: dto.region?.trim() || '',
    countryCode: dto.countryCode?.trim() || 'SN',
    phone: dto.phone?.trim() || '',
    email: dto.email?.trim() || '',
    motto: dto.motto?.trim() || '',
    ministryCode: dto.ministryCode?.trim() || '',
    website: dto.website?.trim() || '',
    currency: formatCurrencyDisplay(dto.currency),
    timezone: dto.timezone?.trim() || 'Africa/Dakar',
    gradingPeriodSystem: dto.gradingPeriodSystem ?? 'TRIMESTER',
    gradingScale: dto.gradingScale || 20,
    updatedAtUtc: dto.updatedAt ?? null,
  };
}

export function mapAcademicYearDtoToItem(dto: AcademicYearResponseDto): AcademicYearItem {
  return {
    id: dto.id,
    code: dto.code,
    label: dto.label,
    startDate: dto.startDate,
    endDate: dto.endDate,
    periodSystem: dto.periodSystem,
    status: dto.status,
    current: dto.current,
  };
}

export function mapEvaluationPeriodDtoToItem(
  dto: EvaluationPeriodResponseDto
): EvaluationPeriodItem {
  return {
    id: dto.id,
    academicYearId: dto.academicYearId,
    code: dto.code,
    name: dto.name,
    periodType: dto.periodType,
    sequenceOrder: dto.sequenceOrder,
    startDate: dto.startDate,
    endDate: dto.endDate,
    weight: dto.weight,
    status: dto.status,
  };
}

export function formatCurrencyDisplay(rawCurrency: string | null | undefined): string {
  const normalized = rawCurrency?.trim().toUpperCase();
  if (!normalized || normalized === 'XOF' || normalized === 'FCFA') {
    return 'FCFA';
  }
  return normalized;
}

export function formatAcademicYearStatusLabel(
  status: AcademicYearStatus,
  isCurrent: boolean
): string {
  if (isCurrent || status === 'ACTIVE') {
    return 'Active';
  }
  switch (status) {
    case 'DRAFT':
      return 'Brouillon';
    case 'CLOSED':
      return 'Clôturée';
    case 'ARCHIVED':
      return 'Archivée';
    default:
      return status;
  }
}

export function resolveAcademicYearBadgeTone(
  status: AcademicYearStatus,
  isCurrent: boolean
): BadgeTone {
  if (isCurrent || status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'DRAFT') {
    return 'info';
  }
  return 'neutral';
}

export function formatPeriodSystemLabel(system: GradingPeriodSystem): string {
  switch (system) {
    case 'TRIMESTER':
      return 'Trimestriel (3 périodes)';
    case 'SEMESTER':
      return 'Semestriel (2 périodes)';
    case 'CUSTOM':
      return 'Personnalisé';
    default:
      return system;
  }
}

export function formatEvaluationPeriodStatusLabel(status: EvaluationPeriodStatus): string {
  switch (status) {
    case 'OPEN':
      return 'En cours';
    case 'PLANNED':
      return 'Planifié';
    case 'CLOSED':
      return 'Clôturé';
    case 'LOCKED':
      return 'Verrouillé';
    default:
      return status;
  }
}

export function formatLocalTimeForInput(value: string | null | undefined, fallback: string): string {
  const trimmed = value?.trim();
  if (!trimmed) {
    return fallback;
  }
  return trimmed.length >= 5 ? trimmed.slice(0, 5) : fallback;
}
