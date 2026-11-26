import { Injectable, inject } from '@angular/core';
import { Observable, from, of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ProblemDetailError } from '../../../core/models/problem-detail.model';
import {
  CreateImportJobInput,
  ImportFileDownload,
  ImportJobDto,
  ImportJobRowDto,
  ImportJobRowsPage,
  ImportSourceFormat,
  ImportTemplateDto,
  ImportType,
  ListImportRowsQuery,
  detectFileExtensionFormat,
  mapJobRowDtoToPreviewRow,
} from '../models/import.model';
import { ImportDemoFallback } from './import-demo-fallback.token';

const DEV_DEMO_TOKEN = 'demo-memory-token';
const SENEGAL_E164_PHONE_REGEX = /^\+221\d{9}$/;

interface StoredDemoJob {
  job: ImportJobDto;
  rows: ImportJobRowDto[];
}

const DEMO_TEMPLATES: readonly ImportTemplateDto[] = [
  {
    importType: 'STUDENTS',
    titleFr: 'Import massif des élèves',
    descriptionFr:
      'Création ou mise à jour contrôlée des dossiers élèves avec affectation optionnelle en classe.',
    columns: [
      { key: 'matricule', labelFr: 'Matricule (optionnel)', required: false, exampleValue: 'DEMO-2026-101', aliases: ['student_matricule', 'code_eleve'] },
      { key: 'first_name', labelFr: 'Prénom', required: true, exampleValue: 'Awa', aliases: ['prenom', 'prenoms'] },
      { key: 'last_name', labelFr: 'Nom', required: true, exampleValue: 'Ndiaye-Démo', aliases: ['nom', 'nom_de_famille'] },
      { key: 'gender', labelFr: 'Genre (MALE/FEMALE/OTHER)', required: true, exampleValue: 'FEMALE', aliases: ['sexe', 'genre'] },
      { key: 'birth_date', labelFr: 'Date de naissance (YYYY-MM-DD)', required: true, exampleValue: '2014-05-12', aliases: ['date_naissance'] },
      { key: 'phone', labelFr: 'Téléphone tuteur E.164 (+221...)', required: false, exampleValue: '+221000000001', aliases: ['telephone', 'telephone_tuteur', 'guardian_phone'] },
      { key: 'class_code', labelFr: 'Code classe', required: false, exampleValue: '6E-A', aliases: ['classe', 'code_classe'] },
    ],
  },
  {
    importType: 'FAMILIES_GUARDIANS',
    titleFr: 'Import massif des familles, parents et tuteurs',
    descriptionFr: 'Création des tuteurs légaux et rattachement aux dossiers élèves.',
    columns: [
      { key: 'guardian_first_name', labelFr: 'Prénom du tuteur', required: true, exampleValue: 'Fatou', aliases: ['prenom_tuteur', 'prenom'] },
      { key: 'guardian_last_name', labelFr: 'Nom du tuteur', required: true, exampleValue: 'Ndiaye-Démo', aliases: ['nom_tuteur', 'nom'] },
      { key: 'guardian_phone', labelFr: 'Téléphone E.164 (+221...)', required: true, exampleValue: '+221000000002', aliases: ['telephone', 'phone'] },
      { key: 'student_matricule', labelFr: 'Matricule élève', required: false, exampleValue: 'DEMO-2026-101', aliases: ['matricule'] },
    ],
  },
  {
    importType: 'ENROLLMENTS',
    titleFr: 'Import massif des inscriptions aux classes',
    descriptionFr: "Affectation d'élèves existants dans les classes de l'année scolaire.",
    columns: [
      { key: 'student_matricule', labelFr: 'Matricule élève', required: true, exampleValue: 'DEMO-2026-101', aliases: ['matricule'] },
      { key: 'class_code', labelFr: 'Code de la classe', required: true, exampleValue: '6E-A', aliases: ['classe', 'code_classe'] },
    ],
  },
  {
    importType: 'TEACHERS',
    titleFr: 'Import massif des enseignants',
    descriptionFr: 'Création ou mise à jour des enseignants avec taux horaire en FCFA entiers.',
    columns: [
      { key: 'employee_code', labelFr: 'Code employé', required: true, exampleValue: 'ENS-DEMO-001', aliases: ['code_employe'] },
      { key: 'first_name', labelFr: 'Prénom', required: true, exampleValue: 'Cheikh', aliases: ['prenom'] },
      { key: 'last_name', labelFr: 'Nom', required: true, exampleValue: 'Fall-Démo', aliases: ['nom'] },
      { key: 'phone', labelFr: 'Téléphone E.164 (+221...)', required: true, exampleValue: '+221000000003', aliases: ['telephone'] },
      { key: 'hourly_rate_xof', labelFr: 'Taux horaire FCFA', required: false, exampleValue: '5000', aliases: ['taux_horaire_fcfa'] },
    ],
  },
  {
    importType: 'GRADES',
    titleFr: "Import massif des notes d'évaluation",
    descriptionFr: 'Saisie en lot des notes pour une évaluation cible sur période ouverte.',
    columns: [
      { key: 'student_matricule', labelFr: 'Matricule élève', required: true, exampleValue: 'DEMO-2026-101', aliases: ['matricule'] },
      { key: 'attendance_status', labelFr: 'Statut', required: false, exampleValue: 'PRESENT', aliases: ['presence'] },
      { key: 'score', labelFr: 'Note', required: false, exampleValue: '15.5', aliases: ['note'] },
      { key: 'comment', labelFr: 'Appréciation', required: false, exampleValue: 'Bon travail', aliases: ['commentaire'] },
    ],
  },
  {
    importType: 'ATTENDANCE',
    titleFr: 'Import massif des présences',
    descriptionFr: "Pointage en lot des élèves pour une séance d'appel.",
    columns: [
      { key: 'student_matricule', labelFr: 'Matricule élève', required: true, exampleValue: 'DEMO-2026-101', aliases: ['matricule'] },
      { key: 'status', labelFr: 'Statut (PRESENT/LATE/ABSENT/EXCUSED)', required: true, exampleValue: 'PRESENT', aliases: ['statut'] },
      { key: 'late_minutes', labelFr: 'Minutes de retard', required: false, exampleValue: '', aliases: ['minutes_retard'] },
    ],
  },
];

