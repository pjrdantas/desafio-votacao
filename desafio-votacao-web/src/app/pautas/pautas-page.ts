import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { distinctUntilChanged, finalize, map, Subscription } from 'rxjs';
import { VotacaoApi } from '../core/votacao-api';
import { FalhaApi, Pauta } from '../core/votacao.models';
import { falhaApi } from '../core/form-errors';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';
import { NovaPautaDialog } from './nova-pauta-dialog';

@Component({
  selector: 'app-pautas-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    ErrorNotice,
    Icon,
  ],
  templateUrl: './pautas-page.html',
})
export class PautasPage {
  private readonly api = inject(VotacaoApi);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  readonly pautas = signal<Pauta[]>([]);
  readonly pagina = signal(0);
  readonly tamanho = 20;
  readonly carregando = signal(true);
  readonly erro = signal<FalhaApi | null>(null);
  readonly colunas = ['titulo', 'descricao', 'criadaEm', 'acao'];
  constructor() {
    this.route.queryParamMap
      .pipe(
        map((params) => {
          const value = Number(params.get('pagina') ?? 0);
          return Number.isInteger(value) && value >= 0 && value <= 2147483647 ? value : 0;
        }),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        this.pagina.set(pagina);
        this.carregar();
      });
  }
  carregar() {
    this.request?.unsubscribe();
    this.carregando.set(true);
    this.erro.set(null);
    this.request = this.api
      .listar(this.pagina(), this.tamanho)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (pautas) => this.pautas.set(pautas),
        error: (error) => this.erro.set(falhaApi(error)),
      });
  }
  mudarPagina(delta: number) {
    if (!this.carregando())
      void this.router.navigate(['/pautas'], { queryParams: { pagina: this.pagina() + delta } });
  }
  novaPauta() {
    this.dialog
      .open(NovaPautaDialog)
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultado: Pauta | 'consultar' | undefined) => {
        if (resultado === 'consultar') {
          if (this.pagina() === 0) this.carregar();
          else void this.router.navigate(['/pautas']);
        } else if (resultado) void this.router.navigate(['/pautas', resultado.id]);
      });
  }
}
