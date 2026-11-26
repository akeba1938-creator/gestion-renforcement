export type ImportType =
  | 'STUDENTS'
  | 'FAMILIES_GUARDIANS'
  | 'TEACHERS'
  | 'ENROLLMENTS'
  | 'GRADES'
  | 'ATTENDANCE';

export type ImportSourceFormat = 'CSV' | 'XLSX';

export type ImportValidationMode = 'STRICT_ALL_OR_NOTHING' | 'PARTIAL_SKIP_ERRORS';

export type DuplicateStrategy = 'FAIL_ON_DUPLICATE' | 'SKIP_DUPLICATES' | 'UPSERT_UPDATE';

export type ImportJobStatus =
  | 'PREVIEW_READY'
  | 'VALIDATION_FAILED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED'
  | 'CANCELLED';

export type ImportRowStatus = 'VALID' | 'DUPLICATE' | 'ERROR' | 'CREATED' | 'UPDATED' | 'SKIPPED';

export type ImportRowAction = 'CREATE' | 'UPDATE' | 'SKIP_DUPLICATE' | 'ERROR';

export type PreviewRowFilter = 'ALL' | 'VALID' | 'ERROR';

export interface ImportColumnDescriptorDto {
  readonly key: string;
  readonly labelFr: string;
  readonly required: boolean;
  readonly exampleValue: string;
  readonly aliases: readonly string[];
}

export interface ImportTemplateDto {
  readonly importType: ImportType;
  readonly titleFr: string;
  readonly descriptionFr: string;
  readonly columns: readonly ImportColumnDescriptorDto[];
}

export interface ImportJobDto {
  readonly id: string;
  readonly jobNumber: string;
  readonly importType: ImportType;
  readonly sourceFormat: ImportSourceFormat;
  readonly validationMode: ImportValidationMode;
  readonly duplicateStrategy: DuplicateStrategy;
  readonly status: ImportJobStatus;
  readonly academicYearId: string | null;
  readonly evaluationId: string | null;
  readonly attendanceSessionId: string | null;
  readonly fileName: string;
  readonly contentType: string;
  readonly fileSizeBytes: number;
  readonly fileSha256: string;
  readonly totalRows: number;
  readonly validRows: number;
  readonly errorRows: number;
  readonly duplicateRows: number;
  readonly createdCount: number;
  readonly updatedCount: number;
  readonly skippedCount: number;
  readonly quotaResource: string | null;
  readonly quotaLimit: number | null;
  readonly quotaUsedBefore: number | null;
  readonly quotaProjectedAfter: number | null;
  readonly createdBy: string;
  readonly analyzedAt: string | null;
  readonly executedAt: string | null;
  readonly executedBy: string | null;
  readonly cancelledAt: string | null;
  readonly cancelledBy: string | null;
  readonly cancellationReason: string | null;
  readonly createdAt: string;
}

export interface ImportJobRowDto {
  readonly id: string;
  readonly rowNumber: number;
  readonly naturalKey: string | null;
  readonly rowStatus: ImportRowStatus;
  readonly plannedAction: ImportRowAction;
  readonly appliedAction: ImportRowAction | null;
  readonly errorCode: string | null;
  readonly errorField: string | null;
  readonly errorMessage: string | null;
  readonly targetEntityType: string | null;
  readonly targetEntityId: string | null;
  readonly rowPreviewJson: string | null;
}

export interface BackendPageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface ImportPreviewRow {
  readonly id: string;
  readonly rowNumber: number;
  readonly naturalKey: string | null;
  readonly studentName: string;
  readonly matricule: string | null;
  readonly className: string;
  readonly guardianPhone: string;
  readonly rowStatus: ImportRowStatus;
  readonly plannedAction: ImportRowAction;
  readonly appliedAction: ImportRowAction | null;
  readonly errorField: string | null;
  readonly anomaly: string | null;
}

