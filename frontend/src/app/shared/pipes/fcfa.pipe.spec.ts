import { FcfaPipe } from './fcfa.pipe';

/**
 * Normalizes French non-breaking spaces (\u00a0 and \u202f) produced by Intl.NumberFormat('fr-FR')
 * so assertions work consistently across Node/ICU environments.
 */
function normalizeSpaces(value: string): string {
  return value.replace(/[\u00a0\u202f]/g, ' ');
}

describe('FcfaPipe', () => {
  const pipe = new FcfaPipe();

  it('formats positive integer amounts in FCFA with French grouping', () => {
    const formatted = pipe.transform(45000);
    expect(formatted).toMatch(/^45[\u00a0\u202f ]000 FCFA$/);
    expect(normalizeSpaces(formatted)).toBe('45 000 FCFA');
  });

  it('formats large amounts without decimals', () => {
    expect(normalizeSpaces(pipe.transform(1875000))).toBe('1 875 000 FCFA');
  });

  it('formats zero and negative integer amounts (e.g. contra-entries)', () => {
    expect(normalizeSpaces(pipe.transform(0))).toBe('0 FCFA');
    expect(normalizeSpaces(pipe.transform(-15000))).toBe('-15 000 FCFA');
  });

  it('truncates accidental decimals so FCFA never renders fractional digits', () => {
    expect(normalizeSpaces(pipe.transform(12500.89))).toBe('12 500 FCFA');
  });

  it('returns an em-dash for null, undefined, or non-finite values', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform(Number.NaN)).toBe('—');
  });
});
