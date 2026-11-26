/**
 * Strictly typed contracts aligned with backend `com.ecole.saas.grade.api`
 * (`EvaluationController`, `ReportCardController`) and `com.ecole.saas.reporting.api`
 * (`ReportingController` -> `AcademicReportView`).
 */

export type BackendEvaluationType =
  | 'HOMEWORK'
  | 'QUIZ'
  | 'TEST'
  | 'COMPOSITION'
  | 'EXAM'
  | 'ORAL'
  | 'PRACTICAL';

export type PrimaryEvaluationType = 'HOMEWORK' | 'COMPOSITION';

export type BackendEvaluationStatus = 'DRAFT' | 'VALIDATED' | 'LOCKED' | 'CANCELLED';

export type BackendGradeAttendanceStatus =
  | 'PRESENT'
  | 'ABSENT_JUSTIFIED'
  | 'ABSENT_UNJUSTIFIED'
  | 'EXEMPTED';

export type BackendReportCardStatus = 'DRAFT' | 'VALIDATED' | 'LOCKED';

export type BackendMention =
  | 'EXCELLENT'
  | 'TRES_BIEN'
  | 'BIEN'
  | 'ASSEZ_BIEN'
  | 'PASSABLE'
  | 'INSUFFISANT'
  | 'MEDIOCRE'
  | 'NONE';

export type BackendCouncilDecision =
  | 'FELICITATIONS'
  | 'ENCOURAGEMENTS'
  | 'TABLEAU_HONNEUR'
  | 'AVERTISSEMENT_TRAVAIL'
  | 'BLAME_TRAVAIL'
  | 'PASSAGE'
  | 'REDOUBLEMENT'
  | 'NONE';

export type GradeTermCode = 'T1' | 'T2' | 'T3';

export type GradesSortField = 'rankInClass' | 'generalAverage' | 'computedAt';
export type GradesSortDirection = 'ASC' | 'DESC';

