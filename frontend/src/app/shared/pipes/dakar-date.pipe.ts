import { Pipe, PipeTransform } from '@angular/core';

export type DakarDateFormat = 'date' | 'datetime' | 'time';

const FORMAT_OPTIONS: Record<DakarDateFormat, Intl.DateTimeFormatOptions> = {
  date: {
    timeZone: 'Africa/Dakar',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  },
  datetime: {
    timeZone: 'Africa/Dakar',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  },
  time: {
    timeZone: 'Africa/Dakar',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  },
};

/**
 * Converts UTC ISO dates / Date instances for display in the Africa/Dakar timezone.
 */
@Pipe({
  name: 'dakarDate',
  standalone: true,
})
export class DakarDatePipe implements PipeTransform {
  transform(
    value: string | Date | null | undefined,
    format: DakarDateFormat = 'date'
  ): string {
    if (!value) {
      return '—';
    }
    const parsed = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return '—';
    }
    return new Intl.DateTimeFormat('fr-FR', FORMAT_OPTIONS[format]).format(parsed);
  }
}
