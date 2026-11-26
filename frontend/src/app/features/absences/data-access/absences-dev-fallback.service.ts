import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  AbsenceClassOption,
  AbsenceRecord,
  AbsencesKpiSummary,
  AbsencesListQuery,
  AbsencesPageResult,
  DEFAULT_ABSENCE_CLASSES,
  JustifyAbsenceInput,
  RollCallStudentItem,
  SubmitRollCallInput,
  SubmitRollCallResult,
  buildStudentInitials,
  formatTimeSlotLabel,
  getTodayDakarIsoDate,
} from '../models/absences.model';
import { AbsencesDemoFallback } from './absences-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';

function shiftDakarIsoDate(daysOffset: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + daysOffset);
  return getTodayDakarIsoDate(date);
}

function buildDemoRoster(): Readonly<Record<string, readonly RollCallStudentItem[]>> {
  const entries: readonly {
    readonly studentId: string;
    readonly studentName: string;
    readonly matricule: string;
    readonly classRoomId: string;
    readonly className: string;
  }[] = [
    { studentId: 'stu-demo-001', studentName: 'Aminata Diallo-Démo', matricule: 'DEMO-2026-001', classRoomId: 'cls-demo-ts2', className: 'Terminale S2' },
    { studentId: 'stu-demo-007', studentName: 'Khady Sall-Démo', matricule: 'DEMO-2026-007', classRoomId: 'cls-demo-ts2', className: 'Terminale S2' },
    { studentId: 'stu-demo-013', studentName: 'Ibrahima Kane-Démo', matricule: 'DEMO-2026-013', classRoomId: 'cls-demo-ts2', className: 'Terminale S2' },
    { studentId: 'stu-demo-014', studentName: 'Mariama Bâ-Démo', matricule: 'DEMO-2026-014', classRoomId: 'cls-demo-ts2', className: 'Terminale S2' },
    { studentId: 'stu-demo-015', studentName: 'Abdoulaye Sy-Démo', matricule: 'DEMO-2026-015', classRoomId: 'cls-demo-ts2', className: 'Terminale S2' },

    { studentId: 'stu-demo-004', studentName: 'Mouhamadou Fall-Démo', matricule: 'DEMO-2026-004', classRoomId: 'cls-demo-1l', className: '1ère L' },
    { studentId: 'stu-demo-012', studentName: 'Pape Abdou Seck-Démo', matricule: 'DEMO-2026-012', classRoomId: 'cls-demo-1l', className: '1ère L' },
    { studentId: 'stu-demo-016', studentName: 'Sokhna Diagne-Démo', matricule: 'DEMO-2026-016', classRoomId: 'cls-demo-1l', className: '1ère L' },
    { studentId: 'stu-demo-017', studentName: 'Modou Thiam-Démo', matricule: 'DEMO-2026-017', classRoomId: 'cls-demo-1l', className: '1ère L' },

    { studentId: 'stu-demo-006', studentName: 'Moussa Diop-Démo', matricule: 'DEMO-2026-006', classRoomId: 'cls-demo-2s', className: 'Seconde S' },
    { studentId: 'stu-demo-010', studentName: 'Serigne Mbaye-Démo', matricule: 'DEMO-2026-010', classRoomId: 'cls-demo-2s', className: 'Seconde S' },
    { studentId: 'stu-demo-018', studentName: 'Coumba Ndiaye-Démo', matricule: 'DEMO-2026-018', classRoomId: 'cls-demo-2s', className: 'Seconde S' },
    { studentId: 'stu-demo-019', studentName: 'Bamba Touré-Démo', matricule: 'DEMO-2026-019', classRoomId: 'cls-demo-2s', className: 'Seconde S' },

    { studentId: 'stu-demo-002', studentName: 'Cheikh Tidiane Ndiaye-Démo', matricule: 'DEMO-2026-002', classRoomId: 'cls-demo-3a', className: '3ème A' },
    { studentId: 'stu-demo-008', studentName: 'Oumar Gueye-Démo', matricule: 'DEMO-2026-008', classRoomId: 'cls-demo-3a', className: '3ème A' },
    { studentId: 'stu-demo-020', studentName: 'Binta Sarr-Démo', matricule: 'DEMO-2026-020', classRoomId: 'cls-demo-3a', className: '3ème A' },
    { studentId: 'stu-demo-021', studentName: 'Lamine Diouf-Démo', matricule: 'DEMO-2026-021', classRoomId: 'cls-demo-3a', className: '3ème A' },

    { studentId: 'stu-demo-003', studentName: 'Fatou Bintou Sow-Démo', matricule: 'DEMO-2026-003', classRoomId: 'cls-demo-6b', className: '6ème B' },
    { studentId: 'stu-demo-009', studentName: 'Ndèye Astou Faye-Démo', matricule: 'DEMO-2026-009', classRoomId: 'cls-demo-6b', className: '6ème B' },
    { studentId: 'stu-demo-022', studentName: 'Mamadou Ciss-Démo', matricule: 'DEMO-2026-022', classRoomId: 'cls-demo-6b', className: '6ème B' },

    { studentId: 'stu-demo-005', studentName: 'Aïssatou Ba-Démo', matricule: 'DEMO-2026-005', classRoomId: 'cls-demo-cm2', className: 'CM2 (Primaire)' },
    { studentId: 'stu-demo-011', studentName: 'Ramatoulaye Cissé-Démo', matricule: 'DEMO-2026-011', classRoomId: 'cls-demo-cm2', className: 'CM2 (Primaire)' },
    { studentId: 'stu-demo-023', studentName: 'Youssoupha Wade-Démo', matricule: 'DEMO-2026-023', classRoomId: 'cls-demo-cm2', className: 'CM2 (Primaire)' },
  ];

  const grouped: Record<string, RollCallStudentItem[]> = {};
  for (const item of entries) {
    const list = grouped[item.classRoomId] ?? [];
    list.push({
      ...item,
      studentInitials: buildStudentInitials(item.studentName),
    });
    grouped[item.classRoomId] = list;
  }
  return grouped;
}