export interface BackendPageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface EvaluationResponseDto {
  readonly id: string;
  readonly academicYearId: string;
  readonly evaluationPeriodId: string;
  readonly periodCode: string;
  readonly periodStatus: string;
  readonly classRoomId: string;
  readonly classRoomCode: string;
  readonly classRoomName: string;
  readonly subjectId: string;
  readonly classSubjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly teacherId: string | null;
  readonly teacherFullName: string | null;
  readonly title: string;
  readonly evaluationType: BackendEvaluationType;
  readonly evaluationDate: string;
  readonly maxScore: number | string;
  readonly weight: number;
  readonly status: BackendEvaluationStatus;
  readonly validatedBy: string | null;
  readonly validatedAt: string | null;
  readonly lockedBy: string | null;
  readonly lockedAt: string | null;
  readonly createdBy: string | null;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreateEvaluationRequestDto {
  readonly evaluationPeriodId: string;
  readonly classRoomId: string;
  readonly subjectId: string;
  readonly teacherId?: string | null;
  readonly title: string;
  readonly evaluationType: BackendEvaluationType;
  readonly evaluationDate: string;
  readonly maxScore: number;
  readonly weight: number;
  readonly notes?: string | null;
}

export interface GradeEntryItemDto {
  readonly studentId: string;
  readonly attendanceStatus: BackendGradeAttendanceStatus;
  readonly score: number | null;
  readonly comment?: string | null;
}

export interface UpsertGradesRequestDto {
  readonly entries: readonly GradeEntryItemDto[];
}

export interface StudentGradeResponseDto {
  readonly id: string;
  readonly evaluationId: string;
  readonly studentId: string;
  readonly studentMatricule: string;
  readonly studentFirstName: string;
  readonly studentLastName: string;
  readonly attendanceStatus: BackendGradeAttendanceStatus;
  readonly score: number | string | null;
  readonly comment: string | null;
  readonly enteredBy: string | null;
  readonly enteredAt: string | null;
  readonly updatedBy: string | null;
  readonly updatedAt: string | null;
}

export interface PeriodSubjectResultResponseDto {
  readonly id: string;
  readonly subjectId: string;
  readonly classSubjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly coefficient: number;
  readonly averageScore: number | string | null;
  readonly classSubjectAverage: number | string | null;
  readonly rankInSubject: number | null;
  readonly evaluationCount: number;
  readonly teacherAppreciation: string | null;
  readonly appreciatedBy: string | null;
  readonly appreciatedAt: string | null;
}

export interface ReportCardResponseDto {
  readonly id: string;
  readonly academicYearId: string;
  readonly evaluationPeriodId: string;
  readonly periodCode: string;
  readonly periodName: string;
  readonly classRoomId: string;
  readonly classRoomCode: string;
  readonly classRoomName: string;
  readonly studentId: string;
  readonly studentMatricule: string;
  readonly studentFirstName: string;
  readonly studentLastName: string;
  readonly gradingScale: number;
  readonly generalAverage: number | string | null;
  readonly classAverage: number | string | null;
  readonly minClassAverage: number | string | null;
  readonly maxClassAverage: number | string | null;
  readonly rankInClass: number | null;
  readonly classSize: number;
  readonly tiedRank: boolean;
  readonly totalWeightedScore: number | string | null;
  readonly totalCoefficients: number;
  readonly mention: BackendMention;
  readonly councilDecision: BackendCouncilDecision;
  readonly homeroomAppreciation: string | null;
  readonly directorAppreciation: string | null;
  readonly status: BackendReportCardStatus;
  readonly computedAt: string | null;
  readonly validatedBy: string | null;
  readonly validatedAt: string | null;
  readonly lockedBy: string | null;
  readonly lockedAt: string | null;
  readonly subjectResults: readonly PeriodSubjectResultResponseDto[];
}

export interface ComputeReportCardsRequestDto {
  readonly classRoomId: string;
  readonly evaluationPeriodId: string;
}

export interface AcademicReportViewDto {
  readonly schoolCode: string;
  readonly schoolName: string;
  readonly academicYearId: string | null;
  readonly academicYearCode: string | null;
  readonly evaluationPeriodId: string | null;
  readonly evaluationPeriodCode: string | null;
  readonly gradingScale: number;
  readonly classCount: number;
  readonly totalActiveStudentsInSchool: number;
  readonly totalEnrolledInScope: number;
  readonly totalCapacityInScope: number;
  readonly occupancyRatePercent: number | string | null;
  readonly reportCardCount: number;
  readonly rankedStudentCount: number;
  readonly passedStudentCount: number;
  readonly passRatePercent: number | string | null;
  readonly overallAverage: number | string | null;
  readonly minAverage: number | string | null;
  readonly maxAverage: number | string | null;
  readonly draftReportCardCount: number;
  readonly validatedReportCardCount: number;
  readonly lockedReportCardCount: number;
  readonly generatedAt: string;
}

export interface GradeClassOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

export interface GradeSubjectOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly defaultCoefficient: number;
}

export interface GradeTermOption {
  readonly code: GradeTermCode;
  readonly id: string;
  readonly label: string;
  readonly shortLabel: string;
}

export interface GradeEntryStudentOption {
  readonly studentId: string;
  readonly studentFullName: string;
  readonly studentInitials: string;
  readonly studentMatricule: string;
  readonly classRoomId: string;
  readonly classRoomName: string;
}

export interface SubjectGradeResult {
  readonly id: string;
  readonly subjectId: string;
  readonly subjectCode: string;
  readonly subjectName: string;
  readonly coefficient: number;
  readonly averageScore: number | null;
  readonly classSubjectAverage: number | null;
  readonly rankInSubject: number | null;
  readonly evaluationCount: number;
  readonly teacherAppreciation: string | null;
}

