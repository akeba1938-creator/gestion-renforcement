import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { inject } from '@angular/core';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <section class="flex min-h-screen items-center justify-center bg-base-200 p-4">
      <div class="card w-full max-w-md border border-base-300 bg-base-100 shadow-sm">
        <div class="card-body gap-4">
          <h1 class="card-title text-xl">Connexion — École SaaS</h1>

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4" novalidate>
            <div class="form-control gap-1">
              <label for="login-email" class="label-text text-sm font-medium">
                Adresse e-mail
              </label>
              <input
                id="login-email"
                type="email"
                autocomplete="username"
                formControlName="email"
                class="input input-bordered w-full"
              />
            </div>

            <div class="form-control gap-1">
              <label for="login-password" class="label-text text-sm font-medium">
                Mot de passe
              </label>
              <input
                id="login-password"
                type="password"
                autocomplete="current-password"
                formControlName="password"
                class="input input-bordered w-full"
              />
            </div>

            @if (statusMessage(); as message) {
              <div class="alert alert-warning" role="status">
                <span>{{ message }}</span>
              </div>
            }

            <button type="submit" class="btn btn-primary w-full">Se connecter</button>
          </form>
        </div>
      </div>
    </section>
  `,
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);

  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly statusMessage = signal<string | null>(null);

  protected onSubmit(): void {
    this.statusMessage.set("Connexion indisponible : le serveur n'est pas encore branché");
  }
}
