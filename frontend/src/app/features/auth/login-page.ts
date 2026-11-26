import { Clipboard } from '@angular/cdk/clipboard';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  LucideArrowLeft,
  LucideBuilding2,
  LucideCheck,
  LucideCircleAlert,
  LucideCopy,
  LucideEye,
  LucideEyeOff,
  LucideKeyRound,
  LucideLock,
  LucideMoon,
  LucideShieldCheck,
  LucideSun,
  LucideWifiOff,
  provideLucideConfig,
} from '@lucide/angular';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { sanitizeReturnUrl } from '../../core/auth/return-url.util';
import { AuthResponse, MfaEnrollResponse } from '../../core/models/auth.model';
import {
  ProblemDetail,
  extractFieldErrors,
  parseProblemDetail,
} from '../../core/models/problem-detail.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ThemeService } from '../../core/services/theme.service';

type LoginStep = 'credentials' | 'mfa';
type LoginMode = 'tenant' | 'platform';

interface SecurityPillar {
  readonly id: string;
  readonly icon: 'building' | 'shield' | 'lock';
  readonly title: string;
  readonly description: string;
}

const SECURITY_PILLARS: readonly SecurityPillar[] = [
  {
    id: 'isolation',
    icon: 'building',
    title: 'Isolation stricte par établissement',
    description:
      'Chaque établissement dispose de son périmètre cloisonné, vérifié côté serveur à chaque appel.',
  },
  {
    id: 'mfa',
    icon: 'shield',
    title: 'Double authentification (MFA)',
    description:
      'Validation TOTP à 6 chiffres pour protéger les opérations sensibles de direction et de caisse.',
  },
  {
    id: 'memory-session',
    icon: 'lock',
    title: 'Session sécurisée en mémoire',
    description:
      'Aucun jeton dans le stockage local du navigateur ; renouvellement protégé par cookie HttpOnly.',
  },
];

@Component({
  selector: 'app-login-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideLucideConfig({ strokeWidth: 1.75 })],
  imports: [
    ReactiveFormsModule,
    LucideArrowLeft,
    LucideBuilding2,
    LucideCheck,
    LucideCircleAlert,
    LucideCopy,
    LucideEye,
    LucideEyeOff,
    LucideKeyRound,
    LucideLock,
    LucideMoon,
    LucideShieldCheck,
    LucideSun,
    LucideWifiOff,
  ],
  templateUrl: './login-page.html',
})
export class LoginPageComponent {
  protected readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  protected readonly networkStatus = inject(NetworkStatusService);

  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly clipboard = inject(Clipboard);
  private readonly destroyRef = inject(DestroyRef);

  private readonly mfaCodeInput = viewChild<ElementRef<HTMLInputElement>>('mfaCodeInput');

  private copyRequestIdTimer: ReturnType<typeof setTimeout> | null = null;
  private copySecretTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly securityPillars = SECURITY_PILLARS;

  protected readonly step = signal<LoginStep>('credentials');
  protected readonly loginMode = signal<LoginMode>('tenant');
  protected readonly showPassword = signal<boolean>(false);
  protected readonly submitting = signal<boolean>(false);
  protected readonly enrollingMfa = signal<boolean>(false);
  protected readonly mfaEnrollment = signal<MfaEnrollResponse | null>(null);
  protected readonly activeError = signal<ProblemDetail | null>(null);
  protected readonly serverFieldErrors = signal<Readonly<Record<string, string>>>({});
  protected readonly credentialsSubmitted = signal<boolean>(false);
  protected readonly mfaSubmitted = signal<boolean>(false);
  protected readonly copiedRequestId = signal<boolean>(false);
  protected readonly copiedSecret = signal<boolean>(false);

  private readonly credentialsVersion = signal<number>(0);
  private readonly mfaVersion = signal<number>(0);