export interface StudentReportCardRecord {
  readonly id: string;
  readonly academicYearId: string;
  readonly evaluationPeriodId: string;
  readonly termCode: GradeTermCode;
  readonly periodName: string;
  readonly classRoomId: string;
  readonly classRoomCode: string;
  readonly classRoomName: string;
  readonly studentId: string;
  readonly studentMatricule: string;
  readonly studentFirstName: string;
  readonly studentLastName: string;
  readonly studentFullName: string;
  readonly studentInitials: string;
  readonly gradingScale: number;
  readonly generalAverage: number | null;
  readonly classAverage: number | null;
  readonly minClassAverage: number | null;
  readonly maxClassAverage: number | null;
  readonly rankInClass: number | null;
  readonly classSize: number;
  readonly tiedRank: boolean;
  readonly totalWeightedScore: number | null;
  readonly totalCoefficients: number;
  readonly mention: BackendMention;
  readonly councilDecision: BackendCouncilDecision;
  readonly homeroomAppreciation: string | null;
  readonly directorAppreciation: string | null;
  readonly status: BackendReportCardStatus;
  readonly computedAtUtc: string | null;
  readonly subjectResults: readonly SubjectGradeResult[];
}

export interface GradesKpiSummary {
  readonly term: GradeTermCode;
  readonly overallAverage: number | null;
  readonly overallAverageTrend: string;
  readonly overallAverageDescription: string;
  readonly passRatePercent: number;
  readonly passedStudentCount: number;
  readonly rankedStudentCount: number;
  readonly enteredGradesCount: number;
  readonly evaluationsCount: number;
  readonly readyReportCardsCount: number;
  readonly totalReportCardsCount: number;
}

export interface GradesListQuery {
  readonly search?: string;
  readonly classRoomId?: string;
  readonly subjectId?: string;
  readonly term: GradeTermCode;
  readonly evaluationPeriodId?: string;
  readonly page: number;
  readonly size: number;
  readonly sortBy?: GradesSortField;
  readonly direction?: GradesSortDirection;
}

export interface GradesPageResult {
  readonly content: readonly StudentReportCardRecord[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly kpis: GradesKpiSummary;
  readonly classes: readonly GradeClassOption[];
  readonly subjects: readonly GradeSubjectOption[];
  readonly terms: readonly GradeTermOption[];
  readonly studentsForEntry: readonly GradeEntryStudentOption[];
}

export interface GradeEntryScoreInput {
  readonly studentId: string;
  readonly score: number;
  readonly comment?: string;
}

export interface SaveEvaluationGradesInput {
  readonly evaluationPeriodId: string;
  readonly termCode: GradeTermCode;
  readonly classRoomId: string;
  readonly subjectId: string;
  readonly evaluationType: PrimaryEvaluationType;
  readonly coefficient: number;
  readonly title: string;
  readonly evaluationDate: string;
  readonly notes?: string;
  readonly entries: readonly GradeEntryScoreInput[];
}

export interface SaveEvaluationGradesResult {
  readonly evaluationId: string;
  readonly title: string;
  readonly savedCount: number;
  readonly classAverage: number | null;
}

export interface GenerateReportCardsInput {
  readonly classRoomId: string;
  readonly evaluationPeriodId: string;
  readonly termCode: GradeTermCode;
}

export interface GenerateReportCardsResult {
  readonly generatedCount: number;
  readonly termCode: GradeTermCode;
  readonly classRoomName: string;
}

export const DEFAULT_GRADE_TERMS: readonly GradeTermOption[] = [
  { code: 'T1', id: 'term-t1', label: 'Trimestre 1', shortLabel: 'T1' },
  { code: 'T2', id: 'term-t2', label: 'Trimestre 2', shortLabel: 'T2' },
  { code: 'T3', id: 'term-t3', label: 'Trimestre 3', shortLabel: 'T3' },
];

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string | null | undefined): boolean {
  return Boolean(value && UUID_PATTERN.test(value.trim()));
}

export function parseNumericValue(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  const numeric = typeof value === 'string' ? Number.parseFloat(value.replace(',', '.')) : value;
  return Number.isFinite(numeric) ? numeric : null;
}

