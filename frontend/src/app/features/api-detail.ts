import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/interceptors';
import { Loader } from '../core/loader';
import { Api, Lifecycle, Policy, PolicyConfig } from '../core/models';
import { StateView } from '../shared/state-view';

const NEXT: Record<Lifecycle, Lifecycle[]> = {
  DRAFT: ['ACTIVE', 'INACTIVE'], ACTIVE: ['INACTIVE', 'DEPRECATED'], INACTIVE: ['ACTIVE'], DEPRECATED: ['INACTIVE'],
};
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];

@Component({
  selector: 'app-api-detail',
  imports: [ReactiveFormsModule, MatTabsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-state [loading]="apiState.loading()" [error]="apiState.error()" [empty]="!apiState.data()">
      @if (apiState.data(); as a) {
        <h1 class="page-title">{{ a.name }} <span class="chip" [class]="'chip ' + a.lifecycle">{{ a.lifecycle }}</span></h1>
        <p class="muted"><code>{{ a.slug }}</code> - owned by team <strong>{{ a.ownerTeam }}</strong> - record version {{ a.version }}</p>
        @if (!canModify()) { <p class="muted" role="note">Read-only: you are not an administrator or an owner of team {{ a.ownerTeam }}. The server enforces this too.</p> }

        <mat-tab-group animationDuration="0ms" (selectedTabChange)="onTab($event.index)">
          <mat-tab label="Overview">
            <div class="pad">
              <form [formGroup]="edit" (ngSubmit)="saveEdit(a)" class="stack" novalidate aria-label="Edit API">
                <mat-form-field appearance="outline"><mat-label>Name</mat-label><input matInput formControlName="name" />
                  @if (edit.controls.name.touched && edit.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
                <mat-form-field appearance="outline"><mat-label>Description</mat-label><textarea matInput rows="3" formControlName="description" maxlength="1000"></textarea></mat-form-field>
                @if (canModify()) { <div><button mat-flat-button color="primary" type="submit" [disabled]="busy()">Save</button></div> }
              </form>
              @if (canModify()) {
                <h3>Lifecycle</h3>
                <div class="toolbar-row">
                  @for (t of nextStates(a); track t) { <button mat-stroked-button type="button" (click)="transition(a, t)" [disabled]="busy()">Move to {{ t }}</button> }
                </div>
                <p class="muted">An API is published to gateways only when it is ACTIVE, has an ACTIVE policy and at least one valid target.</p>
              }
            </div>
          </mat-tab>

          <mat-tab label="Versions & targets">
            <div class="pad">
              @for (v of a.versions; track v.id) {
                <h3><code>{{ v.label }}</code> <span class="chip" [class]="'chip ' + v.status">{{ v.status }}</span> <span class="muted">gateway path: /gw{{ v.basePath }}</span></h3>
                @if (!v.targets.length) { <div class="empty">No backend targets yet - this version is not routable.</div> }
                @else {
                  <div class="table-wrap"><table class="t" role="table" aria-label="Targets of {{ v.label }}">
                    <thead><tr><th>URL</th><th>Weight</th><th>Health path</th><th></th></tr></thead>
                    <tbody>@for (t of v.targets; track t.id) {
                      <tr><td><code>{{ t.url }}</code></td><td>{{ t.weight }}</td><td><code>{{ t.healthPath }}</code></td>
                        <td>@if (canModify()) { <button mat-button type="button" (click)="removeTarget(a, v.id, t.id)">Remove</button> }</td></tr>
                    }</tbody></table></div>
                }
                @if (canModify()) {
                  <form [formGroup]="targetForm" (ngSubmit)="addTarget(a, v.id)" class="grid2 add" novalidate [attr.aria-label]="'Add target to ' + v.label">
                    <mat-form-field appearance="outline"><mat-label>Backend URL</mat-label><input matInput formControlName="url" placeholder="http://orders-service-1:9001" />
                      @if (targetForm.controls.url.touched && targetForm.controls.url.invalid) { <mat-error>Enter an http(s) URL</mat-error> }</mat-form-field>
                    <mat-form-field appearance="outline"><mat-label>Weight (1-100)</mat-label><input matInput type="number" formControlName="weight" /></mat-form-field>
                    <div><button mat-stroked-button type="submit" [disabled]="busy()">Add target</button></div>
                  </form>
                }
              }
              @if (targetErrors().length) {
                <div class="error" role="alert"><strong>Target rejected by destination policy (SSRF protection):</strong>
                  <ul>@for (e of targetErrors(); track e) { <li>{{ e }}</li> }</ul></div>
              }
              @if (canModify()) {
                <h3>Add version</h3>
                <form [formGroup]="versionForm" (ngSubmit)="addVersion(a)" class="grid2" novalidate aria-label="Add version">
                  <mat-form-field appearance="outline"><mat-label>Label</mat-label><input matInput formControlName="label" placeholder="v2" />
                    @if (versionForm.controls.label.touched && versionForm.controls.label.invalid) { <mat-error>1-20 chars: letters, digits, . _ -</mat-error> }</mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Base path</mat-label><input matInput formControlName="basePath" placeholder="/orders/v2" />
                    @if (versionForm.controls.basePath.touched && versionForm.controls.basePath.invalid) { <mat-error>Lowercase path starting with /</mat-error> }</mat-form-field>
                  <div><button mat-stroked-button type="submit" [disabled]="busy()">Add version</button></div>
                </form>
              }
            </div>
          </mat-tab>

          <mat-tab label="Policies">
            <div class="pad">
              <app-state [loading]="history.loading()" [error]="history.error()" [empty]="!history.data()?.length" emptyText="No policy has been submitted yet - this API will not be routable until one is approved.">
                @if (history.data(); as h) {
                  <div class="table-wrap"><table class="t" role="table" aria-label="Policy history">
                    <thead><tr><th>Version</th><th>Status</th><th>Auth</th><th>Rate</th><th>Timeout</th><th>Submitted by</th><th>Decided by</th><th>Comment</th><th></th></tr></thead>
                    <tbody>@for (p of h; track p.id) {
                      <tr>
                        <td>v{{ p.versionNo }}</td><td><span class="chip" [class]="'chip ' + p.status">{{ p.status }}</span></td>
                        <td>{{ p.config.auth }}</td><td>{{ p.config.rateLimitPerMinute }}/min {{ p.config.rateAlgorithm }}</td><td>{{ p.config.timeoutMs }} ms</td>
                        <td>{{ p.submittedBy }}</td><td>{{ p.approvedBy ?? '-' }}</td><td>{{ p.comment }}</td>
                        <td class="actions">
                          @if (p.status === 'PENDING_APPROVAL' && isApprover()) {
                            <button mat-button type="button" (click)="decide(p, true)">Approve</button>
                            <button mat-button type="button" (click)="decide(p, false)">Reject</button>
                          }
                          @if ((p.status === 'ACTIVE' || p.status === 'SUPERSEDED') && isApprover()) {
                            <button mat-button type="button" (click)="revert(a, p)">Revert to v{{ p.versionNo }}</button>
                          }
                        </td>
                      </tr>
                    }</tbody></table></div>
                }
              </app-state>

              @if (canModify()) {
                <h3>New policy version</h3>
                <p class="muted">Changes are validated, then need approval from a different user with the POLICY_APPROVER role before gateways receive them. Propagation is by polling (seconds), not instantaneous.</p>
                <form [formGroup]="policyForm" (ngSubmit)="submitPolicy(a)" class="grid2" novalidate aria-label="Policy editor">
                  <mat-form-field appearance="outline"><mat-label>Authentication</mat-label>
                    <mat-select formControlName="auth"><mat-option value="API_KEY">API key</mat-option><mat-option value="JWT">JWT (OIDC)</mat-option><mat-option value="NONE">None (read-only methods only)</mat-option></mat-select></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Required role (JWT roles claim)</mat-label><input matInput formControlName="requiredRole" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Allowed methods</mat-label>
                    <mat-select formControlName="allowedMethods" multiple>@for (m of methods; track m) { <mat-option [value]="m">{{ m }}</mat-option> }</mat-select></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Rate limit per minute</mat-label><input matInput type="number" formControlName="rateLimitPerMinute" />
                    @if (policyForm.controls.rateLimitPerMinute.invalid) { <mat-error>1 - 6,000,000</mat-error> }</mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Burst</mat-label><input matInput type="number" formControlName="burst" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Algorithm</mat-label>
                    <mat-select formControlName="rateAlgorithm"><mat-option value="TOKEN_BUCKET">Token bucket</mat-option><mat-option value="SLIDING_WINDOW">Sliding window</mat-option><mat-option value="FIXED_WINDOW">Fixed window</mat-option></mat-select></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>End-to-end timeout (ms)</mat-label><input matInput type="number" formControlName="timeoutMs" />
                    @if (policyForm.controls.timeoutMs.invalid) { <mat-error>100 - 60000</mat-error> }</mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Max request bytes</mat-label><input matInput type="number" formControlName="maxRequestBytes" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>IP allowlist (CIDRs, comma separated)</mat-label><input matInput formControlName="ipAllowCidrs" placeholder="10.0.0.0/8, 192.168.1.0/24" /></mat-form-field>
                  <mat-form-field appearance="outline"><mat-label>Comment for reviewer</mat-label><input matInput formControlName="comment" maxlength="500" /></mat-form-field>
                  <mat-checkbox formControlName="retryEnabled">Retry safe methods (GET/HEAD/OPTIONS) on transient failures</mat-checkbox>
                  <div class="toolbar-row">
                    <button mat-stroked-button type="button" (click)="validate()" [disabled]="busy()">Validate (dry run)</button>
                    <button mat-flat-button color="primary" type="submit" [disabled]="busy()">Submit for approval</button>
                  </div>
                </form>
                @if (validation(); as v) {
                  @if (v.valid) { <div class="empty" role="status">Policy is valid.</div> }
                  @else { <div class="error" role="alert"><ul>@for (e of v.errors; track e) { <li>{{ e }}</li> }</ul></div> }
                }
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      }
    </app-state>
  `,
  styles: `.pad { padding: 16px 0; } table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; } .add { margin: 8px 0 16px; } .actions button { white-space: nowrap; }`,
})
export class ApiDetailPage implements OnInit {
  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly snack = inject(MatSnackBar);
  protected readonly methods = METHODS;
  protected readonly apiState = new Loader<Api>();
  protected readonly history = new Loader<Policy[]>();
  protected readonly busy = signal(false);
  protected readonly targetErrors = signal<string[]>([]);
  protected readonly validation = signal<{ valid: boolean; errors: string[] } | null>(null);
  protected readonly canModify = computed(() => {
    const a = this.apiState.data();
    const u = this.auth.user();
    return !!a && !!u && (u.roles.includes('ADMIN') || (u.roles.includes('API_OWNER') && u.team === a.ownerTeam));
  });
  protected readonly isApprover = () => this.auth.hasAnyRole('ADMIN', 'POLICY_APPROVER');

  protected readonly edit = this.fb.nonNullable.group({ name: ['', [Validators.required, Validators.maxLength(160)]], description: '' });
  protected readonly targetForm = this.fb.nonNullable.group({
    url: ['', [Validators.required, Validators.pattern(/^https?:\/\/[^\s]+$/)]], weight: [1, [Validators.min(1), Validators.max(100)]],
  });
  protected readonly versionForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,20}$/)]],
    basePath: ['', [Validators.required, Validators.pattern(/^\/[a-z0-9][a-z0-9/_-]{0,100}$/)]],
  });
  protected readonly policyForm = this.fb.nonNullable.group({
    auth: 'API_KEY' as PolicyConfig['auth'], requiredRole: '', allowedMethods: [['GET'], [Validators.required]],
    rateLimitPerMinute: [100, [Validators.required, Validators.min(1), Validators.max(6_000_000)]], burst: [20, [Validators.required, Validators.min(1)]],
    rateAlgorithm: 'TOKEN_BUCKET' as PolicyConfig['rateAlgorithm'], timeoutMs: [3000, [Validators.required, Validators.min(100), Validators.max(60_000)]],
    maxRequestBytes: [65536, [Validators.required, Validators.min(0)]], ipAllowCidrs: '', retryEnabled: true, comment: '',
  });

  constructor() {
    // keep the edit form in sync whenever fresh API data arrives
    effect(() => {
      const a = this.apiState.data();
      if (a) this.edit.patchValue({ name: a.name, description: a.description ?? '' });
    });
  }

  ngOnInit(): void { this.loadApi(); }

  private apiId(): number { return Number(this.id()); }

  protected loadApi(): void { this.apiState.load(this.api.getApi(this.apiId())); }

  protected onTab(i: number): void { if (i === 2 && !this.history.data()) this.history.load(this.api.policyHistory(this.apiId())); }
  protected nextStates(a: Api): Lifecycle[] { return NEXT[a.lifecycle]; }

  private run<T>(obs: import('rxjs').Observable<T>, ok: string, then?: (r: T) => void): void {
    this.busy.set(true);
    obs.subscribe({
      next: (r) => { this.busy.set(false); this.snack.open(ok, 'OK', { duration: 3000 }); then?.(r); },
      error: (e: unknown) => { this.busy.set(false); this.snack.open(describeError(e), 'Dismiss', { duration: 10000 }); },
    });
  }

  protected saveEdit(a: Api): void {
    if (this.edit.invalid) { this.edit.markAllAsTouched(); return; }
    const v = this.edit.getRawValue();
    this.run(this.api.updateApi(a.id, { ...v, version: a.version }), 'Saved', () => this.loadApi());
  }
  protected transition(a: Api, t: Lifecycle): void { this.run(this.api.transition(a.id, t), `Moved to ${t}`, () => this.loadApi()); }

  protected addVersion(a: Api): void {
    if (this.versionForm.invalid) { this.versionForm.markAllAsTouched(); return; }
    this.run(this.api.addVersion(a.id, this.versionForm.getRawValue()), 'Version added', () => { this.versionForm.reset(); this.loadApi(); });
  }

  protected addTarget(a: Api, versionId: number): void {
    if (this.targetForm.invalid) { this.targetForm.markAllAsTouched(); return; }
    this.targetErrors.set([]);
    this.busy.set(true);
    this.api.addTarget(a.id, versionId, this.targetForm.getRawValue()).subscribe({
      next: () => { this.busy.set(false); this.targetForm.reset({ url: '', weight: 1 }); this.loadApi(); },
      error: (e: unknown) => {
        this.busy.set(false);
        const body = (e as { error?: { errors?: string[] } }).error;
        if (body?.errors?.length) this.targetErrors.set(body.errors); else this.snack.open(describeError(e), 'Dismiss', { duration: 8000 });
      },
    });
  }
  protected removeTarget(a: Api, vid: number, tid: number): void { this.run(this.api.removeTarget(a.id, vid, tid), 'Target removed', () => this.loadApi()); }

  private buildConfig(): PolicyConfig {
    const v = this.policyForm.getRawValue();
    const cidrs = v.ipAllowCidrs.split(',').map((s) => s.trim()).filter(Boolean);
    return {
      auth: v.auth, requiredRole: v.requiredRole.trim() || null, allowedMethods: v.allowedMethods, rateLimitPerMinute: Number(v.rateLimitPerMinute),
      burst: Number(v.burst), rateAlgorithm: v.rateAlgorithm, timeoutMs: Number(v.timeoutMs), maxRequestBytes: Number(v.maxRequestBytes),
      ipAllowCidrs: cidrs.length ? cidrs : null, retryEnabled: v.retryEnabled,
    };
  }

  protected validate(): void {
    this.api.validatePolicy(this.buildConfig()).subscribe({
      next: (r) => this.validation.set(r),
      error: (e: unknown) => this.snack.open(describeError(e), 'Dismiss', { duration: 8000 }),
    });
  }

  protected submitPolicy(a: Api): void {
    if (this.policyForm.invalid) { this.policyForm.markAllAsTouched(); return; }
    this.validation.set(null);
    this.run(this.api.submitPolicy(a.id, this.buildConfig(), this.policyForm.getRawValue().comment), 'Submitted for approval',
      () => this.history.load(this.api.policyHistory(a.id)));
  }

  protected decide(p: Policy, approve: boolean): void {
    this.run(approve ? this.api.approve(p.id) : this.api.reject(p.id), approve ? 'Approved and activated' : 'Rejected',
      () => this.history.load(this.api.policyHistory(p.apiId)));
  }
  protected revert(a: Api, p: Policy): void {
    if (!confirm(`Create a new active version from v${p.versionNo}? Gateways pick it up on their next poll.`)) return;
    this.run(this.api.revert(a.id, p.versionNo), 'Reverted', () => this.history.load(this.api.policyHistory(a.id)));
  }
}
