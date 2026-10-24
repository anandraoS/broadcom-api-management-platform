import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './core/auth.service';

interface NavItem { label: string; path: string; roles?: string[] }

export const NAV: NavItem[] = [
  { label: 'Traffic dashboard', path: '/dashboard' },
  { label: 'API registry', path: '/apis' },
  { label: 'Policy approvals', path: '/approvals', roles: ['ADMIN', 'POLICY_APPROVER'] },
  { label: 'Consumers', path: '/consumers' },
  { label: 'Incident investigation', path: '/incidents' },
  { label: 'Operational events', path: '/events' },
  { label: 'Audit history', path: '/audit', roles: ['ADMIN', 'OPERATOR', 'POLICY_APPROVER'] },
];

/** Application shell: role-aware navigation + responsive sidenav (drawer on narrow screens). */
@Component({
  selector: 'app-shell',
  imports: [MatToolbarModule, MatSidenavModule, MatListModule, MatButtonModule, RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar color="primary" role="banner">
      @if (narrow()) {
        <button mat-button type="button" (click)="drawer.toggle()" aria-label="Toggle navigation">Menu</button>
      }
      <span class="brand">API Control Plane</span>
      <span class="spacer"></span>
      <span class="who" data-testid="whoami">{{ auth.user()?.username }} ({{ auth.user()?.roles?.join(', ') }})</span>
      <button mat-button type="button" (click)="auth.logout()">Sign out</button>
    </mat-toolbar>
    <mat-sidenav-container class="container">
      <mat-sidenav #drawer [mode]="narrow() ? 'over' : 'side'" [opened]="!narrow()" role="navigation" aria-label="Main">
        <mat-nav-list>
          @for (item of items(); track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive="active" (click)="narrow() && drawer.close()">{{ item.label }}</a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content role="main"><div class="content"><router-outlet /></div></mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .container { height: calc(100vh - 64px); }
    mat-sidenav { width: 240px; }
    .content { padding: 16px; max-width: 1280px; margin: 0 auto; }
    .spacer { flex: 1; }
    .brand { font-weight: 600; margin-left: 8px; }
    .who { font-size: 0.85rem; margin-right: 8px; }
    a.active { font-weight: 600; background: rgba(0, 0, 0, 0.06); }
    @media (max-width: 600px) { .who { display: none; } }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly bp = inject(BreakpointObserver);
  protected readonly narrow = toSignal(this.bp.observe('(max-width: 900px)').pipe(map((s) => s.matches)), { initialValue: false });
  /** Navigation reflects the roles in the token; hiding an item never grants or removes server-side access. */
  protected readonly items = computed(() => NAV.filter((i) => !i.roles || this.auth.hasAnyRole(...i.roles)));
}
