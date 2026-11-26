import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  BackendReportCardStatus,
  DEFAULT_GRADE_TERMS,
  GenerateReportCardsInput,
  GenerateReportCardsResult,
  GradeClassOption,
  GradeEntryStudentOption,
  GradeSubjectOption,
  GradeTermCode,
  GradesListQuery,
  GradesPageResult,
  SaveEvaluationGradesInput,
  SaveEvaluationGradesResult,
  StudentReportCardRecord,
  SubjectGradeResult,
  buildStudentInitials,
  computeKpisFromRecords,
  resolveCouncilDecisionFromAverage,
  resolveMentionFromAverage,
  roundToTwoDecimals,
} from '../models/grades.model';
import { GradesDemoFallback } from './grades-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';

const DEMO_CLASSES: readonly GradeClassOption[] = [
  { id: 'cls-ts2', code: 'TS2', name: 'Terminale S2' },
  { id: 'cls-3a', code: '3A', name: '3ème A' },
  { id: 'cls-1l', code: '1L', name: '1ère L' },
  { id: 'cls-2s', code: '2S', name: 'Seconde S' },
  { id: 'cls-6b', code: '6B', name: '6ème B' },
  { id: 'cls-cm2', code: 'CM2', name: 'CM2 (Primaire)' },
];

const DEMO_SUBJECTS: readonly GradeSubjectOption[] = [
  { id: 'sub-math', code: 'MATH', name: 'Mathématiques', defaultCoefficient: 5 },
  { id: 'sub-fr', code: 'FR', name: 'Français', defaultCoefficient: 4 },
  { id: 'sub-pc', code: 'PC', name: 'Sciences Physiques', defaultCoefficient: 5 },
  { id: 'sub-svt', code: 'SVT', name: 'SVT', defaultCoefficient: 4 },
  { id: 'sub-hg', code: 'HG', name: 'Histoire-Géographie', defaultCoefficient: 2 },
  { id: 'sub-ang', code: 'ANG', name: 'Anglais', defaultCoefficient: 2 },
];

interface StudentSeed {
  readonly studentId: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly matricule: string;
  readonly classRoomId: string;
  readonly classSize: number;
  readonly baseScores: Readonly<Record<string, number>>;
  readonly statusT1: BackendReportCardStatus;
  readonly statusT2: BackendReportCardStatus;
  readonly statusT3: BackendReportCardStatus;
  readonly homeroomNote: string;
  readonly directorNote: string;
}