  protected readonly credentialsForm = this.formBuilder.nonNullable.group({
    schoolCode: ['', [Validators.required, Validators.maxLength(32)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected readonly mfaForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected readonly schoolCodeError = computed<string | null>(() => {
    this.credentialsVersion();
    if (this.loginMode() !== 'tenant') {
      return null;
    }
    const serverMsg = this.serverFieldErrors()['schoolCode'];
    if (serverMsg) {
      return serverMsg;
    }
    const control = this.credentialsForm.controls.schoolCode;
    if (!this.credentialsSubmitted() && !control.touched) {
      return null;
    }
    if (control.hasError('required') || control.value.trim().length === 0) {
      return "Le code de l'établissement est obligatoire.";
    }
    if (control.hasError('maxlength')) {
      return "Le code de l'établissement ne doit pas dépasser 32 caractères.";
    }
    return null;
  });

  protected readonly emailError = computed<string | null>(() => {
    this.credentialsVersion();
    const serverMsg = this.serverFieldErrors()['email'];
    if (serverMsg) {
      return serverMsg;
    }
    const control = this.credentialsForm.controls.email;
    if (!this.credentialsSubmitted() && !control.touched) {
      return null;
    }
    if (control.hasError('required') || control.value.trim().length === 0) {
      return "L'adresse e-mail est obligatoire.";
    }
    if (control.hasError('email')) {
      return "Veuillez saisir une adresse e-mail valide.";
    }
    if (control.hasError('maxlength')) {
      return "L'adresse e-mail est trop longue.";
    }
    return null;
  });

  protected readonly passwordError = computed<string | null>(() => {
    this.credentialsVersion();
    const serverMsg = this.serverFieldErrors()['password'];
    if (serverMsg) {
      return serverMsg;
    }
    const control = this.credentialsForm.controls.password;
    if (!this.credentialsSubmitted() && !control.touched) {
      return null;
    }
    if (control.hasError('required') || control.value.length === 0) {
      return 'Le mot de passe est obligatoire.';
    }
    if (control.hasError('maxlength')) {
      return 'Le mot de passe est trop long.';
    }
    return null;
  });

  protected readonly mfaCodeError = computed<string | null>(() => {
    this.mfaVersion();
    const serverMsg = this.serverFieldErrors()['code'];
    if (serverMsg) {
      return serverMsg;
    }
    const control = this.mfaForm.controls.code;
    if (!this.mfaSubmitted() && !control.touched) {
      return null;
    }
    if (control.hasError('required') || control.value.trim().length === 0) {
      return 'Le code de vérification à 6 chiffres est obligatoire.';
    }
    if (control.hasError('pattern')) {
      return 'Le code doit contenir exactement 6 chiffres.';
    }
    return null;
  });

  constructor() {
    this.credentialsForm.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.credentialsVersion.update((v) => v + 1);
    });

    this.credentialsForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (Object.keys(this.serverFieldErrors()).length > 0) {
        this.serverFieldErrors.set({});
      }
    });

    this.mfaForm.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.mfaVersion.update((v) => v + 1);
    });

    this.mfaForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (Object.keys(this.serverFieldErrors()).length > 0) {
        this.serverFieldErrors.set({});
      }
    });

    this.destroyRef.onDestroy(() => {
      if (this.copyRequestIdTimer !== null) {
        clearTimeout(this.copyRequestIdTimer);
      }
      if (this.copySecretTimer !== null) {
        clearTimeout(this.copySecretTimer);
      }
    });
  }

  protected setLoginMode(mode: LoginMode): void {
    if (this.loginMode() === mode || this.submitting()) {
      return;
    }

    this.loginMode.set(mode);
    this.activeError.set(null);
    this.serverFieldErrors.set({});
    this.credentialsSubmitted.set(false);

    const schoolCodeControl = this.credentialsForm.controls.schoolCode;
    if (mode === 'tenant') {
      schoolCodeControl.setValidators([Validators.required, Validators.maxLength(32)]);
    } else {
      schoolCodeControl.clearValidators();
    }
    schoolCodeControl.updateValueAndValidity();
    this.credentialsVersion.update((v) => v + 1);
  }

  protected togglePasswordVisibility(): void {
    this.showPassword.update((visible) => !visible);
  }

  protected isWarningStatus(status: number): boolean {
    return status === 0 || status === 423 || status === 429;
  }

  protected onSubmitCredentials(): void {
    if (this.submitting()) {
      return;
    }

    this.credentialsSubmitted.set(true);
    this.credentialsForm.markAllAsTouched();
    this.credentialsVersion.update((v) => v + 1);

    if (this.credentialsForm.invalid) {
      return;
    }

    if (!this.networkStatus.isOnline()) {
      this.activeError.set({
        type: 'urn:ecole:problem:network-error',
        title: 'Connexion hors-ligne',
        status: 0,
        detail:
          'Vous êtes actuellement hors-ligne. Rétablissez votre connexion réseau pour vous authentifier.',
      });
      return;
    }

    this.activeError.set(null);
    this.serverFieldErrors.set({});
    this.submitting.set(true);

    const { schoolCode, email, password } = this.credentialsForm.getRawValue();
    const request$ =
      this.loginMode() === 'tenant'
        ? this.authService.login({ schoolCode: schoolCode.trim(), email: email.trim(), password })
        : this.authService.loginPlatform({ email: email.trim(), password });

    request$
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (response) => this.handleAuthSuccess(response),
        error: (error: unknown) => this.handleAuthError(error),
      });
  }

  protected onMfaCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    if (!input) {
      return;
    }
    const sanitized = this.sanitizeOtp(input.value);
    if (sanitized !== input.value) {
      input.value = sanitized;
    }
    this.mfaForm.controls.code.setValue(sanitized, { emitEvent: true });
  }

  protected onMfaCodePaste(event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text') ?? '';
    if (!pasted) {
      return;
    }
    event.preventDefault();
    const sanitized = this.sanitizeOtp(pasted);
    this.mfaForm.controls.code.setValue(sanitized, { emitEvent: true });
    this.mfaForm.controls.code.markAsTouched();
    const inputEl = this.mfaCodeInput()?.nativeElement;
    if (inputEl) {
      inputEl.value = sanitized;
    }
  }

  protected onSubmitMfa(): void {
    if (this.submitting()) {
      return;
    }

    this.mfaSubmitted.set(true);
    this.mfaForm.markAllAsTouched();
    this.mfaVersion.update((v) => v + 1);

    if (this.mfaForm.invalid) {
      return;
    }

    if (!this.networkStatus.isOnline()) {
      this.activeError.set({
        type: 'urn:ecole:problem:network-error',
        title: 'Connexion hors-ligne',
        status: 0,
        detail:
          'Vous êtes actuellement hors-ligne. Rétablissez votre connexion réseau pour valider le code.',
      });
      return;
    }

    this.activeError.set(null);
    this.serverFieldErrors.set({});
    this.submitting.set(true);

    const code = this.sanitizeOtp(this.mfaForm.controls.code.value);
    this.authService
      .verifyMfa(code)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (response) => this.handleAuthSuccess(response),
        error: (error: unknown) => this.handleAuthError(error),
      });
  }

  protected backToCredentials(): void {
    this.authService.cancelMfaChallenge();
    this.step.set('credentials');
    this.activeError.set(null);
    this.serverFieldErrors.set({});
    this.mfaEnrollment.set(null);
    this.enrollingMfa.set(false);
    this.mfaSubmitted.set(false);
    this.mfaForm.reset({ code: '' });
  }

  protected copyRequestId(requestId: string): void {
    if (!this.clipboard.copy(requestId)) {
      return;
    }
    this.copiedRequestId.set(true);
    if (this.copyRequestIdTimer !== null) {
      clearTimeout(this.copyRequestIdTimer);
    }
    this.copyRequestIdTimer = setTimeout(() => {
      this.copiedRequestId.set(false);
      this.copyRequestIdTimer = null;
    }, 2500);
  }

  protected copyEnrollmentSecret(secret: string): void {
    if (!this.clipboard.copy(secret)) {
      return;
    }
    this.copiedSecret.set(true);
    if (this.copySecretTimer !== null) {
      clearTimeout(this.copySecretTimer);
    }
    this.copySecretTimer = setTimeout(() => {
      this.copiedSecret.set(false);
      this.copySecretTimer = null;
    }, 2500);
  }

  private handleAuthSuccess(response: AuthResponse): void {
    if (response.status === 'AUTHENTICATED' && this.authService.isAuthenticated()) {
      const targetUrl = sanitizeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
      void this.router.navigateByUrl(targetUrl);
      return;
    }

    if (response.status === 'MFA_REQUIRED' || response.status === 'MFA_ENROLLMENT_REQUIRED') {
      this.activeError.set(null);
      this.serverFieldErrors.set({});
      this.mfaSubmitted.set(false);
      this.mfaForm.reset({ code: '' });
      this.step.set('mfa');

      if (response.status === 'MFA_ENROLLMENT_REQUIRED') {
        this.loadMfaEnrollment();
      } else {
        this.mfaEnrollment.set(null);
      }

      queueMicrotask(() => {
        this.mfaCodeInput()?.nativeElement.focus();
      });
    }
  }

  private loadMfaEnrollment(): void {
    this.enrollingMfa.set(true);
    this.authService
      .enrollMfa()
      .pipe(
        finalize(() => this.enrollingMfa.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (enrollment) => {
          this.mfaEnrollment.set(enrollment);
        },
        error: (error: unknown) => {
          this.handleAuthError(error);
        },
      });
  }

  private handleAuthError(error: unknown): void {
    const parsed = parseProblemDetail(error);
    const enriched = this.enrichAuthProblem(parsed);
    this.activeError.set(enriched);
    this.serverFieldErrors.set(extractFieldErrors(enriched));
  }

  private enrichAuthProblem(problem: ProblemDetail): ProblemDetail {
    if (problem.status === 401 && this.step() === 'credentials') {
      const hasGenericSessionMessage =
        problem.detail.includes('Votre session a expiré') ||
        problem.detail.includes('Authentification requise');
      return {
        ...problem,
        title: problem.title === 'Authentification requise' ? 'Identifiants invalides' : problem.title,
        detail: hasGenericSessionMessage
          ? 'Adresse e-mail, code établissement ou mot de passe incorrect. Vérifiez votre saisie et réessayez.'
          : problem.detail,
      };
    }

    if (problem.status === 423) {
      return {
        ...problem,
        title: problem.title === 'Ressource verrouillée' ? 'Compte temporairement verrouillé' : problem.title,
        detail:
          problem.detail === 'Cette ressource est temporairement verrouillée.'
            ? 'Votre compte est temporairement verrouillé suite à plusieurs tentatives échouées. Réessayez plus tard.'
            : problem.detail,
      };
    }

    if (problem.status === 429) {
      return {
        ...problem,
        title: problem.title || 'Trop de tentatives',
      };
    }

    return problem;
  }

  private sanitizeOtp(value: string): string {
    return value.replace(/\D+/g, '').slice(0, 6);
  }
}
