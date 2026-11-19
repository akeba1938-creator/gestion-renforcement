import { DakarDatePipe } from './dakar-date.pipe';

describe('DakarDatePipe', () => {
  const pipe = new DakarDatePipe();

  it('formats a UTC ISO timestamp as a date in Africa/Dakar timezone', () => {
    // Africa/Dakar is UTC+0 year-round: 23:30 UTC stays on the same calendar day.
    expect(pipe.transform('2026-10-15T23:30:00Z')).toBe('15/10/2026');
  });

  it('formats datetime and time modes in Africa/Dakar timezone', () => {
    const datetime = pipe.transform('2026-10-15T09:05:00Z', 'datetime');
    expect(datetime).toContain('15/10/2026');
    expect(datetime).toContain('09:05');

    expect(pipe.transform('2026-10-15T09:05:00Z', 'time')).toBe('09:05');
  });

  it('accepts Date instances', () => {
    expect(pipe.transform(new Date('2026-01-02T12:00:00Z'), 'date')).toBe('02/01/2026');
  });

  it('returns an em-dash for empty or invalid values', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform('invalid-date')).toBe('—');
  });
});