function escapeCsvCell(raw: string, delimiter = ';'): string {
  const safe = /^[=+\-@\t\r]/.test(raw) ? `'${raw}` : raw;
  if (
    safe.includes(delimiter) ||
    safe.includes('"') ||
    safe.includes('\n') ||
    safe.includes('\r')
  ) {
    return `"${safe.replace(/"/g, '""')}"`;
  }
  return safe;
}

function normalizeHeader(header: string): string {
  return header
    .replace(/^\uFEFF/, '')
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

function splitCsvLine(line: string, delimiter: string): string[] {
  const cells: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"' && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        current += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === delimiter) {
      cells.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  cells.push(current.trim());
  return cells;
}

function buildFictionalDemoRows(jobId: string): ImportJobRowDto[] {
  const specs: readonly {
    readonly rowNumber: number;
    readonly matricule: string;
    readonly firstName: string;
    readonly lastName: string;
    readonly classCode: string;
    readonly phone: string;
    readonly rowStatus: ImportJobRowDto['rowStatus'];
    readonly plannedAction: ImportJobRowDto['plannedAction'];
    readonly errorCode: string | null;
    readonly errorField: string | null;
    readonly errorMessage: string | null;
  }[] = [
    {
      rowNumber: 2,
      matricule: 'DEMO-2026-101',
      firstName: 'Aminata',
      lastName: 'Diallo-Démo',
      classCode: '6ème A',
      phone: '+221000000001',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
    {
      rowNumber: 3,
      matricule: 'DEMO-2026-102',
      firstName: 'Cheikh Tidiane',
      lastName: 'Ndiaye-Démo',
      classCode: '3ème A',
      phone: '+221000000002',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
    {
      rowNumber: 4,
      matricule: 'DEMO-2026-103',
      firstName: 'Fatou Bintou',
      lastName: 'Sow-Démo',
      classCode: 'CM2 (Primaire)',
      phone: '+221000000003',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
    {
      rowNumber: 5,
      matricule: 'DEMO-2026-104',
      firstName: 'Mouhamadou',
      lastName: 'Fall-Démo',
      classCode: 'Seconde S',
      phone: '7712345',
      rowStatus: 'ERROR',
      plannedAction: 'ERROR',
      errorCode: 'INVALID_PHONE_E164',
      errorField: 'phone',
      errorMessage: 'Format téléphone invalide : attendu +221 suivi de 9 chiffres.',
    },
    {
      rowNumber: 6,
      matricule: 'DEMO-2026-105',
      firstName: 'Aïssatou',
      lastName: 'Ba-Démo',
      classCode: 'Terminale S2',
      phone: '+221000000005',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
    {
      rowNumber: 7,
      matricule: 'DEMO-2026-106',
      firstName: 'Moussa',
      lastName: '',
      classCode: '6ème B',
      phone: '+221000000006',
      rowStatus: 'ERROR',
      plannedAction: 'ERROR',
      errorCode: 'REQUIRED_FIELD_MISSING',
      errorField: 'last_name',
      errorMessage: 'Le nom de famille est obligatoire pour créer le dossier élève.',
    },
    {
      rowNumber: 8,
      matricule: 'DEMO-2026-107',
      firstName: 'Ndèye Astou',
      lastName: 'Faye-Démo',
      classCode: '1ère L',
      phone: '+221000000007',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
    {
      rowNumber: 9,
      matricule: 'DEMO-2026-108',
      firstName: 'Oumar',
      lastName: 'Gueye-Démo',
      classCode: '3ème A',
      phone: '+221000000008',
      rowStatus: 'VALID',
      plannedAction: 'CREATE',
      errorCode: null,
      errorField: null,
      errorMessage: null,
    },
  ];

  return specs.map((item) => ({
    id: `${jobId}-row-${item.rowNumber}`,
    rowNumber: item.rowNumber,
    naturalKey: item.matricule,
    rowStatus: item.rowStatus,
    plannedAction: item.plannedAction,
    appliedAction: null,
    errorCode: item.errorCode,
    errorField: item.errorField,
    errorMessage: item.errorMessage,
    targetEntityType: 'STUDENT',
    targetEntityId: null,
    rowPreviewJson: JSON.stringify({
      matricule: item.matricule,
      first_name: item.firstName,
      last_name: item.lastName,
      class_code: item.classCode,
      phone: item.phone,
    }),
  }));
}

/**
 * Purely fictional development-only fallback for `/import` when `/dev-login` is active
 * without a running backend. Never bundled in production (stripped via `app.dev-providers.prod.ts`).
 */
@Injectable({ providedIn: 'root' })
export class ImportDevFallbackService implements ImportDemoFallback {
  private readonly authService = inject(AuthService);
  private readonly jobs = new Map<string, StoredDemoJob>();
  private sequence = 0;

  isDemoSessionActive(): boolean {
    return this.authService.token() === DEV_DEMO_TOKEN;
  }

  listTemplates(): Observable<readonly ImportTemplateDto[]> {
    return of(DEMO_TEMPLATES);
  }

  downloadTemplate(importType: ImportType): Observable<ImportFileDownload> {
    const template =
      DEMO_TEMPLATES.find((item) => item.importType === importType) ?? DEMO_TEMPLATES[0];
    const headers = template.columns.map((c) => escapeCsvCell(c.key)).join(';');
    const example = template.columns.map((c) => escapeCsvCell(c.exampleValue)).join(';');
    const secondExample =
      template.importType === 'STUDENTS'
        ? '\r\nDEMO-2026-102;Moussa;Diop-Démo;MALE;2013-09-21;+221000000002;3E-A'
        : '';
    const csvText = `\uFEFF${headers}\r\n${example}${secondExample}\r\n`;
    const blob = new Blob([csvText], { type: 'text/csv; charset=UTF-8' });
    return of({
      blob,
      fileName: `modele-import-${importType.toLowerCase()}.csv`,
      contentType: 'text/csv; charset=UTF-8',
    });
  }

  createImportJob(input: CreateImportJobInput): Observable<ImportJobDto> {
    return from(this.analyzeFileAsync(input));
  }

  getJob(jobId: string): Observable<ImportJobDto> {
    const stored = this.jobs.get(jobId);
    if (!stored) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: "Lot d'import introuvable",
            status: 404,
            detail: "Le lot d'import demandé n'existe pas ou n'est plus disponible.",
            requestId: 'req-demo-import-404',
          })
      );
    }
    return of(stored.job);
  }

  listJobRows(jobId: string, query: ListImportRowsQuery = {}): Observable<ImportJobRowsPage> {
    const stored = this.jobs.get(jobId);
    if (!stored) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: "Lot d'import introuvable",
            status: 404,
            detail: "Impossible de charger les lignes de ce lot d'import.",
            requestId: 'req-demo-import-rows-404',
          })
      );
    }

    const filtered = stored.rows.filter((row) => {
      if (query.rowStatus && row.rowStatus !== query.rowStatus) {
        return false;
      }
      if (query.plannedAction && row.plannedAction !== query.plannedAction) {
        return false;
      }
      return true;
    });

    const size = Math.max(1, Math.min(200, Math.trunc(query.size ?? 50)));
    const totalElements = filtered.length;
    const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size);
    const page = totalPages === 0 ? 0 : Math.min(Math.max(0, Math.trunc(query.page ?? 0)), totalPages - 1);
    const start = page * size;
    const content = filtered.slice(start, start + size).map((row) => mapJobRowDtoToPreviewRow(row));

    return of({
      content,
      page,
      size,
      totalElements,
      totalPages,
      first: page === 0,
      last: totalPages === 0 || page >= totalPages - 1,
    });
  }

  executeJob(jobId: string): Observable<ImportJobDto> {
    const stored = this.jobs.get(jobId);
    if (!stored) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: "Lot d'import introuvable",
            status: 404,
            detail: "Le lot d'import demandé est introuvable.",
            requestId: 'req-demo-import-exec-404',
          })
      );
    }

    const nowIso = new Date().toISOString();
    let createdCount = 0;
    let skippedCount = 0;

    const updatedRows: ImportJobRowDto[] = stored.rows.map((row) => {
      if (row.rowStatus === 'VALID') {
        createdCount += 1;
        return { ...row, rowStatus: 'CREATED', appliedAction: 'CREATE' };
      }
      if (row.rowStatus === 'DUPLICATE') {
        skippedCount += 1;
        return { ...row, rowStatus: 'SKIPPED', appliedAction: 'SKIP_DUPLICATE' };
      }
      return row;
    });

    const nextStatus =
      stored.job.errorRows > 0 ? 'COMPLETED_WITH_ERRORS' : 'COMPLETED';

    const updatedJob: ImportJobDto = {
      ...stored.job,
      status: nextStatus,
      createdCount,
      updatedCount: 0,
      skippedCount,
      executedAt: nowIso,
      executedBy: this.authService.user()?.id ?? 'usr-demo-director',
    };

    this.jobs.set(jobId, { job: updatedJob, rows: updatedRows });
    return of(updatedJob);
  }

  cancelJob(jobId: string, reason: string): Observable<ImportJobDto> {
    const stored = this.jobs.get(jobId);
    if (!stored) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: "Lot d'import introuvable",
            status: 404,
            detail: "Impossible d'annuler un lot introuvable.",
            requestId: 'req-demo-import-cancel-404',
          })
      );
    }

    const cancelledJob: ImportJobDto = {
      ...stored.job,
      status: 'CANCELLED',
      cancelledAt: new Date().toISOString(),
      cancelledBy: this.authService.user()?.id ?? 'usr-demo-director',
      cancellationReason: reason.trim(),
    };
    this.jobs.set(jobId, { job: cancelledJob, rows: stored.rows });
    return of(cancelledJob);
  }

  downloadErrorReport(jobId: string): Observable<ImportFileDownload> {
    const stored = this.jobs.get(jobId);
    if (!stored) {
      return throwError(
        () =>
          new ProblemDetailError({
            type: 'urn:ecole:problem:not-found',
            title: "Rapport d'erreurs introuvable",
            status: 404,
            detail: "Aucun rapport d'erreurs disponible pour ce lot.",
            requestId: 'req-demo-import-errors-404',
          })
      );
    }

    const errorRows = stored.rows.filter((r) => r.rowStatus === 'ERROR');
    const lines = [
      'row_number;natural_key;row_status;planned_action;error_code;error_field;error_message',
      ...errorRows.map((row) =>
        [
          String(row.rowNumber),
          escapeCsvCell(row.naturalKey ?? ''),
          escapeCsvCell(row.rowStatus),
          escapeCsvCell(row.plannedAction),
          escapeCsvCell(row.errorCode ?? ''),
          escapeCsvCell(row.errorField ?? ''),
          escapeCsvCell(row.errorMessage ?? ''),
        ].join(';')
      ),
    ];

    const blob = new Blob([`\uFEFF${lines.join('\r\n')}\r\n`], {
      type: 'text/csv; charset=UTF-8',
    });

    return of({
      blob,
      fileName: `erreurs-${stored.job.jobNumber.toLowerCase()}.csv`,
      contentType: 'text/csv; charset=UTF-8',
    });
  }

  private async analyzeFileAsync(input: CreateImportJobInput): Promise<ImportJobDto> {
    this.sequence += 1;
    const padded = String(this.sequence).padStart(4, '0');
    const jobId = `job-demo-${padded}`;
    const jobNumber = `IMP-2026-${padded}`;
    const sourceFormat: ImportSourceFormat =
      detectFileExtensionFormat(input.file.name) ?? 'CSV';
    const validationMode = input.validationMode ?? 'PARTIAL_SKIP_ERRORS';
    const duplicateStrategy = input.duplicateStrategy ?? 'SKIP_DUPLICATES';

    let rows: ImportJobRowDto[];
    if (sourceFormat === 'CSV') {
      const content = await this.readFileAsText(input.file);
      rows = this.parseCsvToDemoRows(jobId, content);
    } else {
      rows = buildFictionalDemoRows(jobId);
    }

    const validRows = rows.filter((r) => r.rowStatus === 'VALID').length;
    const errorRows = rows.filter((r) => r.rowStatus === 'ERROR').length;
    const duplicateRows = rows.filter((r) => r.rowStatus === 'DUPLICATE').length;
    const nowIso = new Date().toISOString();

    const blockedByStrict =
      validationMode === 'STRICT_ALL_OR_NOTHING' && errorRows > 0;
    const status: ImportJobDto['status'] =
      rows.length === 0 || validRows === 0 || blockedByStrict
        ? 'VALIDATION_FAILED'
        : 'PREVIEW_READY';

    const job: ImportJobDto = {
      id: jobId,
      jobNumber,
      importType: input.importType,
      sourceFormat,
      validationMode,
      duplicateStrategy,
      status,
      academicYearId: null,
      evaluationId: null,
      attendanceSessionId: null,
      fileName: input.file.name,
      contentType:
        sourceFormat === 'XLSX'
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
          : 'text/csv; charset=UTF-8',
      fileSizeBytes: input.file.size,
      fileSha256: `demo-sha256-${padded}`,
      totalRows: rows.length,
      validRows,
      errorRows,
      duplicateRows,
      createdCount: 0,
      updatedCount: 0,
      skippedCount: 0,
      quotaResource: 'STUDENTS',
      quotaLimit: 2000,
      quotaUsedBefore: 312,
      quotaProjectedAfter: 312 + validRows,
      createdBy: this.authService.user()?.id ?? 'usr-demo-director',
      analyzedAt: nowIso,
      executedAt: null,
      executedBy: null,
      cancelledAt: null,
      cancelledBy: null,
      cancellationReason: null,
      createdAt: nowIso,
    };

    this.jobs.set(jobId, { job, rows });
    return job;
  }

  private async readFileAsText(file: File): Promise<string> {
    if (typeof file.text === 'function') {
      return file.text();
    }
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
      reader.onerror = () => reject(new Error('Impossible de lire le fichier CSV.'));
      reader.readAsText(file, 'utf-8');
    });
  }

  private parseCsvToDemoRows(jobId: string, rawText: string): ImportJobRowDto[] {
    const lines = rawText
      .replace(/^\uFEFF/, '')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    if (lines.length <= 1) {
      return [];
    }

    const headerLine = lines[0];
    const delimiter =
      (headerLine.match(/;/g)?.length ?? 0) >= (headerLine.match(/,/g)?.length ?? 0)
        ? ';'
        : ',';
    const headers = splitCsvLine(headerLine, delimiter).map(normalizeHeader);

    const rows: ImportJobRowDto[] = [];
    for (let index = 1; index < lines.length; index += 1) {
      const cells = splitCsvLine(lines[index], delimiter);
      const record: Record<string, string> = {};
      headers.forEach((key, colIdx) => {
        if (key) {
          record[key] = cells[colIdx] ?? '';
        }
      });

      const firstName = record['first_name'] || record['prenom'] || record['guardian_first_name'] || '';
      const lastName = record['last_name'] || record['nom'] || record['guardian_last_name'] || '';
      const phone =
        record['phone'] ||
        record['guardian_phone'] ||
        record['telephone'] ||
        record['telephone_tuteur'] ||
        '';
      const classCode =
        record['class_code'] || record['classe'] || record['code_classe'] || 'Non affectée';
      const matricule =
        record['matricule'] || record['student_matricule'] || `LIGNE-${index + 1}`;

      let errorField: string | null = null;
      let errorCode: string | null = null;
      let errorMessage: string | null = null;

      if (!firstName.trim()) {
        errorField = 'first_name';
        errorCode = 'REQUIRED_FIELD_MISSING';
        errorMessage = 'Le prénom est obligatoire.';
      } else if (!lastName.trim()) {
        errorField = 'last_name';
        errorCode = 'REQUIRED_FIELD_MISSING';
        errorMessage = 'Le nom de famille est obligatoire.';
      } else if (phone.trim() && !SENEGAL_E164_PHONE_REGEX.test(phone.trim())) {
        errorField = 'phone';
        errorCode = 'INVALID_PHONE_E164';
        errorMessage = 'Format téléphone invalide : attendu +221 suivi de 9 chiffres.';
      }

      const isError = errorMessage !== null;
      rows.push({
        id: `${jobId}-row-${index + 1}`,
        rowNumber: index + 1,
        naturalKey: matricule,
        rowStatus: isError ? 'ERROR' : 'VALID',
        plannedAction: isError ? 'ERROR' : 'CREATE',
        appliedAction: null,
        errorCode,
        errorField,
        errorMessage,
        targetEntityType: 'STUDENT',
        targetEntityId: null,
        rowPreviewJson: JSON.stringify({
          matricule,
          first_name: firstName,
          last_name: lastName,
          class_code: classCode,
          phone,
        }),
      });
    }

    return rows;
  }
}
