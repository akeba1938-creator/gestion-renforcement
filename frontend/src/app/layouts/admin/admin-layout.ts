import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
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
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCalendar,
  LucideCalendarCheck,
  LucideGraduationCap,
  LucideLayoutDashboard,
  LucideLogOut,
  LucideMenu,
  LucideMoon,
  LucideSettings,
  LucideSun,
  LucideUpload,
  LucideUsers,
  LucideWallet,
  LucideWifi,
  LucideWifiOff,
  LucideX,
  provideLucideConfig,
} from '@lucide/angular';
import { AuthService } from '../../core/auth/auth.service';
import { ROUTE_ROLES } from '../../core/config/role-access.config';
import { UserRole } from '../../core/models/user.model';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ThemeService } from '../../core/services/theme.service';

export type AdminNavIcon =
  | 'dashboard'
  | 'students'
  | 'grades'
  | 'attendance'
  | 'finance'
  | 'import'
  | 'settings';

export type AdminNavGroup = 'main' | 'administration';

export interface AdminNavItem {
  readonly route: string;
  readonly label: string;
  readonly icon: AdminNavIcon;
  readonly group: AdminNavGroup;
  readonly badge?: string;
  readonly roles: readonly UserRole[];
}

export const ACADEMIC_YEARS: readonly string[] = ['2026–2027', '2025–2026'];

export const ADMIN_NAV_ITEMS: readonly AdminNavItem[] = [
  {
    route: '/dashboard',
    label: 'Tableau de bord',
    icon: 'dashboard',
    group: 'main',
    roles: ROUTE_ROLES.dashboard,
  },
  {
    route: '/students',
    label: 'Élèves',
    icon: 'students',
    group: 'main',
    roles: ROUTE_ROLES.students,
  },
  {
    route: '/grades',
    label: 'Notes et bulletins',
    icon: 'grades',
    group: 'main',
    roles: ROUTE_ROLES.grades,
  },
  {
    route: '/attendance',
    label: 'Absences',
    icon: 'attendance',
    group: 'main',
    roles: ROUTE_ROLES.attendance,
  },
  {
    route: '/finance',
    label: 'Finances',
    icon: 'finance',
    group: 'main',
    badge: 'Caisse',
    roles: ROUTE_ROLES.finance,
  },
  {
    route: '/import',
    label: 'Import Excel',
    icon: 'import',
    group: 'administration',
    roles: ROUTE_ROLES.import,
  },
  {
    route: '/settings',
    label: 'Paramètres',
    icon: 'settings',
    group: 'administration',
    roles: ROUTE_ROLES.settings,
  },
];

const ROLE_LABELS: Readonly<Record<UserRole, string>> = {
  SAAS_ADMIN: 'Administrateur SaaS',
  PROMOTER: 'Promoteur',
  DIRECTOR: 'Directeur',
  SECRETARY: 'Secrétariat',
  CASHIER: 'Caisse',
  CENSOR: 'Censeur',
  TEACHER: 'Enseignant',
  PARENT: 'Parent',
  STUDENT: 'Élève',
};

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideLucideConfig({ strokeWidth: 1.75 })],
  imports: [
    CdkTrapFocus,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LucideCalendar,
    LucideCalendarCheck,
    LucideGraduationCap,
    LucideLayoutDashboard,
    LucideLogOut,
    LucideMenu,
    LucideMoon,
    LucideSettings,
    LucideSun,
    LucideUpload,
    LucideUsers,
    LucideWallet,
    LucideWifi,
    LucideWifiOff,
    LucideX,
  ],
  host: {
    '(document:keydown.escape)': 'onEscapeKey($event)',
  },
  templateUrl: './admin-layout.html',
})
export class AdminLayoutComponent {
  protected readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  protected readonly networkStatus = inject(NetworkStatusService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly menuToggleButton = viewChild<ElementRef<HTMLButtonElement>>('menuToggleBtn');
  private triggerElement: HTMLElement | null = null;

  protected readonly drawerOpen = signal(false);
  protected readonly academicYears = ACADEMIC_YEARS;
  protected readonly selectedAcademicYear = signal<string>(ACADEMIC_YEARS[0]);

  /**
   * Filters navigation entries to match the active user's role so links never lead
   * to /forbidden. Ergonomics only — server enforces all real permissions.
   */
  protected readonly visibleNavItems = computed<readonly AdminNavItem[]>(() => {
    const currentRole = this.authService.role();
    if (!currentRole) {
      return [];
    }
    return ADMIN_NAV_ITEMS.filter((item) => item.roles.includes(currentRole));
  });

  protected readonly primaryNavItems = computed<readonly AdminNavItem[]>(() =>
    this.visibleNavItems().filter((item) => item.group === 'main')
  );

  protected readonly adminNavItems = computed<readonly AdminNavItem[]>(() =>
    this.visibleNavItems().filter((item) => item.group === 'administration')
  );

  protected readonly userInitials = computed<string>(() => {
    const fullName = this.authService.user()?.fullName?.trim();
    if (!fullName) {
      return 'ES';
    }
    const parts = fullName.split(/\s+/).filter(Boolean);
    if (parts.length === 1) {
      return parts[0].slice(0, 2).toUpperCase();
    }
    return `${parts[0][0] ?? ''}${parts[parts.length - 1][0] ?? ''}`.toUpperCase();
  });

  protected readonly userRoleLabel = computed<string>(() => {
    const role = this.authService.role();
    return role ? ROLE_LABELS[role] : 'Direction';
  });

  protected onAcademicYearChange(event: Event): void {
    const target = event.target as HTMLSelectElement | null;
    if (target?.value) {
      this.selectedAcademicYear.set(target.value);
    }
  }

  protected toggleDrawer(): void {
    if (this.drawerOpen()) {
      this.closeDrawer(true);
    } else {
      this.openDrawer();
    }
  }

  protected openDrawer(): void {
    const active = this.document.activeElement;
    this.triggerElement =
      active instanceof HTMLElement
        ? active
        : (this.menuToggleButton()?.nativeElement ?? null);
    this.drawerOpen.set(true);
  }

  protected closeDrawer(restoreFocus = true): void {
    if (!this.drawerOpen()) {
      return;
    }
    this.drawerOpen.set(false);
    if (restoreFocus) {
      const target = this.triggerElement ?? this.menuToggleButton()?.nativeElement ?? null;
      this.triggerElement = null;
      if (target && target.isConnected) {
        target.focus();
      }
    } else {
      this.triggerElement = null;
    }
  }

  protected onEscapeKey(event: Event): void {
    if (this.drawerOpen()) {
      event.preventDefault();
      this.closeDrawer(true);
    }
  }

  protected logout(): void {
    this.authService
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.router.navigate(['/login']);
      });
  }
}