function buildInitialDemoAbsences(): AbsenceRecord[] {
  const today = getTodayDakarIsoDate();
  const yesterday = shiftDakarIsoDate(-1);
  const twoDaysAgo = shiftDakarIsoDate(-2);
  const threeDaysAgo = shiftDakarIsoDate(-3);

  return [
    {
      id: 'abs-demo-001',
      sessionId: 'sess-demo-001',
      studentId: 'stu-demo-007',
      studentName: 'Khady Sall-Démo',
      studentInitials: 'KS',
      matricule: 'DEMO-2026-007',
      classRoomId: 'cls-demo-ts2',
      className: 'Terminale S2',
      sessionDate: today,
      recordedAtUtc: `${today}T08:15:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Mathématiques',
      teacherName: 'M. Ousmane Dieng',
      rawStatus: 'ABSENT',
      status: 'UNJUSTIFIED',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Absente à l’appel de 8h — parent non encore joint',
    },
    {
      id: 'abs-demo-002',
      sessionId: 'sess-demo-001',
      studentId: 'stu-demo-013',
      studentName: 'Ibrahima Kane-Démo',
      studentInitials: 'IK',
      matricule: 'DEMO-2026-013',
      classRoomId: 'cls-demo-ts2',
      className: 'Terminale S2',
      sessionDate: today,
      recordedAtUtc: `${today}T08:22:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Mathématiques',
      teacherName: 'M. Ousmane Dieng',
      rawStatus: 'LATE',
      status: 'LATE',
      lateMinutes: 15,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Retard transport scolaire (avenue Bourguiba)',
    },
    {
      id: 'abs-demo-003',
      sessionId: 'sess-demo-002',
      studentId: 'stu-demo-002',
      studentName: 'Cheikh Tidiane Ndiaye-Démo',
      studentInitials: 'CN',
      matricule: 'DEMO-2026-002',
      classRoomId: 'cls-demo-3a',
      className: '3ème A',
      sessionDate: today,
      recordedAtUtc: `${today}T08:10:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Français',
      teacherName: 'Mme Marième Sène',
      rawStatus: 'ABSENT',
      status: 'JUSTIFIED',
      lateMinutes: null,
      justified: true,
      justificationReason: 'Consultation médicale (certificat déposé au secrétariat)',
      justificationDocumentRef: 'CERT-MED-2026-041',
      justifiedAtUtc: `${today}T09:40:00Z`,
      notes: 'Retour prévu demain matin',
    },
    {
      id: 'abs-demo-004',
      sessionId: 'sess-demo-003',
      studentId: 'stu-demo-004',
      studentName: 'Mouhamadou Fall-Démo',
      studentInitials: 'MF',
      matricule: 'DEMO-2026-004',
      classRoomId: 'cls-demo-1l',
      className: '1ère L',
      sessionDate: today,
      recordedAtUtc: `${today}T08:18:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Histoire-Géographie',
      teacherName: 'M. Babacar Ndao',
      rawStatus: 'ABSENT',
      status: 'UNJUSTIFIED',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Alerte SMS tuteur programmée',
    },
    {
      id: 'abs-demo-005',
      sessionId: 'sess-demo-004',
      studentId: 'stu-demo-006',
      studentName: 'Moussa Diop-Démo',
      studentInitials: 'MD',
      matricule: 'DEMO-2026-006',
      classRoomId: 'cls-demo-2s',
      className: 'Seconde S',
      sessionDate: today,
      recordedAtUtc: `${today}T14:12:00Z`,
      timeSlot: 'AFTERNOON',
      slotLabel: 'Après-midi (14h–17h)',
      subjectName: 'Physique-Chimie',
      teacherName: 'M. Souleymane Gaye',
      rawStatus: 'LATE',
      status: 'LATE',
      lateMinutes: 20,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Arrivé après le début des TP',
    },
    {
      id: 'abs-demo-006',
      sessionId: 'sess-demo-005',
      studentId: 'stu-demo-009',
      studentName: 'Ndèye Astou Faye-Démo',
      studentInitials: 'NF',
      matricule: 'DEMO-2026-009',
      classRoomId: 'cls-demo-6b',
      className: '6ème B',
      sessionDate: yesterday,
      recordedAtUtc: `${yesterday}T08:08:00Z`,
      timeSlot: 'FULL_DAY',
      slotLabel: 'Journée complète',
      subjectName: 'Vie scolaire',
      teacherName: 'Mme Awa Cissé',
      rawStatus: 'ABSENT',
      status: 'JUSTIFIED',
      lateMinutes: null,
      justified: true,
      justificationReason: 'Événement familial signalé par le tuteur légal',
      justificationDocumentRef: 'MOT-FAM-2026-118',
      justifiedAtUtc: `${yesterday}T11:25:00Z`,
      notes: 'Motif signé dans le carnet de correspondance',
    },
    {
      id: 'abs-demo-007',
      sessionId: 'sess-demo-006',
      studentId: 'stu-demo-008',
      studentName: 'Oumar Gueye-Démo',
      studentInitials: 'OG',
      matricule: 'DEMO-2026-008',
      classRoomId: 'cls-demo-3a',
      className: '3ème A',
      sessionDate: yesterday,
      recordedAtUtc: `${yesterday}T14:05:00Z`,
      timeSlot: 'AFTERNOON',
      slotLabel: 'Après-midi (14h–17h)',
      subjectName: 'Anglais',
      teacherName: 'Mme Fatimata Ba',
      rawStatus: 'ABSENT',
      status: 'UNJUSTIFIED',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Sortie non autorisée signalée par le surveillant',
    },
    {
      id: 'abs-demo-008',
      sessionId: 'sess-demo-007',
      studentId: 'stu-demo-005',
      studentName: 'Aïssatou Ba-Démo',
      studentInitials: 'AB',
      matricule: 'DEMO-2026-005',
      classRoomId: 'cls-demo-cm2',
      className: 'CM2 (Primaire)',
      sessionDate: yesterday,
      recordedAtUtc: `${yesterday}T08:25:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Calcul',
      teacherName: 'M. Ibrahima Sarr',
      rawStatus: 'LATE',
      status: 'LATE',
      lateMinutes: 10,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'Accompagnée par son père à l’accueil',
    },
    {
      id: 'abs-demo-009',
      sessionId: 'sess-demo-008',
      studentId: 'stu-demo-010',
      studentName: 'Serigne Mbaye-Démo',
      studentInitials: 'SM',
      matricule: 'DEMO-2026-010',
      classRoomId: 'cls-demo-2s',
      className: 'Seconde S',
      sessionDate: twoDaysAgo,
      recordedAtUtc: `${twoDaysAgo}T08:14:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'SVT',
      teacherName: 'M. Cheikh Diop',
      rawStatus: 'ABSENT',
      status: 'UNJUSTIFIED',
      lateMinutes: null,
      justified: false,
      justificationReason: null,
      justificationDocumentRef: null,
      justifiedAtUtc: null,
      notes: 'En attente de justificatif parental',
    },
    {
      id: 'abs-demo-010',
      sessionId: 'sess-demo-009',
      studentId: 'stu-demo-001',
      studentName: 'Aminata Diallo-Démo',
      studentInitials: 'AD',
      matricule: 'DEMO-2026-001',
      classRoomId: 'cls-demo-ts2',
      className: 'Terminale S2',
      sessionDate: threeDaysAgo,
      recordedAtUtc: `${threeDaysAgo}T08:05:00Z`,
      timeSlot: 'MORNING',
      slotLabel: 'Matin (08h–12h)',
      subjectName: 'Philosophie',
      teacherName: 'Mme Rokhaya Mbaye',
      rawStatus: 'EXCUSED',
      status: 'JUSTIFIED',
      lateMinutes: null,
      justified: true,
      justificationReason: 'Dispense officielle olympiades de mathématiques',
      justificationDocumentRef: 'CONV-OLYMP-2026-09',
      justifiedAtUtc: `${threeDaysAgo}T07:50:00Z`,
      notes: 'Autorisation de la direction',
    },
  ];
}

