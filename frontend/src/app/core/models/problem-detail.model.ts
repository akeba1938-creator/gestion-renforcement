import { HttpErrorResponse } from '@angular/common/http';

export interface ProblemFieldError {
  readonly field: string;
  readonly message: string;
}

/**
 * Typed RFC 9457 Problem Details model aligned with backend GlobalProblemHandler.
 */
export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  readonly instance?: string;
  readonly requestId?: string;
  readonly timestamp?: string;
  readonly errors?: readonly ProblemFieldError[];
}

/**
 * Application error wrapping a normalized RFC 9457 ProblemDetail so HTTP failures
 * can be handled uniformly by UI components without leaking raw technical details.
 */
export class ProblemDetailError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title);
    this.name = 'ProblemDetailError';
    this.problem = problem;
  }
}

interface StatusFallback {
  readonly type: string;
  readonly title: string;
  readonly detail: string;
}

const DEFAULT_FALLBACK: StatusFallback = {
  type: 'urn:ecole:problem:unexpected-error',
  title: 'Erreur inattendue',
  detail: 'Une erreur inattendue est survenue. Veuillez réessayer.',
};

const STATUS_FALLBACKS: Readonly<Record<number, StatusFallback>> = {
  0: {
    type: 'urn:ecole:problem:network-error',
    title: 'Connexion impossible',
    detail: 'Impossible de joindre le serveur. Vérifiez votre connexion réseau et réessayez.',
  },
  400: {
    type: 'urn:ecole:problem:bad-request',
    title: 'Requête invalide',
    detail: 'Les données transmises sont incomplètes ou invalides.',
  },
  401: {
    type: 'urn:ecole:problem:authentication-required',
    title: 'Authentification requise',
    detail: 'Votre session a expiré ou nécessite une nouvelle connexion.',
  },
  403: {
    type: 'urn:ecole:problem:access-denied',
    title: 'Accès refusé',
    detail: "Vous ne disposez pas des autorisations nécessaires pour effectuer cette action.",
  },
  404: {
    type: 'urn:ecole:problem:not-found',
    title: 'Ressource introuvable',
    detail: 'La ressource demandée est introuvable ou a été supprimée.',
  },
  409: {
    type: 'urn:ecole:problem:conflict',
    title: 'Conflit détecté',
    detail: "L'opération entre en conflit avec l'état actuel des données.",
  },
  422: {
    type: 'urn:ecole:problem:unprocessable-entity',
    title: 'Données non traitables',
    detail: 'Les données fournies ne respectent pas les règles métier attendues.',
  },
  423: {
    type: 'urn:ecole:problem:locked',
    title: 'Ressource verrouillée',
    detail: 'Cette ressource est temporairement verrouillée.',
  },
  429: {
    type: 'urn:ecole:problem:too-many-requests',
    title: 'Trop de requêtes',
    detail: 'Trop de tentatives ont été effectuées. Veuillez patienter avant de réessayer.',
  },
  500: {
    type: 'urn:ecole:problem:internal-error',
    title: 'Erreur serveur',
    detail: 'Une erreur interne est survenue sur le serveur. Veuillez réessayer ultérieurement.',
  },
  502: {
    type: 'urn:ecole:problem:bad-gateway',
    title: 'Service temporairement indisponible',
    detail: 'Le serveur est temporairement injoignable. Veuillez réessayer dans quelques instants.',
  },
  503: {
    type: 'urn:ecole:problem:service-unavailable',
    title: 'Service indisponible',
    detail: 'Le service est momentanément indisponible. Veuillez réessayer ultérieurement.',
  },
  504: {
    type: 'urn:ecole:problem:gateway-timeout',
    title: 'Délai dépassé',
    detail: 'Le serveur a mis trop de temps à répondre. Veuillez réessayer.',
  },
};

const TECHNICAL_LEAK_PATTERN =
  /(<\s*!doctype|<\s*html|java\.|jakarta\.|org\.springframework\.|org\.hibernate\.|sqlstate|stacktrace|\bat\s+[\w.$]+\()/i;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readSafeString(value: unknown, maxLength = 400): string | undefined {
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  if (!trimmed || TECHNICAL_LEAK_PATTERN.test(trimmed)) {
    return undefined;
  }
  return trimmed.length > maxLength ? `${trimmed.slice(0, maxLength - 1)}…` : trimmed;
}

function readIdentifier(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > 128 || /\s/.test(trimmed)) {
    return undefined;
  }
  return trimmed;
}