export interface ImportJobRowsPage {
  readonly content: readonly ImportPreviewRow[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface CreateImportJobInput {
  readonly file: File;
  readonly importType: ImportType;
  readonly validationMode?: ImportValidationMode;
  readonly duplicateStrategy?: DuplicateStrategy;
  readonly executeImmediately?: boolean;
  readonly idempotencyKey?: string;
}

export interface ListImportRowsQuery {
  readonly rowStatus?: ImportRowStatus;
  readonly plannedAction?: ImportRowAction;
  readonly page?: number;
  readonly size?: number;
  readonly sortBy?: 'rowNumber' | 'rowStatus' | 'plannedAction';
  readonly direction?: 'ASC' | 'DESC';
}

export interface ImportFileDownload {
  readonly blob: Blob;
  readonly fileName: string;
  readonly contentType: string;
}

export interface ImportTypeOption {
  readonly value: ImportType;
  readonly label: string;
  readonly description: string;
}

export interface ImportFileValidationResult {
  readonly valid: boolean;
  readonly format: ImportSourceFormat | null;
  readonly errorMessage: string | null;
}

/**
 * Matches backend `ImportFileSecurityPolicy.MAX_FILE_SIZE_BYTES` (5 MiB).
 */
export const IMPORT_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
export const IMPORT_ACCEPTED_EXTENSIONS: readonly string[] = ['.csv', '.xlsx'];
export const IMPORT_ACCEPT_ATTRIBUTE =
  '.csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

export const IMPORT_TYPE_OPTIONS: readonly ImportTypeOption[] = [
  {
    value: 'STUDENTS',
    label: 'Élèves',
    description: 'Création ou mise à jour des dossiers élèves avec affectation de classe optionnelle.',
  },
  {
    value: 'FAMILIES_GUARDIANS',
    label: 'Familles & tuteurs',
    description: 'Import des responsables légaux, contacts +221 et rattachement aux élèves.',
  },
  {
    value: 'ENROLLMENTS',
    label: 'Inscriptions aux classes',
    description: "Affectation d'élèves existants dans les classes de l'année scolaire.",
  },
  {
    value: 'TEACHERS',
    label: 'Enseignants',
    description: 'Création ou mise à jour des enseignants avec spécialités et taux horaires FCFA.',
  },
  {
    value: 'GRADES',
    label: "Notes d'évaluation",
    description: 'Saisie en lot des notes pour une évaluation sur période ouverte.',
  },
  {
    value: 'ATTENDANCE',
    label: 'Présences & absences',
    description: "Pointage en lot des élèves pour une séance d'appel.",
  },
];

export const VALIDATION_MODE_OPTIONS: readonly {
  readonly value: ImportValidationMode;
  readonly label: string;
  readonly hint: string;
}[] = [
  {
    value: 'PARTIAL_SKIP_ERRORS',
    label: 'Ignorer les lignes en erreur',
    hint: 'Importe les lignes valides et génère un rapport CSV des anomalies.',
  },
  {
    value: 'STRICT_ALL_OR_NOTHING',
    label: 'Strict (tout ou rien)',
    hint: "Bloque l'exécution tant qu'au moins une ligne contient une erreur.",
  },
];

const DECIMAL_FORMATTER = new Intl.NumberFormat('fr-FR', {
  maximumFractionDigits: 1,
  minimumFractionDigits: 0,
});

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0\u00A0o';
  }
  if (bytes < 1024) {
    return `${Math.trunc(bytes)}\u00A0o`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${DECIMAL_FORMATTER.format(kb).replace(/\u202F/g, '\u00A0')}\u00A0Ko`;
  }
  const mb = kb / 1024;
  return `${DECIMAL_FORMATTER.format(mb).replace(/\u202F/g, '\u00A0')}\u00A0Mo`;
}

export function detectFileExtensionFormat(fileName: string): ImportSourceFormat | null {
  const normalized = fileName.trim().toLowerCase();
  if (normalized.endsWith('.csv')) {
    return 'CSV';
  }
  if (normalized.endsWith('.xlsx')) {
    return 'XLSX';
  }
  return null;
}

export function validateImportFile(file: File | null | undefined): ImportFileValidationResult {
  if (!file) {
    return {
      valid: false,
      format: null,
      errorMessage: 'Veuillez sélectionner un fichier .csv ou .xlsx à analyser.',
    };
  }

  const format = detectFileExtensionFormat(file.name);
  if (!format) {
    return {
      valid: false,
      format: null,
      errorMessage:
        'Format non pris en charge. Seuls les fichiers CSV (.csv) et Excel (.xlsx) sont acceptés.',
    };
  }

  if (file.size <= 0) {
    return {
      valid: false,
      format,
      errorMessage: 'Le fichier sélectionné est vide (0 octet).',
    };
  }

  if (file.size > IMPORT_MAX_FILE_SIZE_BYTES) {
    return {
      valid: false,
      format,
      errorMessage: `Le fichier dépasse la taille maximale autorisée de ${formatFileSize(IMPORT_MAX_FILE_SIZE_BYTES)}.`,
    };
  }

  return {
    valid: true,
    format,
    errorMessage: null,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parsePreviewJsonObject(rawJson: string | null): Record<string, string> {
  if (!rawJson) {
    return {};
  }
  try {
    const parsed: unknown = JSON.parse(rawJson);
    if (!isRecord(parsed)) {
      return {};
    }
    const result: Record<string, string> = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === 'string' && value.trim().length > 0) {
        result[key] = value.trim();
      } else if (typeof value === 'number' || typeof value === 'boolean') {
        result[key] = String(value);
      }
    }
    return result;
  } catch {
    return {};
  }
}

function pickFirst(map: Readonly<Record<string, string>>, keys: readonly string[]): string {
  for (const key of keys) {
    const value = map[key];
    if (value && value.trim().length > 0) {
      return value.trim();
    }
  }
  return '';
}

export function mapJobRowDtoToPreviewRow(dto: ImportJobRowDto): ImportPreviewRow {
  const fields = parsePreviewJsonObject(dto.rowPreviewJson);

  const firstName = pickFirst(fields, [
    'first_name',
    'prenom',
    'prenoms',
    'guardian_first_name',
    'prenom_tuteur',
  ]);
  const lastName = pickFirst(fields, [
    'last_name',
    'nom',
    'nom_de_famille',
    'guardian_last_name',
    'nom_tuteur',
  ]);
  const fullNameFromFields = [firstName, lastName].filter(Boolean).join(' ').trim();
  const explicitFullName = pickFirst(fields, ['full_name', 'student_name', 'eleve', 'nom_complet']);
  const matricule =
    pickFirst(fields, ['matricule', 'student_matricule', 'code_eleve', 'employee_code']) ||
    dto.naturalKey ||
    null;

  const studentName = fullNameFromFields || explicitFullName || matricule || `Ligne ${dto.rowNumber}`;

  const className =
    pickFirst(fields, ['class_code', 'classe', 'code_classe', 'class_room_code', 'subject_code']) ||
    'Non affectée';

  const guardianPhone =
    pickFirst(fields, [
      'guardian_phone',
      'telephone_tuteur',
      'phone',
      'telephone',
      'tel',
      'guardian_secondary_phone',
    ]) || '—';

  const anomaly = dto.errorMessage?.trim() || null;

  return {
    id: dto.id,
    rowNumber: dto.rowNumber,
    naturalKey: dto.naturalKey,
    studentName,
    matricule,
    className,
    guardianPhone,
    rowStatus: dto.rowStatus,
    plannedAction: dto.plannedAction,
    appliedAction: dto.appliedAction,
    errorField: dto.errorField,
    anomaly,
  };
}

export function extractCsvFilename(
  contentDisposition: string | null | undefined,
  fallbackName: string
): string {
  if (!contentDisposition) {
    return fallbackName;
  }

  const utf8Match = /filename\*=UTF-8''([^;\s]+)/i.exec(contentDisposition);
  if (utf8Match?.[1]) {
    try {
      const decoded = decodeURIComponent(utf8Match[1]).replace(/["/\\]/g, '').trim();
      if (decoded.length > 0) {
        return decoded;
      }
    } catch {
      // Fall back to standard filename attribute parsing
    }
  }

  const asciiMatch = /filename="?([^";\r\n]+)"?/i.exec(contentDisposition);
  if (asciiMatch?.[1]) {
    const cleaned = asciiMatch[1].replace(/["/\\]/g, '').trim();
    if (cleaned.length > 0) {
      return cleaned;
    }
  }

  return fallbackName;
}

export function isJobExecutable(job: ImportJobDto | null): boolean {
  return job !== null && job.status === 'PREVIEW_READY' && job.validRows > 0;
}

export function isJobTerminal(job: ImportJobDto | null): boolean {
  if (!job) {
    return false;
  }
  return (
    job.status === 'COMPLETED' ||
    job.status === 'COMPLETED_WITH_ERRORS' ||
    job.status === 'FAILED' ||
    job.status === 'CANCELLED'
  );
}