export function roundToTwoDecimals(value: number): number {
  return Math.round(value * 100) / 100;
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

export function parseTermCode(raw: string | null | undefined): GradeTermCode {
  if (!raw) {
    return 'T1';
  }
  const upper = raw.trim().toUpperCase();
  if (upper === 'T2' || upper.includes('2')) {
    return 'T2';
  }
  if (upper === 'T3' || upper.includes('3')) {
    return 'T3';
  }
  return 'T1';
}

export function resolveMentionFromAverage(average: number | null): BackendMention {
  if (average === null || !Number.isFinite(average)) {
    return 'NONE';
  }
  if (average >= 18) {
    return 'EXCELLENT';
  }
  if (average >= 16) {
    return 'TRES_BIEN';
  }
  if (average >= 14) {
    return 'BIEN';
  }
  if (average >= 12) {
    return 'ASSEZ_BIEN';
  }
  if (average >= 10) {
    return 'PASSABLE';
  }
  if (average >= 8) {
    return 'INSUFFISANT';
  }
  return 'MEDIOCRE';
}

export function resolveCouncilDecisionFromAverage(average: number | null): BackendCouncilDecision {
  if (average === null || !Number.isFinite(average)) {
    return 'NONE';
  }
  if (average >= 16) {
    return 'FELICITATIONS';
  }
  if (average >= 14) {
    return 'TABLEAU_HONNEUR';
  }
  if (average >= 12) {
    return 'ENCOURAGEMENTS';
  }
  if (average >= 10) {
    return 'PASSAGE';
  }
  if (average >= 8) {
    return 'AVERTISSEMENT_TRAVAIL';
  }
  return 'BLAME_TRAVAIL';
}

export function mapSubjectResultDto(dto: PeriodSubjectResultResponseDto): SubjectGradeResult {
  return {
    id: dto.id,
    subjectId: dto.subjectId,
    subjectCode: dto.subjectCode,
    subjectName: dto.subjectName,
    coefficient: Math.max(1, Math.trunc(dto.coefficient)),
    averageScore: parseNumericValue(dto.averageScore),
    classSubjectAverage: parseNumericValue(dto.classSubjectAverage),
    rankInSubject: dto.rankInSubject,
    evaluationCount: Math.max(0, Math.trunc(dto.evaluationCount)),
    teacherAppreciation: dto.teacherAppreciation,
  };
}

export function mapReportCardDtoToRecord(dto: ReportCardResponseDto): StudentReportCardRecord {
  const fullName = `${dto.studentFirstName} ${dto.studentLastName}`.trim();
  const generalAverage = parseNumericValue(dto.generalAverage);

  return {
    id: dto.id,
    academicYearId: dto.academicYearId,
    evaluationPeriodId: dto.evaluationPeriodId,
    termCode: parseTermCode(dto.periodCode || dto.periodName),
    periodName: dto.periodName || `Trimestre ${parseTermCode(dto.periodCode).slice(1)}`,
    classRoomId: dto.classRoomId,
    classRoomCode: dto.classRoomCode,
    classRoomName: dto.classRoomName,
    studentId: dto.studentId,
    studentMatricule: dto.studentMatricule,
    studentFirstName: dto.studentFirstName,
    studentLastName: dto.studentLastName,
    studentFullName: fullName,
    studentInitials: buildStudentInitials(fullName),
    gradingScale: dto.gradingScale || 20,
    generalAverage,
    classAverage: parseNumericValue(dto.classAverage),
    minClassAverage: parseNumericValue(dto.minClassAverage),
    maxClassAverage: parseNumericValue(dto.maxClassAverage),
    rankInClass: dto.rankInClass,
    classSize: Math.max(0, Math.trunc(dto.classSize)),
    tiedRank: Boolean(dto.tiedRank),
    totalWeightedScore: parseNumericValue(dto.totalWeightedScore),
    totalCoefficients: Math.max(0, Math.trunc(dto.totalCoefficients)),
    mention: dto.mention ?? resolveMentionFromAverage(generalAverage),
    councilDecision: dto.councilDecision ?? resolveCouncilDecisionFromAverage(generalAverage),
    homeroomAppreciation: dto.homeroomAppreciation,
    directorAppreciation: dto.directorAppreciation,
    status: dto.status,
    computedAtUtc: dto.computedAt,
    subjectResults: (dto.subjectResults ?? []).map(mapSubjectResultDto),
  };
}

export function mapSaveEvaluationInputToCreateDto(
  input: SaveEvaluationGradesInput
): CreateEvaluationRequestDto {
  return {
    evaluationPeriodId: input.evaluationPeriodId.trim(),
    classRoomId: input.classRoomId.trim(),
    subjectId: input.subjectId.trim(),
    title: input.title.trim(),
    evaluationType: input.evaluationType,
    evaluationDate: input.evaluationDate,
    maxScore: 20,
    weight: Math.max(1, Math.min(20, Math.trunc(input.coefficient))),
    notes: input.notes?.trim() || null,
  };
}

export function mapSaveEvaluationEntriesToUpsertDto(
  entries: readonly GradeEntryScoreInput[]
): UpsertGradesRequestDto {
  return {
    entries: entries.map((item) => ({
      studentId: item.studentId.trim(),
      attendanceStatus: 'PRESENT',
      score: roundToTwoDecimals(item.score),
      comment: item.comment?.trim() || null,
    })),
  };
}

export function buildKpisFromAcademicReport(
  report: AcademicReportViewDto,
  term: GradeTermCode,
  evaluationsCount: number,
  enteredGradesCount: number
): GradesKpiSummary {
  const overallAverage = parseNumericValue(report.overallAverage);
  const passRateRaw = parseNumericValue(report.passRatePercent);
  const passRatePercent =
    passRateRaw !== null
      ? Math.round(passRateRaw * 10) / 10
      : report.rankedStudentCount > 0
        ? Math.round((report.passedStudentCount / report.rankedStudentCount) * 1000) / 10
        : 0;
  const readyCount = Math.trunc(report.validatedReportCardCount + report.lockedReportCardCount);
  const totalCards = Math.max(readyCount, Math.trunc(report.reportCardCount));

  return {
    term,
    overallAverage: overallAverage !== null ? roundToTwoDecimals(overallAverage) : null,
    overallAverageTrend:
      overallAverage !== null && overallAverage >= 12 ? 'Progression stable' : 'Suivi renforcé',
    overallAverageDescription: `barème sur ${report.gradingScale || 20}`,
    passRatePercent,
    passedStudentCount: Math.trunc(report.passedStudentCount),
    rankedStudentCount: Math.trunc(report.rankedStudentCount),
    enteredGradesCount,
    evaluationsCount,
    readyReportCardsCount: readyCount,
    totalReportCardsCount: totalCards,
  };
}

export function computeKpisFromRecords(
  records: readonly StudentReportCardRecord[],
  term: GradeTermCode,
  evaluationsCount?: number
): GradesKpiSummary {
  const ranked = records.filter((item) => item.generalAverage !== null);
  const avgSum = ranked.reduce((sum, item) => sum + (item.generalAverage ?? 0), 0);
  const overallAverage = ranked.length > 0 ? roundToTwoDecimals(avgSum / ranked.length) : null;
  const passedStudentCount = ranked.filter((item) => (item.generalAverage ?? 0) >= 10).length;
  const passRatePercent =
    ranked.length > 0 ? Math.round((passedStudentCount / ranked.length) * 1000) / 10 : 0;

  const enteredGradesCount = records.reduce(
    (sum, card) =>
      sum + card.subjectResults.reduce((subSum, subject) => subSum + subject.evaluationCount, 0),
    0
  );
  const computedEvaluationsCount =
    evaluationsCount ??
    Math.max(
      0,
      new Set(
        records.flatMap((card) =>
          card.subjectResults.map((sub) => `${card.classRoomId}:${sub.subjectId}`)
        )
      ).size
    );

  const readyReportCardsCount = records.filter(
    (item) => item.status === 'VALIDATED' || item.status === 'LOCKED'
  ).length;

  return {
    term,
    overallAverage,
    overallAverageTrend:
      overallAverage === null
        ? 'Aucune note'
        : overallAverage >= 14
          ? 'Très bon niveau'
          : overallAverage >= 12
            ? 'Objectif atteint'
            : overallAverage >= 10
              ? 'Ensemble satisfaisant'
              : 'Soutien recommandé',
    overallAverageDescription:
      ranked.length > 1 ? `sur ${ranked.length} élèves classés` : `sur ${ranked.length} élève classé`,
    passRatePercent,
    passedStudentCount,
    rankedStudentCount: ranked.length,
    enteredGradesCount,
    evaluationsCount: computedEvaluationsCount,
    readyReportCardsCount,
    totalReportCardsCount: records.length,
  };
}
