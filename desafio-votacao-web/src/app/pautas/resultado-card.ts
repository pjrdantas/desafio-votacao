import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DECISOES, FalhaApi, Resultado } from '../core/votacao.models';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';
@Component({
  selector: 'app-resultado-card',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    ErrorNotice,
    Icon,
  ],
  template: ` <section
    class="card result-card"
    aria-labelledby="resultado-titulo"
    [attr.aria-busy]="carregando()"
  >
    <div class="section-heading">
      <h2 id="resultado-titulo">
        {{
          resultado()?.situacao === 'ENCERRADA'
            ? 'Resultado final'
            : resultado()?.situacao === 'ABERTA'
              ? 'Resultado parcial'
              : 'Apuração'
        }}
      </h2>
      <button
        mat-stroked-button
        class="refresh-button"
        [disabled]="carregando()"
        (click)="atualizar.emit()"
      >
        <app-icon name="refresh" />{{ carregando() ? 'Atualizando…' : 'Atualizar resultado' }}
      </button>
    </div>
    @if (erro(); as falha) {
      <app-error-notice title="Resultado indisponível" [error]="falha" />
      @if (resultado()) {
        <p class="stale-message">
          Exibindo a última consulta. Atualize para verificar a situação atual.
        </p>
      }
    }
    @if (carregando() && !resultado()) {
      <div class="loading-state compact" role="status">
        <mat-spinner diameter="22" /><span>Consultando resultado…</span>
      </div>
    }
    @if (resultado(); as r) {
      @if (r.situacao === 'NAO_ABERTA') {
        <p class="result-empty">
          A sessão ainda não foi aberta. Abra a sessão para iniciar a votação.
        </p>
      } @else {
        <div class="decision-band" [attr.data-decisao]="r.decisao">
          <div>
            <span class="eyebrow">Decisão</span><strong>{{ decisoes[r.decisao] }}</strong>
          </div>
          <div class="total-votes">
            <span class="eyebrow">Total de votos</span><strong>{{ r.total | number }}</strong>
          </div>
        </div>
        <div class="vote-bar yes">
          <div class="bar-label">
            <span><span class="legend-dot"></span>Sim</span
            ><span
              ><strong>{{ r.sim | number }}</strong>
              <span class="muted">({{ percentual(r.sim, r.total) | number: '1.0-1' }}%)</span></span
            >
          </div>
          <mat-progress-bar
            mode="determinate"
            [value]="percentual(r.sim, r.total)"
            aria-label="Percentual de votos Sim"
          />
        </div>
        <div class="vote-bar no">
          <div class="bar-label">
            <span><span class="legend-dot"></span>Não</span
            ><span
              ><strong>{{ r.nao | number }}</strong>
              <span class="muted">({{ percentual(r.nao, r.total) | number: '1.0-1' }}%)</span></span
            >
          </div>
          <mat-progress-bar
            mode="determinate"
            [value]="percentual(r.nao, r.total)"
            aria-label="Percentual de votos Não"
          />
        </div>
        @if (r.situacao === 'ABERTA') {
          <p class="partial-note">A decisão será definida após o encerramento da sessão.</p>
        }
      }
      <p class="updated-at">Atualizado em {{ r.apuradoEm | date: 'dd/MM/yyyy, HH:mm:ss' }}</p>
    }
  </section>`,
})
export class ResultadoCard {
  readonly resultado = input<Resultado | null>(null);
  readonly carregando = input(false);
  readonly erro = input<FalhaApi | null>(null);
  readonly atualizar = output<void>();
  readonly decisoes = DECISOES;
  percentual(votos: number, total: number) {
    return total > 0 ? (votos / total) * 100 : 0;
  }
}
