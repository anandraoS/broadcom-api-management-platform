import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Access denied</h1>
    <p>Your role does not include this area. The server enforces this independently of what the menu shows.</p>
    <a routerLink="/dashboard">Back to the dashboard</a>
  `,
})
export class ForbiddenPage {}
