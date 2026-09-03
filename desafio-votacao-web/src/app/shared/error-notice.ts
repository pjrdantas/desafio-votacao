import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { FalhaApi } from '../core/votacao.models';
@Component({
  selector: 'app-error-notice',
  imports: [MatButtonModule],
  template: ` <div class="notice error" role="alert">
    <strong>{{ title() }}</strong>
    <p>{{ error().mensagem }}</p>
    @if (error().correlationId) {
      <details>
        <summary>Detalhes para suporte</summary>
        <code>{{ error().correlationId }}</code>
      </details>
    }
    @if (retryable()) {
      <button mat-button type="button" (click)="retry.emit()">Tentar novamente</button>
    }
  </div>`,
})
export class ErrorNotice {
  readonly error = input.required<FalhaApi>();
  readonly title = input('Não foi possível concluir');
  readonly retryable = input(false);
  readonly retry = output<void>();
}
