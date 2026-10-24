import { JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../core/api.service';
import { Loader } from '../core/loader';
import { IncidentReport } from '../core/models';
import { StateView } from '../shared/state-view';

/**
 * Investigation view. Findings are produced by deterministic threshold rules on the server and always show the
 * evidence that triggered them. Nothing on this page is AI-generated.
 */
@Component({
  selector: 'app-incidents',
  imports: [JsonPipe, MatFormFieldModule, MatSelectModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Incident investigation</h1>
    <div class="toolbar-row">
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Look-back window</mat-label>
        <mat-select [value]="window()" (selectionChange)="setWindow($event.value)">
          @for (w of [15, 30, 60, 240, 1440]; track w) { <mat-option [value]="w">Last {{ w }} min</mat-option> }
        </mat-select></mat-form-field>
      <span class="spacer"></span>
    </div>
    <app-state [loading]="rep.loading()" [error]="rep.error()">
      @if (rep.data(); as r) {
        <p class="muted" role="note">{{ r.note }}</p>
        <h2>Findings ({{ r.findings.length }})</h2>
        @if (!r.findings.length) { <div class="empty">No rule fired in this window - telemetry looks healthy.</div> }
        <div class="card-grid">
          @for (f of r.findings; track f.rule + f.route) {
            <section class="finding" [attr.aria-label]="f.rule">
              <h3><span class="chip" [class]="'chip ' + f.severity">{{ f.severity }}</span> {{ f.rule }} @if (f.route) { <small>on {{ f.route }}</small> }</h3>
              <p>{{ f.summary }}</p>
              <h4>Evidence (measured)</h4>
              <pre class="mono">{{ f.evidence | json }}</pre>
              <h4>Suggested investigation steps (rule-based)</h4>
              <ol>@for (s of f.suggestedSteps; track s) { <li>{{ s }}</li> }</ol>
            </section>
          }
        </div>
        <h2>Recent configuration changes (possible cause)</h2>
        @if (!r.recentConfigChanges.length) { <div class="empty">No configuration changes in this window.</div> }
        @else {
          <div class="table-wrap"><table class="t" role="table" aria-label="Recent changes"><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Detail</th></tr></thead>
            <tbody>@for (a of r.recentConfigChanges; track a.id) {
              <tr><td>{{ a.ts.slice(0, 19).replace('T', ' ') }}</td><td>{{ a.actor }}</td><td>{{ a.action }}</td><td>{{ a.entityType }} {{ a.entityId }}</td><td>{{ a.detail }}</td></tr>
            }</tbody></table></div>
        }
        <h2>Correlated events</h2>
        @if (!r.recentEvents.length) { <div class="empty">No operational events in this window.</div> }
        @else {
          <div class="table-wrap"><table class="t" role="table" aria-label="Recent events"><thead><tr><th>Time</th><th>Severity</th><th>Type</th><th>Route</th><th>Message</th></tr></thead>
            <tbody>@for (e of r.recentEvents; track e.id) {
              <tr><td>{{ e.ts.slice(0, 19).replace('T', ' ') }}</td><td><span class="chip" [class]="'chip ' + e.severity">{{ e.severity }}</span></td><td>{{ e.type }}</td><td>{{ e.routeSlug ?? '-' }}</td><td>{{ e.message }}</td></tr>
            }</tbody></table></div>
        }
        <p class="muted">Next: open the Grafana gateway dashboard for percentiles, and Jaeger with the correlation id from a failing request for the trace.</p>
      }
    </app-state>
  `,
  styles: `.finding { background: #fff; border-radius: 8px; padding: 16px; } table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; vertical-align: top; } h4 { margin: 8px 0 4px; }`,
})
export class IncidentsPage {
  private readonly api = inject(ApiService);
  protected readonly window = signal(30);
  protected readonly rep = new Loader<IncidentReport>();

  constructor() { this.reload(); }
  protected setWindow(w: number): void { this.window.set(w); this.reload(); }
  private reload(): void { this.rep.load(this.api.incidents(this.window())); }
}
