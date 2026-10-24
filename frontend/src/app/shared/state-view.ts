import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/** Consistent loading / error / empty presentation; projects the real content when there is data. */
@Component({
  selector: 'app-state',
  imports: [MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) { <mat-progress-bar mode="indeterminate" aria-label="Loading" /> }
    @if (error(); as e) {
      <div class="error" role="alert">{{ e }}</div>
    } @else if (!loading() && empty()) {
      <div class="empty" role="status">{{ emptyText() }}</div>
    } @else {
      <ng-content />
    }
  `,
})
export class StateView {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyText = input('Nothing to show.');
}
