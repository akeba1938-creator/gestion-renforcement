import { FcfaPipe } from './fcfa.pipe';

describe('FcfaPipe', () => {
  const pipe = new FcfaPipe();

  it('formats positive integer amounts in FCFA with standard non-breaking space grouping', () => {
    const formatted = pipe.transform(45000);
    expect(formatted).toBe('45\u00A0000 FCFA');
    expect(formatted).not.toContain('\u202F');
  });

  it('formats large amounts without decimals and replaces narrow spaces with standard NBSP', () => {
    const formatted = pipe.transform(20150000);
    expect(formatted).toBe('20\u00A0150\u00A0000 FCFA');
    expect(formatted).not.toContain('\u202F');
    expect(pipe.transform(1875000)).toBe('1\u00A0875\u00A0000 FCFA');
  });

  it('formats zero and negative integer amounts (e.g. contra-entries)', () => {
    expect(pipe.transform(0)).toBe('0 FCFA');
    expect(pipe.transform(-15000)).toBe('-15\u00A0000 FCFA');
  });

  it('truncates accidental decimals so FCFA never renders fractional digits', () => {
    expect(pipe.transform(12500.89)).toBe('12\u00A0500 FCFA');
  });

  it('returns an em-dash for null, undefined, or non-finite values', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform(Number.NaN)).toBe('—');
  });
});
