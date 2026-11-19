import { vi } from 'vitest';
import { DakarDatePipe } from './dakar-date.pipe';

describe('DakarDatePipe', () => {
  const pipe = new DakarDatePipe();

  it('formats a UTC instant near midnight in Africa/Dakar and explicitly passes timeZone Africa/Dakar', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat');

    // Africa/Dakar is UTC+0 year-round: 23:59 UTC stays on 15/10/2026.
    const formattedDate = pipe.transform('2026-10-15T23:59:00Z', 'date');
    expect(formattedDate).toBe('15/10/2026');
    expect(spy).toHaveBeenCalledWith(
      'fr-FR',
      expect.objectContaining({ timeZone: 'Africa/Dakar' })
    );

    const formattedDateTime = pipe.transform('2026-10-15T23:59:00Z', 'datetime');
    expect(formattedDateTime).toContain('15/10/2026');
    expect(formattedDateTime).toContain('23:59');

    spy.mockRestore();
  });

  it('formats time mode in Africa/Dakar timezone', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat');

    expect(pipe.transform('2026-10-15T09:05:00Z', 'time')).toBe('09:05');
    expect(spy).toHaveBeenCalledWith(
      'fr-FR',
      expect.objectContaining({ timeZone: 'Africa/Dakar' })
    );

    spy.mockRestore();
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
