import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { distinctUntilChanged, finalize, interval, map, Subscription } from 'rxjs';
import { VotacaoApi } from '../core/votacao-api';
import { FalhaApi, Pauta, Resultado } from '../core/votacao.models';
import { falhaApi } from '../core/form-errors';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';
import { StatusBadge } from '../shared/status-badge';
import { ResultadoCard } from './resultado-card';
import { AbrirSessaoDialog } from './abrir-sessao-dialog';
import { RegistrarVotoDialog } from './registrar-voto-dialog';

@Component({
  selector: 'app-pauta-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    ErrorNotice,
    Icon,
    StatusBadge,
    ResultadoCard,
  ],
  templateUrl: './pauta-page.html',
})
export class PautaPage {
  private readonly api = inject(VotacaoApi);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly destroyRef = inject(DestroyRef);
  private pautaRequest?: Subscription;
  private resultadoRequest?: Subscription;
  private id = '';
  private ultimoPoll = 0;
  private desvioRelogio = 0;
  readonly agora = signal(Date.now());
  readonly segundosRestantes = computed(() => {
    const fim = this.resultado()?.encerraEm;
    return fim ? Math.max(0, Math.ceil((Date.parse(fim) - this.agora()) / 1000)) : null;
  });
  readonly contagem = computed(() => {
    const segundos = this.segundosRestantes();
    return segundos === null ? '' : Math.floor(segundos / 60) + ':' + String(segundos % 60).padStart(2, '0');
  });
  readonly paginaAnterior = this.route.snapshot.queryParamMap.get('pagina') ?? '0';
  readonly pauta = signal<Pauta | null>(null);
  readonly resultado = signal<Resultado | null>(null);
  readonly carregando = signal(true);
  readonly carregandoResultado = signal(true);
  readonly erro = signal<FalhaApi | null>(null);
  readonly erroResultado = signal<FalhaApi | null>(null);
  constructor() {
    interval(1000).pipe(takeUntilDestroyed()).subscribe(() => {
      if (document.visibilityState === 'hidden') return;
      this.agora.set(Date.now() + this.desvioRelogio);
      const espera = this.erroResultado() ? 15000 : this.segundosRestantes() === 0 ? 1000 : 5000;
      if (this.pauta() && this.resultado()?.situacao !== 'ENCERRADA' && !this.carregandoResultado() && Date.now() - this.ultimoPoll >= espera) this.atualizar();
    });
    this.route.paramMap
      .pipe(
        map((params) => params.get('id')!),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((id) => {
        this.id = id;
        this.carregar();
      });
  }
  carregar() {
    this.pautaRequest?.unsubscribe();
    this.resultadoRequest?.unsubscribe();
    this.pauta.set(null);
    this.resultado.set(null);
    this.erro.set(null);
    this.erroResultado.set(null);
    this.carregando.set(true);
    this.pautaRequest = this.api
      .buscar(this.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (pauta) => {
          this.pauta.set(pauta);
          this.title.setTitle(`${pauta.titulo} | Desafio Votação`);
          this.atualizar();
        },
        error: (error) => this.erro.set(falhaApi(error)),
      });
  }
  atualizar() {
    this.ultimoPoll = Date.now();
    this.resultadoRequest?.unsubscribe();
    this.carregandoResultado.set(true);
    this.erroResultado.set(null);
    this.resultadoRequest = this.api
      .resultado(this.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregandoResultado.set(false)),
      )
      .subscribe({
        next: (resultado) => {
          this.desvioRelogio = Date.parse(resultado.apuradoEm) - Date.now();
          this.agora.set(Date.now() + this.desvioRelogio);
          this.resultado.set(resultado);
        },
        error: (error) => this.erroResultado.set(falhaApi(error)),
      });
  }
  abrirSessao() {
    const pauta = this.pauta();
    if (
      !pauta ||
      this.carregandoResultado() ||
      this.erroResultado() ||
      this.resultado()?.situacao !== 'NAO_ABERTA'
    )
      return;
    this.dialog
      .open(AbrirSessaoDialog, { data: pauta })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.atualizar());
  }
  registrarVoto() {
    const pauta = this.pauta();
    if (
      !pauta ||
      this.carregandoResultado() ||
      this.erroResultado() ||
      (this.resultado()?.situacao !== 'ABERTA' || this.segundosRestantes() === 0)
    )
      return;
    this.dialog
      .open(RegistrarVotoDialog, { data: pauta })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.atualizar());
  }
}