const DEMO_STUDENT_SEEDS: readonly StudentSeed[] = [
  {
    studentId: 'stu-demo-001',
    firstName: 'Aminata',
    lastName: 'Diallo-Démo',
    matricule: 'DEMO-2026-001',
    classRoomId: 'cls-ts2',
    classSize: 34,
    baseScores: {
      'sub-math': 17.5,
      'sub-fr': 15.75,
      'sub-pc': 16.5,
      'sub-svt': 16.25,
      'sub-hg': 15.0,
      'sub-ang': 16.0,
    },
    statusT1: 'LOCKED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Excellent trimestre, rigueur scientifique exemplaire et participation active.',
    directorNote: 'Félicitations du conseil de classe. Poursuivez sur cette lancée vers le Bac.',
  },
  {
    studentId: 'stu-demo-002',
    firstName: 'Cheikh Tidiane',
    lastName: 'Ndiaye-Démo',
    matricule: 'DEMO-2026-002',
    classRoomId: 'cls-3a',
    classSize: 38,
    baseScores: {
      'sub-math': 14.5,
      'sub-fr': 13.75,
      'sub-pc': 14.25,
      'sub-svt': 15.0,
      'sub-hg': 14.0,
      'sub-ang': 13.5,
    },
    statusT1: 'VALIDATED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Travail sérieux et régulier dans l’ensemble des disciplines.',
    directorNote: 'Tableau d’honneur. Bonne préparation pour le BFEM.',
  },
  {
    studentId: 'stu-demo-003',
    firstName: 'Fatou Bintou',
    lastName: 'Sow-Démo',
    matricule: 'DEMO-2026-003',
    classRoomId: 'cls-6b',
    classSize: 42,
    baseScores: {
      'sub-math': 15.25,
      'sub-fr': 16.0,
      'sub-pc': 14.75,
      'sub-svt': 15.5,
      'sub-hg': 16.25,
      'sub-ang': 15.0,
    },
    statusT1: 'LOCKED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Très belle adaptation au collège, élève curieuse et appliquée.',
    directorNote: 'Très bon trimestre. Tableau d’honneur décerné avec encouragements.',
  },
  {
    studentId: 'stu-demo-004',
    firstName: 'Mouhamadou',
    lastName: 'Fall-Démo',
    matricule: 'DEMO-2026-004',
    classRoomId: 'cls-1l',
    classSize: 36,
    baseScores: {
      'sub-math': 10.5,
      'sub-fr': 14.25,
      'sub-pc': 11.0,
      'sub-svt': 12.25,
      'sub-hg': 15.0,
      'sub-ang': 14.5,
    },
    statusT1: 'VALIDATED',
    statusT2: 'DRAFT',
    statusT3: 'DRAFT',
    homeroomNote: 'Solides aptitudes littéraires ; efforts à maintenir en mathématiques.',
    directorNote: 'Encouragements du conseil. Ensemble prometteur.',
  },
  {
    studentId: 'stu-demo-005',
    firstName: 'Aïssatou',
    lastName: 'Ba-Démo',
    matricule: 'DEMO-2026-005',
    classRoomId: 'cls-cm2',
    classSize: 40,
    baseScores: {
      'sub-math': 16.25,
      'sub-fr': 17.0,
      'sub-pc': 15.5,
      'sub-svt': 16.75,
      'sub-hg': 16.0,
      'sub-ang': 15.75,
    },
    statusT1: 'LOCKED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Élève brillante, écriture soignée et excellente compréhension des consignes.',
    directorNote: 'Félicitations. Prête pour le CFEE et l’entrée en 6ème.',
  },
  {
    studentId: 'stu-demo-006',
    firstName: 'Moussa',
    lastName: 'Diop-Démo',
    matricule: 'DEMO-2026-006',
    classRoomId: 'cls-2s',
    classSize: 35,
    baseScores: {
      'sub-math': 11.25,
      'sub-fr': 10.5,
      'sub-pc': 10.75,
      'sub-svt': 11.5,
      'sub-hg': 11.0,
      'sub-ang': 10.25,
    },
    statusT1: 'VALIDATED',
    statusT2: 'DRAFT',
    statusT3: 'DRAFT',
    homeroomNote: 'Résultats justes mais en progrès sur les derniers devoirs surveillés.',
    directorNote: 'Doit approfondir le travail personnel pour consolider les acquis.',
  },
  {
    studentId: 'stu-demo-007',
    firstName: 'Khady',
    lastName: 'Sall-Démo',
    matricule: 'DEMO-2026-007',
    classRoomId: 'cls-ts2',
    classSize: 34,
    baseScores: {
      'sub-math': 14.75,
      'sub-fr': 13.5,
      'sub-pc': 15.25,
      'sub-svt': 14.5,
      'sub-hg': 13.0,
      'sub-ang': 14.0,
    },
    statusT1: 'LOCKED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Bonne maîtrise des matières scientifiques, attitude sérieuse en classe.',
    directorNote: 'Tableau d’honneur. Continuez dans cette dynamique positive.',
  },
  {
    studentId: 'stu-demo-008',
    firstName: 'Oumar',
    lastName: 'Gueye-Démo',
    matricule: 'DEMO-2026-008',
    classRoomId: 'cls-3a',
    classSize: 38,
    baseScores: {
      'sub-math': 8.75,
      'sub-fr': 9.5,
      'sub-pc': 8.25,
      'sub-svt': 9.75,
      'sub-hg': 10.25,
      'sub-ang': 9.0,
    },
    statusT1: 'VALIDATED',
    statusT2: 'DRAFT',
    statusT3: 'DRAFT',
    homeroomNote: 'Lacunes en mathématiques et sciences physiques malgré une bonne volonté.',
    directorNote: 'Avertissement travail. Séances de remédiation recommandées dès la rentrée.',
  },
  {
    studentId: 'stu-demo-009',
    firstName: 'Ndèye Astou',
    lastName: 'Faye-Démo',
    matricule: 'DEMO-2026-009',
    classRoomId: 'cls-6b',
    classSize: 42,
    baseScores: {
      'sub-math': 12.5,
      'sub-fr': 13.25,
      'sub-pc': 12.0,
      'sub-svt': 13.0,
      'sub-hg': 12.75,
      'sub-ang': 13.5,
    },
    statusT1: 'VALIDATED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Ensemble satisfaisant et comportement irréprochable en classe.',
    directorNote: 'Encouragements du conseil de classe.',
  },
  {
    studentId: 'stu-demo-010',
    firstName: 'Serigne',
    lastName: 'Mbaye-Démo',
    matricule: 'DEMO-2026-010',
    classRoomId: 'cls-2s',
    classSize: 35,
    baseScores: {
      'sub-math': 13.5,
      'sub-fr': 11.75,
      'sub-pc': 14.0,
      'sub-svt': 13.25,
      'sub-hg': 12.0,
      'sub-ang': 12.5,
    },
    statusT1: 'VALIDATED',
    statusT2: 'VALIDATED',
    statusT3: 'DRAFT',
    homeroomNote: 'Très bonne implication en sciences, peut gagner en régularité en français.',
    directorNote: 'Encouragements. Bon potentiel pour la série S.',
  },
  {
    studentId: 'stu-demo-011',
    firstName: 'Ramatoulaye',
    lastName: 'Cissé-Démo',
    matricule: 'DEMO-2026-011',
    classRoomId: 'cls-cm2',
    classSize: 40,
    baseScores: {
      'sub-math': 12.25,
      'sub-fr': 13.0,
      'sub-pc': 12.5,
      'sub-svt': 13.25,
      'sub-hg': 12.75,
      'sub-ang': 12.0,
    },
    statusT1: 'DRAFT',
    statusT2: 'DRAFT',
    statusT3: 'DRAFT',
    homeroomNote: 'Élève attentive et appliquée, progrès notables en rédaction.',
    directorNote: 'Encouragements. Poursuivre les révisions régulières.',
  },
  {
    studentId: 'stu-demo-012',
    firstName: 'Pape Abdou',
    lastName: 'Seck-Démo',
    matricule: 'DEMO-2026-012',
    classRoomId: 'cls-1l',
    classSize: 36,
    baseScores: {
      'sub-math': 7.5,
      'sub-fr': 9.25,
      'sub-pc': 7.0,
      'sub-svt': 8.5,
      'sub-hg': 9.5,
      'sub-ang': 8.75,
    },
    statusT1: 'DRAFT',
    statusT2: 'DRAFT',
    statusT3: 'DRAFT',
    homeroomNote: 'Travail insuffisant ce trimestre ; un suivi rapproché avec la famille est engagé.',
    directorNote: 'Avertissement travail. Mobilisation impérative au prochain trimestre.',
  },
];

