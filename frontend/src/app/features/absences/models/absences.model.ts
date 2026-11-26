export type BackendAttendanceRecordStatus = 'PRESENT' | 'LATE' | 'ABSENT' | 'EXCUSED';
export type BackendAttendanceSessionStatus = 'OPEN' | 'SUBMITTED' | 'LOCKED' | 'CANCELLED';
export type BackendAttendanceTimeSlot = 'MORNING' | 'AFTERNOON' | 'FULL_DAY' | 'CUSTOM';

export type AbsenceRecordStatus = 'JUSTIFIED' | 'UNJUSTIFIED' | 'LATE';
export type AbsenceStatusFilter = 'ALL' | AbsenceRecordStatus;
export type RollCallStatus = 'PRESENT' | 'ABSENT' | 'LATE';

/**
 * Spring PageResponse mirror (`com.ecole.saas.attendance.api.PageResponse` / `academic.api.PageResponse`).
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
 * Exact backend DTO mirror (`com.ecole.saas.attendance.api.AttendanceRecordResponse`).
 */
export interface AttendanceRecordResponseDto {
  readonly id: string;
  readonly sessionId: string;
  readonly studentId: string;
  readonly studentMatricule: string;
  readonly studentFirstName: string;
  readonly studentLastName: string;
  readonly status: BackendAttendanceRecordStatus;
  readonly lateMinutes: number | null;
  readonly justified: boolean;
  readonly justificationReason: string | null;
  readonly justificationDocumentRef: string | null;
  readonly justifiedBy: string | null;
  readonly justifiedAt: string | null;
  readonly notes: string | null;
  readonly recordedBy: string | null;
  readonly updatedBy: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Exact backend DTO mirror (`com.ecole.saas.attendance.api.AttendanceSessionResponse`).
 */
export interface AttendanceSessionResponseDto {
  readonly id: string;
  readonly academicYearId: string;
  readonly evaluationPeriodId: string | null;
  readonly periodCode: string | null;
  readonly classRoomId: string;
  readonly classRoomCode: string | null;
  readonly classRoomName: string | null;
  readonly subjectId: string | null;
  readonly subjectCode: string | null;
  readonly subjectName: string | null;
  readonly teacherId: string | null;
  readonly teacherFullName: string | null;
  readonly sessionDate: string;
  readonly timeSlot: BackendAttendanceTimeSlot;
  readonly startTime: string | null;
  readonly endTime: string | null;
  readonly label: string | null;
  readonly status: BackendAttendanceSessionStatus;
  readonly recordedBy: string | null;
  readonly submittedAt: string | null;
  readonly lockedBy: string | null;
  readonly lockedAt: string | null;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly records: readonly AttendanceRecordResponseDto[];
}

/**
 * Exact backend DTO mirror (`com.ecole.saas.attendance.api.CreateAttendanceSessionRequest`).
 */
export interface CreateAttendanceSessionRequestDto {
  readonly classRoomId: string;
  readonly evaluationPeriodId?: string;
  readonly subjectId?: string;
  readonly teacherId?: string;
  readonly sessionDate: string;
  readonly timeSlot?: BackendAttendanceTimeSlot;
  readonly startTime?: string;
  readonly endTime?: string;
  readonly label?: string;
  readonly notes?: string;
  readonly initializePresent?: boolean;
}

/**
 * Exact backend DTO mirror (`com.ecole.saas.attendance.api.UpsertAttendanceRecordsRequest`).
 */
export interface UpsertAttendanceRecordItemDto {
  readonly studentId: string;
  readonly status: BackendAttendanceRecordStatus;
  readonly lateMinutes?: number;
  readonly notes?: string;
}

export interface UpsertAttendanceRecordsRequestDto {
  readonly records: readonly UpsertAttendanceRecordItemDto[];
}

/**
 * Exact backend DTO mirror (`com.ecole.saas.attendance.api.JustifyAttendanceRecordRequest`).
 */
export interface JustifyAttendanceRecordRequestDto {
  readonly reason: string;
  readonly documentRef?: string;
}

/**
 * Backend reporting mirror (`GET /api/v1/reporting/attendance`).
 */
export interface AttendanceClassMetricsDto {
  readonly classRoomId: string;
  readonly classRoomCode: string;
  readonly classRoomName: string;
  readonly sessionCount: number;
  readonly totalRecords: number;
  readonly presentCount: number;
  readonly lateCount: number;
  readonly totalLateMinutes: number;
  readonly absentCount: number;
  readonly justifiedAbsenceCount: number;
  readonly unjustifiedAbsenceCount: number;
  readonly excusedCount: number;
  readonly attendanceRatePercent: number;
}

export interface AttendanceReportViewDto {
  readonly schoolCode: string;
  readonly schoolName: string;
  readonly fromDate: string | null;
  readonly toDate: string | null;
  readonly sessionCount: number;
  readonly totalRecords: number;
  readonly presentCount: number;
  readonly lateCount: number;
  readonly totalLateMinutes: number;
  readonly absentCount: number;
  readonly justifiedAbsenceCount: number;
  readonly unjustifiedAbsenceCount: number;
  readonly excusedCount: number;
  readonly attendanceRatePercent: number;
  readonly classes: readonly AttendanceClassMetricsDto[];
  readonly generatedAt: string;
}

/**
 * Backend academic class room mirror (`GET /api/v1/academic/classes`).
 */
export interface ClassRoomResponseDto {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly gradeLevelName: string | null;
  readonly status: string;
}

export interface AbsenceClassOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

export interface RollCallStudentItem {
  readonly studentId: string;
  readonly studentName: string;
  readonly studentInitials: string;
  readonly matricule: string;
  readonly classRoomId: string;
  readonly className: string;
}

/**
 * UI-ready record representing an absence or tardiness item in the Absences table & drawer.
 */
export interface AbsenceRecord {
  readonly id: string;
  readonly sessionId: string;
  readonly studentId: string;
  readonly studentName: string;
  readonly studentInitials: string;
  readonly matricule: string;
  readonly classRoomId: string;
  readonly className: string;
  readonly sessionDate: string;
  readonly recordedAtUtc: string;
  readonly timeSlot: BackendAttendanceTimeSlot;
  readonly slotLabel: string;
  readonly subjectName: string | null;
  readonly teacherName: string | null;
  readonly rawStatus: BackendAttendanceRecordStatus;
  readonly status: AbsenceRecordStatus;
  readonly lateMinutes: number | null;
  readonly justified: boolean;
  readonly justificationReason: string | null;
  readonly justificationDocumentRef: string | null;
  readonly justifiedAtUtc: string | null;
  readonly notes: string | null;
}

export interface AbsencesKpiSummary {
  readonly attendanceRatePercent: number;
  readonly attendanceRateTrendLabel: string;
  readonly attendanceRateSubLabel: string;
  readonly todayAbsencesCount: number;
  readonly todayAbsencesTrendLabel: string;
  readonly todayAbsencesSubLabel: string;
  readonly unjustifiedCount: number;
  readonly unjustifiedTrendLabel: string;
  readonly unjustifiedSubLabel: string;
  readonly lateCount: number;
  readonly lateTrendLabel: string;
  readonly lateSubLabel: string;
}

export interface AbsencesListQuery {
  readonly page: number;
  readonly size: number;
  readonly search?: string;
  readonly className?: string;
  readonly classRoomId?: string;
  readonly status?: AbsenceStatusFilter;
  readonly date?: string;
  readonly sortBy?: string;
  readonly direction?: 'ASC' | 'DESC';
}

export interface AbsencesPageResult {
  readonly content: readonly AbsenceRecord[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly kpis: AbsencesKpiSummary;
  readonly availableClasses: readonly AbsenceClassOption[];
  readonly rollCallRosterByClass: Readonly<Record<string, readonly RollCallStudentItem[]>>;
}

export interface RollCallEntryInput {
  readonly studentId: string;
  readonly status: RollCallStatus;
  readonly lateMinutes?: number;
  readonly notes?: string;
}

export interface SubmitRollCallInput {
  readonly classRoomId: string;
  readonly className: string;
  readonly sessionDate: string;
  readonly timeSlot: BackendAttendanceTimeSlot;
  readonly label?: string;
  readonly notes?: string;
  readonly entries: readonly RollCallEntryInput[];
}

export interface SubmitRollCallResult {
  readonly sessionId: string;
  readonly className: string;
  readonly sessionDate: string;
  readonly timeSlot: BackendAttendanceTimeSlot;
  readonly presentCount: number;
  readonly absentCount: number;
  readonly lateCount: number;
}

export interface JustifyAbsenceInput {
  readonly recordId: string;
  readonly reason: string;
  readonly documentRef?: string;
}

export const DEFAULT_ABSENCE_CLASSES: readonly AbsenceClassOption[] = [
  { id: 'cls-demo-ts2', code: 'TS2', name: 'Terminale S2' },
  { id: 'cls-demo-1l', code: '1L', name: '1ère L' },
  { id: 'cls-demo-2s', code: '2S', name: 'Seconde S' },
  { id: 'cls-demo-3a', code: '3A', name: '3ème A' },
  { id: 'cls-demo-6b', code: '6B', name: '6ème B' },
  { id: 'cls-demo-cm2', code: 'CM2', name: 'CM2 (Primaire)' },
];

export const TIME_SLOT_OPTIONS: readonly {
  readonly value: BackendAttendanceTimeSlot;
  readonly label: string;
  readonly shortLabel: string;
}[] = [
  { value: 'MORNING', label: 'Matin (08h00 – 12h00)', shortLabel: 'Matin' },
  { value: 'AFTERNOON', label: 'Après-midi (14h00 – 17h00)', shortLabel: 'Après-midi' },
  { value: 'FULL_DAY', label: 'Journée complète', shortLabel: 'Journée' },
];

const DAKAR_ISO_DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Africa/Dakar',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const DAKAR_LONG_DATE_FORMATTER = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Africa/Dakar',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

export function getTodayDakarIsoDate(reference: Date = new Date()): string {
  return DAKAR_ISO_DATE_FORMATTER.format(reference);
}

export function formatDakarLongDate(reference: Date = new Date()): string {
  const formatted = DAKAR_LONG_DATE_FORMATTER.format(reference);
  return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}

export function buildStudentInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return 'EL';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0][0] ?? ''}${parts[parts.length - 1][0] ?? ''}`.toUpperCase();
}

export function formatTimeSlotLabel(
  slot: BackendAttendanceTimeSlot,
  startTime?: string | null,
  endTime?: string | null
): string {
  if (startTime && endTime) {
    const start = startTime.slice(0, 5).replace(':', 'h');
    const end = endTime.slice(0, 5).replace(':', 'h');
    return `${start}–${end}`;
  }
  switch (slot) {
    case 'MORNING':
      return 'Matin (08h–12h)';
    case 'AFTERNOON':
      return 'Après-midi (14h–17h)';
    case 'FULL_DAY':
      return 'Journée complète';
    case 'CUSTOM':
      return 'Créneau libre';
  }
}

export function resolveAbsenceStatus(
  rawStatus: BackendAttendanceRecordStatus,
  justified: boolean
): AbsenceRecordStatus {
  if (rawStatus === 'LATE') {
    return 'LATE';
  }
  if (rawStatus === 'EXCUSED' || justified) {
    return 'JUSTIFIED';
  }
  return 'UNJUSTIFIED';
}

export function mapAttendanceRecordDto(
  session: AttendanceSessionResponseDto,
  dto: AttendanceRecordResponseDto
): AbsenceRecord {
  const fullName = `${dto.studentFirstName} ${dto.studentLastName}`.trim() || 'Élève';
  const className = session.classRoomName?.trim() || session.classRoomCode?.trim() || 'Non affectée';
  const slotLabel = formatTimeSlotLabel(session.timeSlot, session.startTime, session.endTime);

  return {
    id: dto.id,
    sessionId: session.id,
    studentId: dto.studentId,
    studentName: fullName,
    studentInitials: buildStudentInitials(fullName),
    matricule: dto.studentMatricule,
    classRoomId: session.classRoomId,
    className,
    sessionDate: session.sessionDate,
    recordedAtUtc: dto.createdAt || session.createdAt || `${session.sessionDate}T08:00:00Z`,
    timeSlot: session.timeSlot,
    slotLabel,
    subjectName: session.subjectName ?? null,
    teacherName: session.teacherFullName ?? null,
    rawStatus: dto.status,
    status: resolveAbsenceStatus(dto.status, dto.justified),
    lateMinutes: dto.lateMinutes,
    justified: dto.justified || dto.status === 'EXCUSED',
    justificationReason: dto.justificationReason,
    justificationDocumentRef: dto.justificationDocumentRef,
    justifiedAtUtc: dto.justifiedAt,
    notes: dto.notes,
  };
}

export function extractAbsenceRecordsFromSessions(
  sessions: readonly AttendanceSessionResponseDto[]
): readonly AbsenceRecord[] {
  const items: AbsenceRecord[] = [];
  for (const session of sessions) {
    if (session.status === 'CANCELLED') {
      continue;
    }
    for (const rec of session.records) {
      if (rec.status === 'PRESENT') {
        continue;
      }
      items.push(mapAttendanceRecordDto(session, rec));
    }
  }
  return items.sort((a, b) => b.recordedAtUtc.localeCompare(a.recordedAtUtc));
}

export function extractRosterFromSessions(
  sessions: readonly AttendanceSessionResponseDto[]
): Readonly<Record<string, readonly RollCallStudentItem[]>> {
  const mapByClass = new Map<string, Map<string, RollCallStudentItem>>();
  for (const session of sessions) {
    const className = session.classRoomName?.trim() || session.classRoomCode?.trim() || 'Classe';
    let classRoster = mapByClass.get(session.classRoomId);
    if (!classRoster) {
      classRoster = new Map<string, RollCallStudentItem>();
      mapByClass.set(session.classRoomId, classRoster);
    }
    for (const rec of session.records) {
      if (!classRoster.has(rec.studentId)) {
        const studentName = `${rec.studentFirstName} ${rec.studentLastName}`.trim() || 'Élève';
        classRoster.set(rec.studentId, {
          studentId: rec.studentId,
          studentName,
          studentInitials: buildStudentInitials(studentName),
          matricule: rec.studentMatricule,
          classRoomId: session.classRoomId,
          className,
        });
      }
    }
  }

  const result: Record<string, readonly RollCallStudentItem[]> = {};
  for (const [classId, roster] of mapByClass.entries()) {
    result[classId] = Array.from(roster.values()).sort((a, b) =>
      a.studentName.localeCompare(b.studentName, 'fr')
    );
  }
  return result;
}

export function buildKpisFromAttendanceReport(
  report: AttendanceReportViewDto,
  records: readonly AbsenceRecord[],
  todayIso: string
): AbsencesKpiSummary {
  const todayAbsencesCount = records.filter(
    (item) => item.sessionDate === todayIso && item.status !== 'LATE'
  ).length;
  const unjustifiedCount = Math.max(0, Math.trunc(report.unjustifiedAbsenceCount));
  const lateCount = Math.max(0, Math.trunc(report.lateCount));
  const rate = Math.round(Number(report.attendanceRatePercent ?? 0) * 10) / 10;

  return {
    attendanceRatePercent: rate,
    attendanceRateTrendLabel: rate >= 94 ? 'Assiduité élevée' : rate >= 88 ? 'À surveiller' : 'Alerte',
    attendanceRateSubLabel: `${Math.trunc(report.sessionCount)} séances enregistrées`,
    todayAbsencesCount,
    todayAbsencesTrendLabel: todayAbsencesCount === 0 ? 'Journée calme' : "Aujourd'hui",
    todayAbsencesSubLabel: `${Math.trunc(report.absentCount)} absences sur la période`,
    unjustifiedCount,
    unjustifiedTrendLabel: unjustifiedCount > 0 ? 'À régulariser' : 'Tout justifié',
    unjustifiedSubLabel: `${Math.trunc(report.justifiedAbsenceCount)} justifiées`,
    lateCount,
    lateTrendLabel:
      report.totalLateMinutes > 0 ? `${Math.trunc(report.totalLateMinutes)} min cumulées` : 'Aucun cumul',
    lateSubLabel: 'retards signalés',
  };
}

export function computeFallbackKpisFromSessions(
  sessions: readonly AttendanceSessionResponseDto[],
  records: readonly AbsenceRecord[],
  todayIso: string
): AbsencesKpiSummary {
  let totalRecords = 0;
  let presentOrLate = 0;
  let totalLateMinutes = 0;

  for (const session of sessions) {
    if (session.status === 'CANCELLED') {
      continue;
    }
    for (const rec of session.records) {
      totalRecords += 1;
      if (rec.status === 'PRESENT' || rec.status === 'LATE') {
        presentOrLate += 1;
      }
      if (rec.status === 'LATE' && rec.lateMinutes) {
        totalLateMinutes += rec.lateMinutes;
      }
    }
  }

  const attendanceRatePercent =
    totalRecords > 0 ? Math.round((presentOrLate / totalRecords) * 1000) / 10 : 100;
  const todayAbsencesCount = records.filter(
    (item) => item.sessionDate === todayIso && item.status !== 'LATE'
  ).length;
  const unjustifiedCount = records.filter((item) => item.status === 'UNJUSTIFIED').length;
  const justifiedCount = records.filter((item) => item.status === 'JUSTIFIED').length;
  const lateCount = records.filter((item) => item.status === 'LATE').length;

  return {
    attendanceRatePercent,
    attendanceRateTrendLabel:
      attendanceRatePercent >= 94 ? 'Assiduité élevée' : attendanceRatePercent >= 88 ? 'À surveiller' : 'Alerte',
    attendanceRateSubLabel: `${sessions.length} séances consolidées`,
    todayAbsencesCount,
    todayAbsencesTrendLabel: todayAbsencesCount === 0 ? 'Journée calme' : "Aujourd'hui",
    todayAbsencesSubLabel: `${unjustifiedCount + justifiedCount} absences enregistrées`,
    unjustifiedCount,
    unjustifiedTrendLabel: unjustifiedCount > 0 ? 'À régulariser' : 'Tout justifié',
    unjustifiedSubLabel: `${justifiedCount} déjà justifiées`,
    lateCount,
    lateTrendLabel: totalLateMinutes > 0 ? `${totalLateMinutes} min cumulées` : 'Aucun cumul',
    lateSubLabel: 'retards signalés',
  };
}
