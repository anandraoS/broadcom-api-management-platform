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
import { AuditEvent, PageView } from '../core/models';
import { StateView } from '../shared/state-view';

/** Append-only history of administrative actions. Detail text is rendered as text (never as HTML). */
@Component({
  selector: 'app-audit',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Audit history</h1>
    <form [formGroup]="f" class="toolbar-row" role="search" aria-label="Filter audit events">
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Entity type</mat-label>
        <mat-select formControlName="entityType"><mat-option value="">All</mat-option><mat-option value="API">API</mat-option><mat-option value="CONSUMER">CONSUMER</mat-option></mat-select></mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Entity id</mat-label><input matInput formControlName="entityId" maxlength="40" /></mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Actor</mat-label><input matInput formControlName="actor" maxlength="80" /></mat-form-field>
    </form>
    <app-state [loading]="list.loading()" [error]="list.error()" [empty]="!list.data()?.content?.length" emptyText="No audit events match.">
      @if (list.data(); as p) {
        <div class="table-wrap"><table class="t" role="table" aria-label="Audit events">
          <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Detail</th><th>Correlation id</th></tr></thead>
          <tbody>@for (a of p.content; track a.id) {
            <tr><td>{{ a.ts.slice(0, 19).replace('T', ' ') }}</td><td>{{ a.actor }}</td><td>{{ a.action }}</td><td>{{ a.entityType }} {{ a.entityId }}</td>
              <td>{{ a.detail }}</td><td><code>{{ a.correlationId ?? '-' }}</code></td></tr>
          }</tbody></table></div>
        <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" aria-label="Select page of audit events" />
      }
    </app-state>
  `,
  styles: `table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; vertical-align: top; }`,
})
export class AuditPage {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  protected readonly list = new Loader<PageView<AuditEvent>>();
  protected readonly f = this.fb.nonNullable.group({ entityType: '', entityId: '', actor: '' });
  private page = 0;
  private size = 25;

  constructor() {
    this.f.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => { this.page = 0; this.reload(); });
    this.reload();
  }
  protected onPage(e: PageEvent): void { this.page = e.pageIndex; this.size = e.pageSize; this.reload(); }
  private reload(): void { this.list.load(this.api.audit({ ...this.f.getRawValue(), page: this.page, size: this.size })); }
}
