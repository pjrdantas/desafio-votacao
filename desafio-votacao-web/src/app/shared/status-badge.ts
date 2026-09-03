import { Component, input } from '@angular/core';
import { Situacao, SITUACOES } from '../core/votacao.models';
@Component({
  selector: 'app-status-badge',
  template:
    '<span class="status-badge" [class.open]="status() === \'ABERTA\'"><span class="status-dot"></span>{{ labels[status()] }}</span>',
})
export class StatusBadge {
  readonly status = input.required<Situacao>();
  protected readonly labels = SITUACOES;
}
