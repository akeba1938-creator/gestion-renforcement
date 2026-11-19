import { Component, inject, signal } from '@angular/core';
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
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
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
  templateUrl: './admin-layout.html',
})
export class AdminLayoutComponent {
  protected readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);

  protected readonly drawerOpen = signal(false);

  protected toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected logout(): void {
    this.authService.clearSession();
    void this.router.navigate(['/login']);
  }
}
