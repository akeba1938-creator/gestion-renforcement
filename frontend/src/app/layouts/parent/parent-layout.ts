import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Shell layout reserved for the Parent space (not wired in routes yet).
 */
@Component({
  selector: 'app-parent-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="min-h-screen bg-base-200 text-base-content">
      <main class="mx-auto max-w-3xl p-4">
        <router-outlet />
      </main>
    </div>
  `,
})
export class ParentLayoutComponent {}