const TERM_DELTAS: Readonly<Record<GradeTermCode, number>> = {
  T1: 0,
  T2: 0.35,
  T3: 0.65,
};

function subjectAppreciationForScore(score: number): string {
  if (score >= 16) {
    return 'Très bonne maîtrise, travail remarquable.';
  }
  if (score >= 14) {
    return 'Bon trimestre, méthode rigoureuse.';
  }
  if (score >= 12) {
    return 'Ensemble satisfaisant et régulier.';
  }
  if (score >= 10) {
    return 'Résultats convenables, peut encore progresser.';
  }
  return 'Notions à consolider avec un travail plus soutenu.';
}

function clampScore(value: number): number {
  return roundToTwoDecimals(Math.max(0, Math.min(20, value)));
}

function buildSeededReportCards(): StudentReportCardRecord[] {
  const results: StudentReportCardRecord[] = [];
  const classById = new Map(DEMO_CLASSES.map((c) => [c.id, c]));

  for (const term of DEFAULT_GRADE_TERMS) {
    const delta = TERM_DELTAS[term.code];
    const termCards: StudentReportCardRecord[] = [];

    for (const seed of DEMO_STUDENT_SEEDS) {
      const classRoom = classById.get(seed.classRoomId) ?? DEMO_CLASSES[0];
      const fullName = `${seed.firstName} ${seed.lastName}`;
      const subjectResults: SubjectGradeResult[] = DEMO_SUBJECTS.map((subject, idx) => {
        const base = seed.baseScores[subject.id] ?? 12;
        const score = clampScore(base + delta + (idx % 2 === 0 ? 0.1 : -0.05));
        const classAvg = clampScore(12.1 + (idx % 3) * 0.45 + delta * 0.4);
        return {
          id: `res-${term.code.toLowerCase()}-${seed.studentId}-${subject.id}`,
          subjectId: subject.id,
          subjectCode: subject.code,
          subjectName: subject.name,
          coefficient: subject.defaultCoefficient,
          averageScore: score,
          classSubjectAverage: classAvg,
          rankInSubject: null,
          evaluationCount: term.code === 'T1' ? 2 : term.code === 'T2' ? 2 : 1,
          teacherAppreciation: subjectAppreciationForScore(score),
        };
      });

      const totalCoefficients = subjectResults.reduce((sum, s) => sum + s.coefficient, 0);
      const totalWeightedScore = roundToTwoDecimals(
        subjectResults.reduce((sum, s) => sum + (s.averageScore ?? 0) * s.coefficient, 0)
      );
      const generalAverage =
        totalCoefficients > 0 ? roundToTwoDecimals(totalWeightedScore / totalCoefficients) : null;

      const status: BackendReportCardStatus =
        term.code === 'T1' ? seed.statusT1 : term.code === 'T2' ? seed.statusT2 : seed.statusT3;

      termCards.push({
        id: `rc-${term.code.toLowerCase()}-${seed.studentId}`,
        academicYearId: 'ay-2026-2027',
        evaluationPeriodId: term.id,
        termCode: term.code,
        periodName: term.label,
        classRoomId: classRoom.id,
        classRoomCode: classRoom.code,
        classRoomName: classRoom.name,
        studentId: seed.studentId,
        studentMatricule: seed.matricule,
        studentFirstName: seed.firstName,
        studentLastName: seed.lastName,
        studentFullName: fullName,
        studentInitials: buildStudentInitials(fullName),
        gradingScale: 20,
        generalAverage,
        classAverage: 12.65,
        minClassAverage: 7.85,
        maxClassAverage: 17.1,
        rankInClass: 1,
        classSize: seed.classSize,
        tiedRank: false,
        totalWeightedScore,
        totalCoefficients,
        mention: resolveMentionFromAverage(generalAverage),
        councilDecision: resolveCouncilDecisionFromAverage(generalAverage),
        homeroomAppreciation: seed.homeroomNote,
        directorAppreciation: seed.directorNote,
        status,
        computedAtUtc: '2026-11-25T16:30:00Z',
        subjectResults,
      });
    }

    results.push(...recomputeClassRankings(termCards));
  }

  return results;
}

