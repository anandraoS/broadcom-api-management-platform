import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/interceptors';
import { Loader } from '../core/loader';
import { Api, PageView } from '../core/models';
import { StateView } from '../shared/state-view';

@Component({
  selector: 'app-api-list',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule, StateView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">API registry</h1>
    <form [formGroup]="filters" class="toolbar-row" role="search" aria-label="Filter APIs">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Search name or slug</mat-label>
        <input matInput formControlName="q" maxlength="80" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Lifecycle</mat-label>
        <mat-select formControlName="lifecycle">
          <mat-option value="">All</mat-option>
          @for (l of lifecycles; track l) { <mat-option [value]="l">{{ l }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Owner team</mat-label>
        <input matInput formControlName="owner" maxlength="80" />
      </mat-form-field>
      <span class="spacer"></span>
      @if (canCreate()) { <button mat-flat-button color="primary" type="button" (click)="showCreate.set(!showCreate())">{{ showCreate() ? 'Close' : 'Register API' }}</button> }
    </form>

    @if (showCreate()) {
      <form [formGroup]="create" (ngSubmit)="submitCreate()" class="grid2" novalidate aria-label="Register API">
        <mat-form-field appearance="outline"><mat-label>Slug</mat-label><input matInput formControlName="slug" placeholder="orders" />
          @if (create.controls.slug.touched && create.controls.slug.invalid) { <mat-error>3-80 chars: lowercase letters, digits, dashes</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Name</mat-label><input matInput formControlName="name" />
          @if (create.controls.name.touched && create.controls.name.invalid) { <mat-error>Name is required</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Owner team</mat-label><input matInput formControlName="ownerTeam" />
          @if (create.controls.ownerTeam.touched && create.controls.ownerTeam.invalid) { <mat-error>Lowercase team id required</mat-error> }</mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Description</mat-label><input matInput formControlName="description" maxlength="1000" /></mat-form-field>
        <div><button mat-flat-button color="primary" type="submit" [disabled]="creating()">Create</button></div>
      </form>
    }

    <app-state [loading]="list.loading()" [error]="list.error()" [empty]="!list.data()?.content?.length" emptyText="No APIs match these filters.">
      @if (list.data(); as page) {
        <div class="table-wrap">
          <table role="table" aria-label="APIs" class="t">
            <thead><tr><th>Name</th><th>Slug</th><th>Owner</th><th>Lifecycle</th><th>Updated</th></tr></thead>
            <tbody>
              @for (a of page.content; track a.id) {
                <tr>
                  <td><a [routerLink]="['/apis', a.id]">{{ a.name }}</a></td>
                  <td><code>{{ a.slug }}</code></td><td>{{ a.ownerTeam }}</td>
                  <td><span class="chip" [class]="'chip ' + a.lifecycle">{{ a.lifecycle }}</span></td>
                  <td>{{ a.updatedAt.slice(0, 19).replace('T', ' ') }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="page.totalElements" [pageSize]="page.size" [pageIndex]="page.page" [pageSizeOptions]="[10, 20, 50]"
                       (page)="onPage($event)" aria-label="Select page of APIs" />
      }
    </app-state>
  `,
  styles: `table.t { border-collapse: collapse; width: 100%; } th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #eee; } .grid2 { margin-bottom: 16px; }`,
})
export class ApiListPage {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly lifecycles = ['DRAFT', 'ACTIVE', 'INACTIVE', 'DEPRECATED'];
  protected readonly list = new Loader<PageView<Api>>();
  protected readonly showCreate = signal(false);
  protected readonly creating = signal(false);
  protected readonly canCreate = () => this.auth.hasAnyRole('ADMIN', 'API_OWNER');
  private page = 0;
  private size = 20;
  // one idempotency key per form instance: a double-click or retry cannot create the API twice
  private idemKey = crypto.randomUUID();

  protected readonly filters = this.fb.nonNullable.group({ q: '', lifecycle: '', owner: '' });
  protected readonly create = this.fb.nonNullable.group({
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$/)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    ownerTeam: [this.auth.user()?.team ?? '', [Validators.required, Validators.pattern(/^[a-z0-9-]{2,80}$/)]],
    description: '',
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)), takeUntilDestroyed())
      .subscribe(() => { this.page = 0; this.reload(); });
    this.reload();
  }

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.reload();
  }

  private reload(): void {
    const f = this.filters.getRawValue();
    this.list.load(this.api.searchApis({ ...f, page: this.page, size: this.size }));
  }

  submitCreate(): void {
    if (this.create.invalid) { this.create.markAllAsTouched(); return; }
    this.creating.set(true);
    this.api.createApi(this.create.getRawValue(), this.idemKey).subscribe({
      next: (a) => { this.idemKey = crypto.randomUUID(); void this.router.navigate(['/apis', a.id]); },
      error: (e: unknown) => { this.creating.set(false); this.snack.open(describeError(e), 'Dismiss', { duration: 8000 }); },
    });
  }
}
