import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/interceptors';
import { Loader } from '../core/loader';
import { Consumer, PageView } from '../core/models';
import { StateView } from '../shared/state-view';

@Component({
  selector: 'app-consumer-list',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatPaginatorModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Consumer applications</h1>
    <div class="toolbar-row">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Search by name</mat-label><input matInput [formControl]="q" maxlength="80" />
      </mat-form-field>
      <span class="spacer"></span>
      @if (canCreate()) { <button mat-flat-button color="primary" type="button" (click)="show.set(!show())">{{ show() ? 'Close' : 'Register consumer' }}</button> }
    </div>
    @if (show()) {
      <form [formGroup]="form" (ngSubmit)="submit()" class="grid2" novalidate aria-label="Register consumer">
        <mat-form-field appearance="outline"><mat-label>Name</mat-label><input matInput formControlName="name" />
          @if (form.controls.name.touched && form.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Client id</mat-label><input matInput formControlName="clientId" placeholder="acme-partner" />
          @if (form.controls.clientId.touched && form.controls.clientId.invalid) { <mat-error>3-79 chars: lowercase letters, digits, dashes</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Owner email</mat-label><input matInput type="email" formControlName="ownerEmail" />
          @if (form.controls.ownerEmail.touched && form.controls.ownerEmail.invalid) { <mat-error>Valid email required</mat-error> }</mat-form-field>
        <div><button mat-flat-button color="primary" type="submit">Create</button></div>
      </form>
    }
    <app-state [loading]="list.loading()" [error]="list.error()" [empty]="!list.data()?.content?.length" emptyText="No consumers found.">
      @if (list.data(); as p) {
        <div class="table-wrap"><table class="t" role="table" aria-label="Consumers">
          <thead><tr><th>Name</th><th>Client id</th><th>Owner</th><th>Status</th></tr></thead>
          <tbody>@for (c of p.content; track c.id) {
            <tr><td><a [routerLink]="['/consumers', c.id]">{{ c.name }}</a></td><td><code>{{ c.clientId }}</code></td><td>{{ c.ownerEmail }}</td>
              <td><span class="chip" [class]="'chip ' + (c.active ? 'ACTIVE' : 'INACTIVE')">{{ c.active ? 'ACTIVE' : 'DISABLED' }}</span></td></tr>
          }</tbody></table></div>
        <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.page" [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" aria-label="Select page of consumers" />
      }
    </app-state>
  `,
  styles: `table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; } .grid2 { margin-bottom: 16px; }`,
})
export class ConsumerListPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly snack = inject(MatSnackBar);
  protected readonly list = new Loader<PageView<Consumer>>();
  protected readonly show = signal(false);
  protected readonly canCreate = () => this.auth.hasAnyRole('ADMIN', 'API_OWNER');
  protected readonly q = this.fb.nonNullable.control('');
  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    clientId: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9-]{2,78}$/)]],
    ownerEmail: ['', [Validators.required, Validators.email]],
  });
  private page = 0;
  private size = 20;

  constructor() {
    this.q.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => { this.page = 0; this.reload(); });
    this.reload();
  }

  protected onPage(e: PageEvent): void { this.page = e.pageIndex; this.size = e.pageSize; this.reload(); }
  private reload(): void { this.list.load(this.api.searchConsumers({ q: this.q.value, page: this.page, size: this.size })); }

  protected submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.api.createConsumer(this.form.getRawValue()).subscribe({
      next: () => { this.form.reset(); this.show.set(false); this.reload(); this.snack.open('Consumer created', 'OK', { duration: 3000 }); },
      error: (e: unknown) => this.snack.open(describeError(e), 'Dismiss', { duration: 8000 }),
    });
  }
}
