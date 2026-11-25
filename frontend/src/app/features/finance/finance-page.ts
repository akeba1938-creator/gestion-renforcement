import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-finance-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="space-y-2">
      <h1 class="text-2xl font-bold text-base-content">Finances</h1>
      <p class="text-sm text-base-content/75">Module en cours de construction.</p>
    </section>
  `,
})
export class FinancePageComponent {}
