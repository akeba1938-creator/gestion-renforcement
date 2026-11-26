import { Pipe, PipeTransform } from '@angular/core';

const FCFA_FORMATTER = new Intl.NumberFormat('fr-FR', {
  maximumFractionDigits: 0,
});

/**
 * Formats integer FCFA (XOF) monetary amounts without decimals.
 */
@Pipe({
  name: 'fcfa',
  standalone: true,
})
export class FcfaPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '—';
    }
    const integerAmount = Math.trunc(value);
    const formatted = FCFA_FORMATTER.format(integerAmount).replace(/\u202F/g, '\u00A0');
    return `${formatted} FCFA`;
  }
}