/**
 * Purely fictional attendance/absence dataset for local development mode only (`/dev-login` without backend).
 * Never bundled in production builds (excluded via `app.dev-providers.prod.ts` file replacement).
 */
@Injectable({ providedIn: 'root' })
export class AbsencesDevFallbackService implements AbsencesDemoFallback {
  private readonly authService = inject(AuthService);

  private readonly classes: readonly AbsenceClassOption[] = [...DEFAULT_ABSENCE_CLASSES];
  private readonly rosterByClass = buildDemoRoster();
  private records: AbsenceRecord[] = buildInitialDemoAbsences();
  private sessionSequence = 20;
  private recordSequence = 50;
  private baselineTotalChecks = 240;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  list(query: AbsencesListQuery): Observable<AbsencesPageResult> {
    const search = query.search?.trim().toLowerCase() ?? '';
    const className = query.className?.trim() ?? '';
    const status = query.status ?? 'ALL';
    const dateFilter = query.date?.trim() ?? '';

    const filtered = this.records.filter((item) => {
      if (className && item.className !== className && item.classRoomId !== className) {
        return false;
      }
      if (status !== 'ALL' && item.status !== status) {
        return false;
      }
      if (dateFilter && item.sessionDate !== dateFilter) {
        return false;
      }
      if (!search) {
        return true;
      }
      return (
        item.studentName.toLowerCase().includes(search) ||
        item.matricule.toLowerCase().includes(search) ||
        item.className.toLowerCase().includes(search) ||
        (item.justificationReason?.toLowerCase().includes(search) ?? false) ||
        (item.justificationDocumentRef?.toLowerCase().includes(search) ?? false) ||
        (item.notes?.toLowerCase().includes(search) ?? false)
      );
    });

    const sorted = [...filtered].sort((a, b) => b.recordedAtUtc.localeCompare(a.recordedAtUtc));

    const size = Math.max(1, Math.min(100, Math.trunc(query.size || 8)));
    const totalElements = sorted.length;
    const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
    const requestedPage = Math.max(0, Math.trunc(query.page || 0));
    const safePage = totalPages === 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    const start = safePage * size;
    const content = sorted.slice(start, start + size);

    return of({
      content,
      page: safePage,
      size,
      totalElements,
      totalPages,
      first: safePage === 0,
      last: totalPages === 0 || safePage >= totalPages - 1,
      kpis: this.buildDemoKpis(),
      availableClasses: this.classes,
      rollCallRosterByClass: this.rosterByClass,
    });
  }