function parseFieldErrors(raw: unknown): readonly ProblemFieldError[] | undefined {
  if (Array.isArray(raw)) {
    const parsed: ProblemFieldError[] = [];
    for (const entry of raw) {
      if (!isRecord(entry)) {
        continue;
      }
      const field = readSafeString(entry['field'], 120);
      const message = readSafeString(entry['message'], 300);
      if (field && message) {
        parsed.push({ field, message });
      }
    }
    return parsed.length > 0 ? parsed : undefined;
  }

  if (isRecord(raw)) {
    const parsed: ProblemFieldError[] = [];
    for (const [key, val] of Object.entries(raw)) {
      const field = readSafeString(key, 120);
      const message = readSafeString(val, 300);
      if (field && message) {
        parsed.push({ field, message });
      }
    }
    return parsed.length > 0 ? parsed : undefined;
  }

  return undefined;
}

function tryParseJsonBody(body: unknown): Record<string, unknown> | null {
  if (isRecord(body)) {
    return body;
  }
  if (typeof body === 'string') {
    const trimmed = body.trim();
    if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(trimmed);
      return isRecord(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }
  return null;
}

function fallbackForStatus(status: number): StatusFallback {
  if (status in STATUS_FALLBACKS) {
    return STATUS_FALLBACKS[status];
  }
  if (status >= 500) {
    return STATUS_FALLBACKS[500];
  }
  if (status >= 400) {
    return STATUS_FALLBACKS[400];
  }
  return DEFAULT_FALLBACK;
}

function isStructuredProblemBody(body: Record<string, unknown>): boolean {
  return (
    typeof body['type'] === 'string' ||
    typeof body['title'] === 'string' ||
    typeof body['detail'] === 'string' ||
    Array.isArray(body['errors'])
  );
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (!isRecord(value)) {
    return false;
  }
  return (
    typeof value['type'] === 'string' &&
    typeof value['title'] === 'string' &&
    typeof value['status'] === 'number' &&
    typeof value['detail'] === 'string'
  );
}

export function isProblemDetailError(value: unknown): value is ProblemDetailError {
  return value instanceof ProblemDetailError;
}

/**
 * Safely parses any HTTP or unknown error into a UI-ready RFC 9457 ProblemDetail.
 * Never exposes raw HTML, stack traces, or internal technical messages.
 */
export function parseProblemDetail(error: unknown, fallbackStatus = 500): ProblemDetail {
  if (isProblemDetailError(error)) {
    return error.problem;
  }

  if (isProblemDetail(error)) {
    return error;
  }

  if (error instanceof HttpErrorResponse) {
    const status = typeof error.status === 'number' ? error.status : fallbackStatus;
    const fallback = fallbackForStatus(status);
    const headerRequestId = readIdentifier(error.headers?.get('X-Request-Id'));

    if (status === 0) {
      return {
        type: fallback.type,
        title: fallback.title,
        status: 0,
        detail: fallback.detail,
        ...(headerRequestId ? { requestId: headerRequestId } : {}),
      };
    }

    const body = tryParseJsonBody(error.error);
    if (body && isStructuredProblemBody(body)) {
      const bodyStatus =
        typeof body['status'] === 'number' && Number.isInteger(body['status'])
          ? body['status']
          : status;
      const resolvedFallback = fallbackForStatus(bodyStatus);
      const type = readSafeString(body['type'], 200) ?? resolvedFallback.type;
      const title = readSafeString(body['title'], 160) ?? resolvedFallback.title;
      const detail = readSafeString(body['detail'], 400) ?? resolvedFallback.detail;
      const instance = readSafeString(body['instance'], 256);
      const requestId = readIdentifier(body['requestId']) ?? headerRequestId;
      const timestamp = readSafeString(body['timestamp'], 64);
      const errors = parseFieldErrors(body['errors']);

      return {
        type,
        title,
        status: bodyStatus,
        detail,
        ...(instance ? { instance } : {}),
        ...(requestId ? { requestId } : {}),
        ...(timestamp ? { timestamp } : {}),
        ...(errors ? { errors } : {}),
      };
    }

    return {
      type: fallback.type,
      title: fallback.title,
      status,
      detail: fallback.detail,
      ...(headerRequestId ? { requestId: headerRequestId } : {}),
    };
  }

  const fallback = fallbackForStatus(fallbackStatus);
  return {
    type: fallback.type,
    title: fallback.title,
    status: fallbackStatus,
    detail: fallback.detail,
  };
}

/**
 * Maps ProblemDetail field errors to a field -> message dictionary for reactive/signal forms.
 */
export function extractFieldErrors(
  problem: ProblemDetail | null | undefined
): Readonly<Record<string, string>> {
  const result: Record<string, string> = {};
  if (!problem?.errors) {
    return result;
  }
  for (const entry of problem.errors) {
    if (!(entry.field in result)) {
      result[entry.field] = entry.message;
    }
  }
  return result;
}
