import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/interceptors';
import { Loader } from '../core/loader';
import { Policy } from '../core/models';
import { StateView } from '../shared/state-view';

/** Four-eyes review queue: a policy cannot be approved by the person who submitted it (enforced by the server). */
@Component({
  selector: 'app-approvals',
  imports: [MatButtonModule, RouterLink, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Policy approvals</h1>
    <app-state [loading]="pending.loading()" [error]="pending.error()" [empty]="!pending.data()?.length" emptyText="No policy changes are waiting for review.">
      @if (pending.data(); as list) {
        <div class="card-grid">
          @for (p of list; track p.id) {
            <div class="pcard">
              <h3><a [routerLink]="['/apis', p.apiId]">API #{{ p.apiId }}</a> - policy v{{ p.versionNo }}</h3>
              <p class="muted">Submitted by <strong>{{ p.submittedBy }}</strong> at {{ p.createdAt.slice(0, 19).replace('T', ' ') }}</p>
              <p>{{ p.comment || 'No comment' }}</p>
              <ul>
                <li>Auth: {{ p.config.auth }}</li>
                <li>Methods: {{ p.config.allowedMethods.join(', ') }}</li>
                <li>Rate: {{ p.config.rateLimitPerMinute }}/min ({{ p.config.rateAlgorithm }}, burst {{ p.config.burst }})</li>
                <li>Timeout: {{ p.config.timeoutMs }} ms - max body {{ p.config.maxRequestBytes }} B - retries {{ p.config.retryEnabled ? 'on' : 'off' }}</li>
                <li>IP allowlist: {{ p.config.ipAllowCidrs?.join(', ') ?? 'none' }}</li>
              </ul>
              @if (p.submittedBy === me()) {
                <p class="muted" role="note">You submitted this change; another approver must review it.</p>
              } @else {
                <button mat-flat-button color="primary" type="button" (click)="decide(p, true)">Approve</button>
                <button mat-stroked-button type="button" (click)="decide(p, false)">Reject</button>
              }
            </div>
          }
        </div>
      }
    </app-state>
  `,
  styles: `.pcard { background: #fff; border-radius: 8px; padding: 16px; }`,
})
export class ApprovalsPage {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly auth = inject(AuthService);
  protected readonly pending = new Loader<Policy[]>();
  protected readonly me = () => this.auth.user()?.username;

  constructor() { this.reload(); }

  private reload(): void { this.pending.load(this.api.pendingPolicies()); }

  protected decide(p: Policy, approve: boolean): void {
    (approve ? this.api.approve(p.id) : this.api.reject(p.id)).subscribe({
      next: () => { this.snack.open(approve ? 'Approved and activated' : 'Rejected', 'OK', { duration: 3000 }); this.reload(); },
      error: (e: unknown) => this.snack.open(describeError(e), 'Dismiss', { duration: 8000 }),
    });
  }
}
