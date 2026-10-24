import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/interceptors';
import { Loader } from '../core/loader';
import { Api, KeyIssued, KeyView, PageView, Subscription } from '../core/models';
import { StateView } from '../shared/state-view';

@Component({
  selector: 'app-consumer-detail',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-state [loading]="subs.loading()" [error]="subs.error()">
      <h1 class="page-title">Consumer #{{ id() }}</h1>

      <h2>Subscriptions</h2>
      @if (!subs.data()?.length) { <div class="empty">Not subscribed to any API. A key only works for APIs the consumer is subscribed to.</div> }
      @else {
        <div class="table-wrap"><table class="t" role="table" aria-label="Subscriptions">
          <thead><tr><th>API</th><th>Status</th><th></th></tr></thead>
          <tbody>@for (s of subs.data(); track s.id) {
            <tr><td><code>{{ s.apiSlug }}</code></td><td><span class="chip" [class]="'chip ' + (s.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE')">{{ s.status }}</span></td>
              <td>@if (canManage() && s.status === 'ACTIVE') { <button mat-button type="button" (click)="cancel(s)">Cancel</button> }</td></tr>
          }</tbody></table></div>
      }
      @if (canManage()) {
        <form [formGroup]="subForm" (ngSubmit)="subscribe()" class="toolbar-row add" aria-label="Subscribe to an API">
          <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>API</mat-label>
            <mat-select formControlName="apiId" (openedChange)="$event && loadApis()">
              @for (a of apis(); track a.id) { <mat-option [value]="a.id">{{ a.name }} ({{ a.slug }})</mat-option> }
            </mat-select></mat-form-field>
          <button mat-stroked-button type="submit" [disabled]="subForm.invalid">Subscribe</button>
        </form>
      }

      <h2>API keys</h2>
      @if (canManage()) {
        @if (issued(); as k) {
          <div class="secret" role="alert">
            <strong>Copy this key now - it is shown once and only a hash is stored.</strong>
            <pre class="mono" data-testid="issued-key">{{ k.apiKey }}</pre>
            <button mat-button type="button" (click)="dismissKey()">I have stored it</button>
          </div>
        }
        <form [formGroup]="keyForm" (ngSubmit)="issue()" class="toolbar-row" aria-label="Issue API key">
          <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Valid for (days, max 365)</mat-label>
            <input matInput type="number" formControlName="days" /></mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="keyForm.invalid">Issue key</button>
        </form>
        @if (!keys.data()?.length) { <div class="empty">No keys issued.</div> }
        @else {
          <div class="table-wrap"><table class="t" role="table" aria-label="API keys">
            <thead><tr><th>Prefix</th><th>Expires</th><th>Status</th><th></th></tr></thead>
            <tbody>@for (k of keys.data(); track k.id) {
              <tr><td><code>{{ k.keyPrefix }}</code></td><td>{{ k.expiresAt.slice(0, 10) }}</td>
                <td><span class="chip" [class]="'chip ' + (k.revoked ? 'REJECTED' : 'ACTIVE')">{{ k.revoked ? 'REVOKED' : 'ACTIVE' }}</span></td>
                <td>@if (!k.revoked) { <button mat-button type="button" (click)="revoke(k)">Revoke</button> }</td></tr>
            }</tbody></table></div>
        }
        <p class="muted">Rotate by issuing a new key, switching the consumer over, then revoking the old one.</p>
      } @else { <p class="muted">Only administrators and API owners can manage keys.</p> }
    </app-state>
  `,
  styles: `table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; } .add { margin: 12px 0; } .secret { background: #fff8e1; padding: 12px; border-radius: 8px; margin-bottom: 12px; }`,
})
export class ConsumerDetailPage implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly snack = inject(MatSnackBar);
  protected readonly subs = new Loader<Subscription[]>();
  protected readonly keys = new Loader<KeyView[]>();
  protected readonly apis = signal<Api[]>([]);
  protected readonly issued = signal<KeyIssued | null>(null);
  protected readonly canManage = () => this.auth.hasAnyRole('ADMIN', 'API_OWNER');
  protected readonly subForm = this.fb.nonNullable.group({ apiId: [0, [Validators.min(1)]] });
  protected readonly keyForm = this.fb.nonNullable.group({ days: [90, [Validators.required, Validators.min(1), Validators.max(365)]] });

  ngOnInit(): void { this.reload(); }
  private cid(): number { return Number(this.id()); }
  private reload(): void {
    this.subs.load(this.api.subscriptions(this.cid()));
    if (this.canManage()) this.keys.load(this.api.keys(this.cid()));
  }

  protected loadApis(): void {
    if (this.apis().length) return;
    this.api.searchApis({ size: 100 }).subscribe((p: PageView<Api>) => this.apis.set(p.content));
  }

  private act<T>(o: Observable<T>, ok: string, then?: (r: T) => void): void {
    o.subscribe({
      next: (r) => { this.snack.open(ok, 'OK', { duration: 3000 }); then?.(r); this.reload(); },
      error: (e: unknown) => this.snack.open(describeError(e), 'Dismiss', { duration: 8000 }),
    });
  }
  protected subscribe(): void { this.act(this.api.subscribe(this.cid(), this.subForm.getRawValue().apiId), 'Subscribed'); }
  protected cancel(s: Subscription): void { this.act(this.api.cancelSubscription(this.cid(), s.id), 'Subscription cancelled'); }
  protected issue(): void { this.act(this.api.issueKey(this.cid(), this.keyForm.getRawValue().days), 'Key issued', (k) => this.issued.set(k)); }
  protected revoke(k: KeyView): void {
    if (confirm(`Revoke key ${k.keyPrefix}? Calls using it fail within one gateway poll interval.`)) this.act(this.api.revokeKey(this.cid(), k.id), 'Key revoked');
  }
  protected dismissKey(): void { this.issued.set(null); }
}
