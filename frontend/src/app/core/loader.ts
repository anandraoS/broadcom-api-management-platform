import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';
import { describeError } from './interceptors';

/**
 * Loading/error/data state holder for one request. Starting a new load cancels the previous one (no stale response can
 * overwrite a newer one) and subscriptions end with the owning component (no leaks).
 */
export class Loader<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  private sub?: Subscription;
  private readonly destroyRef = inject(DestroyRef);

  load(source: Observable<T>): void {
    this.sub?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.sub = source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (v) => {
        this.data.set(v);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(describeError(e));
        this.loading.set(false);
      },
    });
  }
}