  submitRollCall(input: SubmitRollCallInput): Observable<SubmitRollCallResult> {
    const roster = this.rosterByClass[input.classRoomId] ?? [];
    if (roster.length === 0) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:class-not-found',
            title: 'Classe introuvable',
            status: 404,
            detail: "La classe sélectionnée ne contient aucun élève inscrit pour l'appel.",
            requestId: 'req-demo-abs-404',
          })
      );
    }

    this.sessionSequence += 1;
    const sessionId = `sess-demo-${this.sessionSequence}`;
    const nowIso = new Date().toISOString();
    const slotLabel = formatTimeSlotLabel(input.timeSlot);
    const rosterMap = new Map(roster.map((student) => [student.studentId, student]));

    let presentCount = 0;
    let absentCount = 0;
    let lateCount = 0;
    const createdRecords: AbsenceRecord[] = [];

    for (const entry of input.entries) {
      this.baselineTotalChecks += 1;
      if (entry.status === 'PRESENT') {
        presentCount += 1;
        continue;
      }

      const student = rosterMap.get(entry.studentId);
      if (!student) {
        continue;
      }

      this.recordSequence += 1;
      if (entry.status === 'ABSENT') {
        absentCount += 1;
        createdRecords.push({
          id: `abs-demo-${this.recordSequence}`,
          sessionId,
          studentId: student.studentId,
          studentName: student.studentName,
          studentInitials: student.studentInitials,
          matricule: student.matricule,
          classRoomId: student.classRoomId,
          className: student.className,
          sessionDate: input.sessionDate,
          recordedAtUtc: nowIso,
          timeSlot: input.timeSlot,
          slotLabel,
          subjectName: null,
          teacherName: this.authService.user()?.fullName ?? 'Enseignant Démo',
          rawStatus: 'ABSENT',
          status: 'UNJUSTIFIED',
          lateMinutes: null,
          justified: false,
          justificationReason: null,
          justificationDocumentRef: null,
          justifiedAtUtc: null,
          notes: entry.notes?.trim() || input.notes?.trim() || 'Absence saisie lors de l’appel rapide',
        });
      } else {
        lateCount += 1;
        const minutes = entry.lateMinutes && entry.lateMinutes > 0 ? Math.trunc(entry.lateMinutes) : 15;
        createdRecords.push({
          id: `abs-demo-${this.recordSequence}`,
          sessionId,
          studentId: student.studentId,
          studentName: student.studentName,
          studentInitials: student.studentInitials,
          matricule: student.matricule,
          classRoomId: student.classRoomId,
          className: student.className,
          sessionDate: input.sessionDate,
          recordedAtUtc: nowIso,
          timeSlot: input.timeSlot,
          slotLabel,
          subjectName: null,
          teacherName: this.authService.user()?.fullName ?? 'Enseignant Démo',
          rawStatus: 'LATE',
          status: 'LATE',
          lateMinutes: minutes,
          justified: false,
          justificationReason: null,
          justificationDocumentRef: null,
          justifiedAtUtc: null,
          notes: entry.notes?.trim() || input.notes?.trim() || `Retard de ${minutes} min saisi à l’appel`,
        });
      }
    }

    if (createdRecords.length > 0) {
      this.records = [...createdRecords, ...this.records];
    }

    return of({
      sessionId,
      className: input.className,
      sessionDate: input.sessionDate,
      timeSlot: input.timeSlot,
      presentCount,
      absentCount,
      lateCount,
    });
  }

  justifyRecord(input: JustifyAbsenceInput): Observable<AbsenceRecord> {
    const existing = this.records.find((item) => item.id === input.recordId);
    if (!existing) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:attendance-record-not-found',
            title: 'Pointage introuvable',
            status: 404,
            detail: "L'absence sélectionnée est introuvable.",
            requestId: 'req-demo-justify-404',
          })
      );
    }

    const trimmedReason = input.reason.trim();
    if (!trimmedReason) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:validation-error',
            title: 'Motif requis',
            status: 422,
            detail: 'Le motif de justification est obligatoire.',
            requestId: 'req-demo-justify-422',
            errors: [{ field: 'reason', message: 'Veuillez renseigner un motif de justification.' }],
          })
      );
    }

    const updated: AbsenceRecord = {
      ...existing,
      status: existing.rawStatus === 'LATE' ? 'LATE' : 'JUSTIFIED',
      justified: true,
      justificationReason: trimmedReason,
      justificationDocumentRef: input.documentRef?.trim() || existing.justificationDocumentRef,
      justifiedAtUtc: new Date().toISOString(),
    };

    this.records = this.records.map((item) => (item.id === existing.id ? updated : item));
    return of(updated);
  }

  private buildDemoKpis(): AbsencesKpiSummary {
    const todayIso = getTodayDakarIsoDate();
    const totalAbsences = this.records.filter((item) => item.status !== 'LATE').length;
    const todayAbsencesCount = this.records.filter(
      (item) => item.sessionDate === todayIso && item.status !== 'LATE'
    ).length;
    const unjustifiedCount = this.records.filter((item) => item.status === 'UNJUSTIFIED').length;
    const justifiedCount = this.records.filter((item) => item.status === 'JUSTIFIED').length;
    const lateRecords = this.records.filter((item) => item.status === 'LATE');
    const lateCount = lateRecords.length;
    const totalLateMinutes = lateRecords.reduce((sum, item) => sum + (item.lateMinutes ?? 0), 0);

    const presentOrLate = Math.max(0, this.baselineTotalChecks - totalAbsences);
    const attendanceRatePercent =
      this.baselineTotalChecks > 0
        ? Math.round((presentOrLate / this.baselineTotalChecks) * 1000) / 10
        : 100;

    return {
      attendanceRatePercent,
      attendanceRateTrendLabel: '+0,6 pt',
      attendanceRateSubLabel: 'vs semaine précédente',
      todayAbsencesCount,
      todayAbsencesTrendLabel: todayAbsencesCount > 0 ? "Aujourd'hui" : 'Aucune absence',
      todayAbsencesSubLabel: `${totalAbsences} absences cette semaine`,
      unjustifiedCount,
      unjustifiedTrendLabel: unjustifiedCount > 0 ? 'À régulariser' : 'Tout justifié',
      unjustifiedSubLabel: `${justifiedCount} déjà justifiées`,
      lateCount,
      lateTrendLabel: totalLateMinutes > 0 ? `${totalLateMinutes} min cumulées` : '0 min',
      lateSubLabel: 'sur les derniers appels',
    };
  }
}