function recomputeClassRankings(
  cards: readonly StudentReportCardRecord[]
): StudentReportCardRecord[] {
  const byClassAndTerm = new Map<string, StudentReportCardRecord[]>();
  for (const card of cards) {
    const key = `${card.termCode}:${card.classRoomId}`;
    const bucket = byClassAndTerm.get(key) ?? [];
    bucket.push(card);
    byClassAndTerm.set(key, bucket);
  }

  const updatedById = new Map<string, StudentReportCardRecord>();

  for (const group of byClassAndTerm.values()) {
    const sorted = [...group].sort((a, b) => (b.generalAverage ?? -1) - (a.generalAverage ?? -1));
    const averages = sorted
      .map((item) => item.generalAverage)
      .filter((val): val is number => val !== null);
    const classAvg =
      averages.length > 0
        ? roundToTwoDecimals(averages.reduce((s, v) => s + v, 0) / averages.length)
        : null;
    const minAvg = averages.length > 0 ? Math.min(...averages) : null;
    const maxAvg = averages.length > 0 ? Math.max(...averages) : null;

    let currentRank = 0;
    for (let i = 0; i < sorted.length; i++) {
      const card = sorted[i];
      const prev = i > 0 ? sorted[i - 1] : null;
      const next = i < sorted.length - 1 ? sorted[i + 1] : null;
      const sameAsPrev = prev !== null && prev.generalAverage === card.generalAverage;
      const sameAsNext = next !== null && next.generalAverage === card.generalAverage;
      if (!sameAsPrev) {
        currentRank = i + 1;
      }

      updatedById.set(card.id, {
        ...card,
        rankInClass: card.generalAverage !== null ? currentRank : null,
        tiedRank: sameAsPrev || sameAsNext,
        classAverage: classAvg,
        minClassAverage: minAvg,
        maxClassAverage: maxAvg,
      });
    }
  }

  return cards.map((card) => updatedById.get(card.id) ?? card);
}

