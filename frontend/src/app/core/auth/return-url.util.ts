export const DEFAULT_POST_LOGIN_URL = '/dashboard';

const FORBIDDEN_RETURN_PATHS: readonly string[] = ['/login', '/forbidden'];
const CONTROL_CHAR_PATTERN = /[\u0000-\u001F\u007F]/;

/**
 * Validates and normalizes a post-login `returnUrl` query parameter.
 * Prevents open redirects (`https://...`, `//evil.com`, backslashes, control chars)
 * and redirect loops back to `/login`.
 */
export function sanitizeReturnUrl(raw: string | null | undefined): string {
  if (!raw) {
    return DEFAULT_POST_LOGIN_URL;
  }

  const trimmed = raw.trim();
  if (
    !trimmed ||
    !trimmed.startsWith('/') ||
    trimmed.startsWith('//') ||
    trimmed.includes('\\') ||
    CONTROL_CHAR_PATTERN.test(trimmed)
  ) {
    return DEFAULT_POST_LOGIN_URL;
  }

  try {
    const parsed = new URL(trimmed, 'http://localhost');
    if (parsed.origin !== 'http://localhost') {
      return DEFAULT_POST_LOGIN_URL;
    }

    const normalizedPath = parsed.pathname.replace(/\/+$/, '') || '/';
    if (
      normalizedPath === '/' ||
      FORBIDDEN_RETURN_PATHS.some(
        (blocked) => normalizedPath === blocked || normalizedPath.startsWith(`${blocked}/`)
      )
    ) {
      return DEFAULT_POST_LOGIN_URL;
    }

    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return DEFAULT_POST_LOGIN_URL;
  }
}
