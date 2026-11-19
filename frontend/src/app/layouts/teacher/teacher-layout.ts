import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Shell layout reserved for the Teacher space (not wired in routes yet).
 */
@Component({
  selector: 'app-teacher-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="min-h-screen bg-base-200 text-base-content">
      <main class="mx-auto max-w-5xl p-4">
        <router-outlet />
      </main>
    </div>
  `,
})
export class TeacherLayoutComponent {}
