import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Loader } from '../core/loader';
import { OpEvent, PageView } from '../core/models';
import { StateView } from '../shared/state-view';

@Component({
  selector: 'app-events',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Operational events</h1>
    <form [formGroup]="f" class="toolbar-row" role="search" aria-label="Filter events">
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Type</mat-label>
        <mat-select formControlName="type"><mat-option value="">All</mat-option>
          @for (t of types; track t) { <mat-option [value]="t">{{ t }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Severity</mat-label>
        <mat-select formControlName="severity"><mat-option value="">All</mat-option><mat-option value="INFO">INFO</mat-option><mat-option value="WARN">WARN</mat-option><mat-option value="ERROR">ERROR</mat-option></mat-select></mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Route (api/version)</mat-label><input matInput formControlName="route" maxlength="80" /></mat-form-field>
    </form>
    <app-state [loading]="list.loading()" [error]="list.error()" [empty]="!list.data()?.content?.length" emptyText="No events match these filters.">
      @if (list.data(); as p) {
        <div class="table-wrap"><table class="t" role="table" aria-label="Events">
          <thead><tr><th>Time</th><th>Severity</th><th>Type</th><th>Route</th><th>Message</th><th>Correlation id</th></tr></thead>
          <tbody>@for (e of p.content; track e.id) {
            <tr><td>{{ e.ts.slice(0, 19).replace('T', ' ') }}</td><td><span class="chip" [class]="'chip ' + e.severity">{{ e.severity }}</span></td>
              <td>{{ e.type }}</td><td>{{ e.routeSlug ?? '-' }}</td><td>{{ e.message }}</td><td><code>{{ e.correlationId ?? '-' }}</code></td></tr>
          }</tbody></table></div>
        <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" aria-label="Select page of events" />
      }
    </app-state>
  `,
  styles: `table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; vertical-align: top; }`,
})
export class EventsPage {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  protected readonly types = ['CIRCUIT_OPEN', 'TARGET_UNHEALTHY', 'TARGET_RECOVERED', 'SNAPSHOT_STALE', 'INVALID_CONFIG', 'RATE_LIMITER_DEGRADED', 'RATE_LIMITER_RECOVERED', 'POOL_EXHAUSTED', 'QUEUE_BACKLOG'];
  protected readonly list = new Loader<PageView<OpEvent>>();
  protected readonly f = this.fb.nonNullable.group({ type: '', severity: '', route: '' });
  private page = 0;
  private size = 25;

  constructor() {
    this.f.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => { this.page = 0; this.reload(); });
    this.reload();
  }
  protected onPage(e: PageEvent): void { this.page = e.pageIndex; this.size = e.pageSize; this.reload(); }
  private reload(): void { this.list.load(this.api.events({ ...this.f.getRawValue(), page: this.page, size: this.size })); }
}