/**
 * Purely fictional grades & report cards dataset for local development mode only (`/dev-login`).
 * Never bundled in production builds (excluded via `app.dev-providers.prod.ts`).
 */
@Injectable({ providedIn: 'root' })
export class GradesDevFallbackService implements GradesDemoFallback {
  private readonly authService = inject(AuthService);

  private reportCards: StudentReportCardRecord[] = buildSeededReportCards();
  private evaluationSequence = 18;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  list(query: GradesListQuery): Observable<GradesPageResult> {
    const termCards = this.reportCards.filter((card) => card.termCode === query.term);

    const search = query.search?.trim().toLowerCase() ?? '';
    const classFilter = query.classRoomId?.trim() ?? '';
    const subjectFilter = query.subjectId?.trim() ?? '';

    const filtered = termCards.filter((card) => {
      if (
        classFilter &&
        classFilter !== 'ALL' &&
        card.classRoomId !== classFilter &&
        card.classRoomCode !== classFilter &&
        card.classRoomName !== classFilter
      ) {
        return false;
      }
      if (
        subjectFilter &&
        subjectFilter !== 'ALL' &&
        !card.subjectResults.some(
          (sub) =>
            sub.subjectId === subjectFilter ||
            sub.subjectCode === subjectFilter ||
            sub.subjectName === subjectFilter
        )
      ) {
        return false;
      }
      if (!search) {
        return true;
      }
      return (
        card.studentFullName.toLowerCase().includes(search) ||
        card.studentMatricule.toLowerCase().includes(search) ||
        card.classRoomName.toLowerCase().includes(search)
      );
    });

    const sorted = [...filtered].sort((a, b) => {
      const avgDiff = (b.generalAverage ?? -1) - (a.generalAverage ?? -1);
      if (avgDiff !== 0) {
        return avgDiff;
      }
      return a.studentFullName.localeCompare(b.studentFullName, 'fr');
    });

    const size = Math.max(1, Math.min(100, Math.trunc(query.size || 8)));
    const totalElements = sorted.length;
    const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
    const requestedPage = Math.max(0, Math.trunc(query.page || 0));
    const safePage = totalPages === 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    const start = safePage * size;
    const content = sorted.slice(start, start + size);

    const kpiScope =
      classFilter && classFilter !== 'ALL'
        ? termCards.filter(
            (card) => card.classRoomId === classFilter || card.classRoomCode === classFilter
          )
        : termCards;
    const kpis = computeKpisFromRecords(kpiScope, query.term, this.evaluationSequence);

    const studentsForEntry: GradeEntryStudentOption[] = DEMO_STUDENT_SEEDS.map((seed) => {
      const cls = DEMO_CLASSES.find((c) => c.id === seed.classRoomId) ?? DEMO_CLASSES[0];
      const fullName = `${seed.firstName} ${seed.lastName}`;
      return {
        studentId: seed.studentId,
        studentFullName: fullName,
        studentInitials: buildStudentInitials(fullName),
        studentMatricule: seed.matricule,
        classRoomId: cls.id,
        classRoomName: cls.name,
      };
    });

    return of({
      content,
      page: safePage,
      size,
      totalElements,
      totalPages,
      first: safePage === 0,
      last: totalPages === 0 || safePage >= totalPages - 1,
      kpis,
      classes: DEMO_CLASSES,
      subjects: DEMO_SUBJECTS,
      terms: DEFAULT_GRADE_TERMS,
      studentsForEntry,
    });
  }

