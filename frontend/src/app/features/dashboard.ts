import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Subject, merge, timer } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Loader } from '../core/loader';
import { TrafficSummary } from '../core/models';
import { StateView } from '../shared/state-view';

/**
 * Traffic dashboard. Data comes from per-minute aggregates shipped by gateways, so it lags real traffic by up to
 * ~1 minute plus the shipper interval; use Grafana for second-level resolution and true latency percentiles.
 */
@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, PercentPipe, MatCardModule, MatFormFieldModule, MatSelectModule, RouterLink, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Traffic dashboard</h1>
    <div class="toolbar-row">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Window</mat-label>
        <mat-select [value]="window()" (selectionChange)="setWindow($event.value)" aria-label="Time window">
          @for (w of windows; track w) { <mat-option [value]="w">Last {{ w }} min</mat-option> }
        </mat-select>
      </mat-form-field>
      <span class="spacer"></span>
      <span class="muted">Auto-refresh every 30 s. Percentiles: see Grafana (gateway dashboard).</span>
    </div>
    <app-state [loading]="state.loading()" [error]="state.error()" [empty]="!(state.data()?.routes?.length)"
               emptyText="No traffic reported in this window yet. Send requests through the gateway and wait for the next telemetry flush.">
      @if (state.data(); as d) {
        <div class="card-grid" data-testid="kpis">
          <mat-card><mat-card-header><mat-card-subtitle>Requests</mat-card-subtitle></mat-card-header>
            <mat-card-content><div class="kpi">{{ d.totalRequests | number }}</div></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-subtitle>5xx error rate</mat-card-subtitle></mat-card-header>
            <mat-card-content><div class="kpi" [class.bad]="d.overallErrorRate > 0.05">{{ d.overallErrorRate | percent: '1.0-1' }}</div></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-subtitle>Throughput</mat-card-subtitle></mat-card-header>
            <mat-card-content><div class="kpi">{{ throughput() | number: '1.0-1' }}/s</div></mat-card-content></mat-card>
          <mat-card><mat-card-header><mat-card-subtitle>Rate-limited / auth failures</mat-card-subtitle></mat-card-header>
            <mat-card-content><div class="kpi">{{ totals().rl | number }} / {{ totals().auth | number }}</div></mat-card-content></mat-card>
        </div>
        <h2>By route</h2>
        <div class="table-wrap">
          <table role="table" aria-label="Route metrics" class="rt">
            <thead><tr><th>Route</th><th>Requests</th><th>5xx rate</th><th>Avg latency</th><th>Max latency</th><th>429</th><th>401</th><th>Timeouts</th><th>5xx share</th></tr></thead>
            <tbody>
              @for (r of d.routes; track r.route) {
                <tr>
                  <td><a routerLink="/incidents">{{ r.route }}</a></td>
                  <td>{{ r.requests | number }}</td>
                  <td [class.bad]="r.errorRate > 0.05">{{ r.errorRate | percent: '1.0-1' }}</td>
                  <td>{{ r.avgLatencyMs | number: '1.0-0' }} ms</td>
                  <td>{{ r.maxLatencyMs | number }} ms</td>
                  <td>{{ r.rateLimited }}</td><td>{{ r.authFailures }}</td><td>{{ r.timeouts }}</td>
                  <td><div class="bar" [attr.aria-label]="'5xx share ' + (r.errorRate * 100 | number: '1.0-1') + ' percent'"><div class="fill" [style.width.%]="Math.min(100, r.errorRate * 100)"></div></div></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </app-state>
  `,
  styles: `
    .kpi { font-size: 1.8rem; font-weight: 600; } .bad { color: #b3261e; }
    table.rt { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; }
    .bar { width: 100px; height: 8px; background: #eee; border-radius: 4px; } .fill { height: 8px; background: #d93025; border-radius: 4px; }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly Math = Math;
  protected readonly windows = [5, 15, 60, 240];
  protected readonly window = signal(15);
  protected readonly state = new Loader<TrafficSummary>();
  private readonly refresh$ = new Subject<void>();

  protected readonly totals = computed(() => {
    const rs = this.state.data()?.routes ?? [];
    return { rl: rs.reduce((a, r) => a + r.rateLimited, 0), auth: rs.reduce((a, r) => a + r.authFailures, 0) };
  });
  protected readonly throughput = computed(() => {
    const d = this.state.data();
    return d ? d.totalRequests / (d.windowMinutes * 60) : 0;
  });

  constructor() {
    // single stream: periodic tick OR manual refresh -> latest request wins (switchMap cancels the previous one)
    merge(timer(0, 30_000), this.refresh$)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.state.load(this.api.traffic(this.window())));
  }

  setWindow(w: number): void {
    this.window.set(w);
    this.refresh$.next();
  }
}
