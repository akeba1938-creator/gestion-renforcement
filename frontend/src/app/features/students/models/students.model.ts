/**
 * Backend DTO contracts aligned strictly with `com.ecole.saas.student.api.*`
 * (`/api/v1/students`) and view models for the `Élèves` feature.
 */

export type BackendStudentStatus =
  | 'ACTIVE'
  | 'TRANSFERRED'
  | 'GRADUATED'
  | 'WITHDRAWN'
  | 'ARCHIVED';

export type BackendStudentGender = 'MALE' | 'FEMALE' | 'OTHER';

export type StudentSortField =
  | 'matricule'
  | 'lastName'
  | 'firstName'
  | 'birthDate'
  | 'admissionDate'
  | 'status'
  | 'createdAt';

export type SortOrderDirection = 'ASC' | 'DESC';

/**
 * Exact mirror of backend `com.ecole.saas.student.api.PageResponse<T>`.
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
 * Exact mirror of backend `com.ecole.saas.student.api.StudentResponse`.
 */
export interface StudentResponseDto {
  readonly id: string;
  readonly matricule: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly gender: BackendStudentGender;
  readonly birthDate: string;
  readonly birthPlace: string | null;
  readonly nationality: string | null;
  readonly address: string | null;
  readonly phone: string | null;
  readonly email: string | null;
  readonly photoObjectKey: string | null;
  readonly medicalNotes: string | null;
  readonly admissionDate: string;
  readonly status: BackendStudentStatus;
  readonly statusReason: string | null;
  readonly archivedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Exact mirror of backend `com.ecole.saas.student.api.CreateStudentRequest`.
 */
export interface CreateStudentRequestDto {
  readonly firstName: string;
  readonly lastName: string;
  readonly gender: BackendStudentGender;
  readonly birthDate: string;
  readonly birthPlace?: string | null;
  readonly nationality?: string | null;
  readonly address?: string | null;
  readonly phone?: string | null;
  readonly email?: string | null;
  readonly medicalNotes?: string | null;
  readonly admissionDate?: string | null;
}

export type StudentDisplayStatus =
  | 'ACTIVE'
  | 'PENDING'
  | 'WITHDRAWN'
  | 'TRANSFERRED'
  | 'GRADUATED'
  | 'ARCHIVED';

export type StudentStatusFilter = 'ALL' | 'ACTIVE' | 'PENDING' | 'WITHDRAWN';

export type StudentFinancialStatus = 'UP_TO_DATE' | 'PARTIAL' | 'OVERDUE' | 'UNKNOWN';

export interface StudentRecord {
  readonly id: string;
  readonly matricule: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly fullName: string;
  readonly initials: string;
  readonly gender: BackendStudentGender;
  readonly birthDate: string;
  readonly admissionDate: string;
  readonly className: string;
  readonly guardianName: string;
  /** E.164 format (e.g. +221770000001) or empty string if unavailable. */
  readonly guardianPhone: string;
  readonly email: string | null;
  readonly address: string | null;
  readonly status: StudentDisplayStatus;
  readonly statusReason: string | null;
  /** Remaining tuition balance in integer FCFA (0 = up to date, null = unavailable). */
  readonly balanceFcfa: number | null;
  readonly financialStatus: StudentFinancialStatus;
  /** UTC ISO-8601 timestamp. */
  readonly createdAt: string;
}

export interface StudentsListQuery {
  readonly search?: string;
  readonly className?: string;
  readonly status?: StudentStatusFilter;
  readonly page: number;
  readonly size: number;
  readonly sortBy?: StudentSortField;
  readonly direction?: SortOrderDirection;
}

export interface StudentsPageResult {
  readonly content: readonly StudentRecord[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly availableClasses: readonly string[];
}

export interface CreateStudentInput {
  readonly firstName: string;
  readonly lastName: string;
  readonly className: string;
  readonly guardianName: string;
  readonly guardianPhone: string;
  readonly gender?: BackendStudentGender;
  readonly birthDate?: string;
}

export const DEFAULT_STUDENT_CLASSES: readonly string[] = [
  'Terminale S2',
  '1ère L',
  'Seconde S',
  '3ème A',
  '6ème B',
  'CM2 (Primaire)',
];

const DEFAULT_FALLBACK_BIRTH_DATE = '2012-09-15';

export function buildStudentInitials(firstName: string, lastName: string): string {
  const first = firstName.trim().charAt(0).toUpperCase();
  const last = lastName.trim().charAt(0).toUpperCase();
  return `${first}${last}`.trim() || 'EL';
}

export function mapStatusFilterToBackendParam(
  status: StudentStatusFilter | undefined
): BackendStudentStatus | undefined {
  if (status === 'ACTIVE') {
    return 'ACTIVE';
  }
  if (status === 'WITHDRAWN') {
    return 'WITHDRAWN';
  }
  return undefined;
}

export function mapStudentDtoToRecord(
  dto: StudentResponseDto,
  overrides?: Partial<
    Pick<StudentRecord, 'className' | 'guardianName' | 'guardianPhone' | 'balanceFcfa' | 'financialStatus'>
  >
): StudentRecord {
  const firstName = dto.firstName.trim();
  const lastName = dto.lastName.trim();
  const fullName = `${firstName} ${lastName}`.trim();

  return {
    id: dto.id,
    matricule: dto.matricule,
    firstName,
    lastName,
    fullName,
    initials: buildStudentInitials(firstName, lastName),
    gender: dto.gender,
    birthDate: dto.birthDate,
    admissionDate: dto.admissionDate,
    className: overrides?.className ?? 'Non affecté',
    guardianName: overrides?.guardianName ?? 'Tuteur non renseigné',
    guardianPhone: overrides?.guardianPhone ?? dto.phone ?? '',
    email: dto.email,
    address: dto.address,
    status: dto.status,
    statusReason: dto.statusReason,
    balanceFcfa: overrides?.balanceFcfa ?? null,
    financialStatus: overrides?.financialStatus ?? 'UNKNOWN',
    createdAt: dto.createdAt,
  };
}

function getTodayIsoDateDakar(): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dakar',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(new Date());
}

export function mapCreateInputToRequestDto(input: CreateStudentInput): CreateStudentRequestDto {
  const normalizedPhone = input.guardianPhone.trim();
  const birthDate = input.birthDate?.trim() || DEFAULT_FALLBACK_BIRTH_DATE;
  return {
    firstName: input.firstName.trim(),
    lastName: input.lastName.trim(),
    gender: input.gender ?? 'FEMALE',
    birthDate,
    phone: normalizedPhone || null,
    admissionDate: getTodayIsoDateDakar(),
  };
}