  getReportCard(reportCardId: string): Observable<StudentReportCardRecord> {
    const found = this.reportCards.find((card) => card.id === reportCardId);
    if (!found) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:report-card-not-found',
            title: 'Bulletin introuvable',
            status: 404,
            detail: "Le bulletin demandé n'existe pas pour cette période.",
            requestId: 'req-demo-rc-404',
          })
      );
    }
    return of(found);
  }

  saveEvaluationGrades(input: SaveEvaluationGradesInput): Observable<SaveEvaluationGradesResult> {
    const subject = DEMO_SUBJECTS.find((s) => s.id === input.subjectId);
    const classRoom = DEMO_CLASSES.find((c) => c.id === input.classRoomId);

    if (!subject || !classRoom) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:grade-validation-error',
            title: 'Paramètres invalides',
            status: 400,
            detail: 'La classe ou la matière sélectionnée est introuvable.',
            requestId: 'req-demo-eval-400',
          })
      );
    }

    const scoreByStudent = new Map<string, number>();
    for (const entry of input.entries) {
      if (!Number.isFinite(entry.score) || entry.score < 0 || entry.score > 20) {
        return throwError(
          () =>
            new ProblemDetailError({
              type: 'urn:ecole:problem:grade-out-of-bounds',
              title: 'Note hors barème',
              status: 422,
              detail: 'Chaque note saisie doit être comprise entre 0 et 20.',
              requestId: 'req-demo-eval-422',
              errors: [{ field: 'entries', message: 'Les notes doivent être bornées entre 0 et 20.' }],
            })
        );
      }
      scoreByStudent.set(entry.studentId, clampScore(entry.score));
    }

    this.evaluationSequence += 1;

    const updatedCards = this.reportCards.map((card) => {
      if (card.termCode !== input.termCode || card.classRoomId !== input.classRoomId) {
        return card;
      }
      const newScore = scoreByStudent.get(card.studentId);
      if (newScore === undefined) {
        return card;
      }

      const updatedSubjects = card.subjectResults.map((sub) => {
        if (sub.subjectId !== input.subjectId) {
          return sub;
        }
        const prevCount = Math.max(0, sub.evaluationCount);
        const prevAvg = sub.averageScore ?? newScore;
        const blendedAvg = roundToTwoDecimals((prevAvg * prevCount + newScore) / (prevCount + 1));
        return {
          ...sub,
          coefficient: Math.max(1, Math.min(20, Math.trunc(input.coefficient))),
          averageScore: blendedAvg,
          evaluationCount: prevCount + 1,
          teacherAppreciation: subjectAppreciationForScore(blendedAvg),
        };
      });

      const totalCoefficients = updatedSubjects.reduce((sum, s) => sum + s.coefficient, 0);
      const totalWeightedScore = roundToTwoDecimals(
        updatedSubjects.reduce((sum, s) => sum + (s.averageScore ?? 0) * s.coefficient, 0)
      );
      const generalAverage =
        totalCoefficients > 0 ? roundToTwoDecimals(totalWeightedScore / totalCoefficients) : null;

      return {
        ...card,
        subjectResults: updatedSubjects,
        totalCoefficients,
        totalWeightedScore,
        generalAverage,
        mention: resolveMentionFromAverage(generalAverage),
        councilDecision: resolveCouncilDecisionFromAverage(generalAverage),
        computedAtUtc: new Date().toISOString(),
      };
    });

    this.reportCards = recomputeClassRankings(updatedCards);

    const scores = Array.from(scoreByStudent.values());
    const classAverage =
      scores.length > 0
        ? roundToTwoDecimals(scores.reduce((sum, v) => sum + v, 0) / scores.length)
        : null;

    return of({
      evaluationId: `eval-demo-${this.evaluationSequence}`,
      title: input.title.trim(),
      savedCount: scores.length,
      classAverage,
    });
  }

  generateReportCards(input: GenerateReportCardsInput): Observable<GenerateReportCardsResult> {
    const filterByClass = Boolean(input.classRoomId && input.classRoomId !== 'ALL');
    let generatedCount = 0;

    const updatedCards = this.reportCards.map((card) => {
      if (card.termCode !== input.termCode) {
        return card;
      }
      if (filterByClass && card.classRoomId !== input.classRoomId) {
        return card;
      }
      generatedCount += 1;
      return {
        ...card,
        status: card.status === 'LOCKED' ? ('LOCKED' as const) : ('VALIDATED' as const),
        computedAtUtc: new Date().toISOString(),
      };
    });

    this.reportCards = recomputeClassRankings(updatedCards);

    const targetClassName = filterByClass
      ? (DEMO_CLASSES.find((c) => c.id === input.classRoomId)?.name ?? 'Classe sélectionnée')
      : 'Toutes les classes';

    return of({
      generatedCount,
      termCode: input.termCode,
      classRoomName: targetClassName,
    });
  }
}
