import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
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
  LucideX,
} from '@lucide/angular';
import { AuthService } from '../../core/auth/auth.service';
import { ROUTE_ROLES } from '../../core/config/role-access.config';
import { UserRole } from '../../core/models/user.model';
import { ThemeService } from '../../core/services/theme.service';

export type AdminNavIcon =
  | 'dashboard'
  | 'students'
  | 'grades'
  | 'attendance'
  | 'finance'
  | 'import'
  | 'settings';

export interface AdminNavItem {
  readonly route: string;
  readonly label: string;
  readonly icon: AdminNavIcon;
  readonly roles: readonly UserRole[];
}

export const ADMIN_NAV_ITEMS: readonly AdminNavItem[] = [
  {
    route: '/dashboard',
    label: 'Tableau de bord',
    icon: 'dashboard',
    roles: ROUTE_ROLES.dashboard,
  },
  {
    route: '/students',
    label: 'Élèves',
    icon: 'students',
    roles: ROUTE_ROLES.students,
  },
  {
    route: '/grades',
    label: 'Notes et bulletins',
    icon: 'grades',
    roles: ROUTE_ROLES.grades,
  },
  {
    route: '/attendance',
    label: 'Absences',
    icon: 'attendance',
    roles: ROUTE_ROLES.attendance,
  },
  {
    route: '/finance',
    label: 'Finances',
    icon: 'finance',
    roles: ROUTE_ROLES.finance,
  },
  {
    route: '/import',
    label: 'Import Excel',
    icon: 'import',
    roles: ROUTE_ROLES.import,
  },
  {
    route: '/settings',
    label: 'Paramètres',
    icon: 'settings',
    roles: ROUTE_ROLES.settings,
  },
];

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTrapFocus,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
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
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private readonly menuToggleButton = viewChild<ElementRef<HTMLButtonElement>>('menuToggleBtn');
  private triggerElement: HTMLElement | null = null;

  protected readonly drawerOpen = signal(false);

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
    this.authService.clearSession();
    void this.router.navigate(['/login']);
  }
}
