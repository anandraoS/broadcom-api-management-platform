import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="wrap">
      <mat-card>
        <mat-card-header><mat-card-title>Sign in</mat-card-title></mat-card-header>
        <mat-card-content>
          <p class="muted">Development identity mode. Credentials are issued by the local dev identity provider; production uses your OIDC provider.</p>
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
            <mat-form-field appearance="outline">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" autocomplete="username" required />
              @if (form.controls.username.touched && form.controls.username.invalid) { <mat-error>Username is required</mat-error> }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="current-password" required />
              @if (form.controls.password.touched && form.controls.password.invalid) { <mat-error>Password is required</mat-error> }
            </mat-form-field>
            @if (error(); as e) { <div class="error" role="alert">{{ e }}</div> }
            <button mat-flat-button color="primary" type="submit" [disabled]="busy()">{{ busy() ? 'Signing in...' : 'Sign in' }}</button>
          </form>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
    mat-card { width: min(420px, 100%); padding: 8px; }
  `,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(64)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: () => {
        // one generic message for every failure: no hint about which field was wrong
        this.error.set('Sign-in failed. Check your credentials and try again.');
        this.busy.set(false);
      },
    });
  }
}
