import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { VotacaoApi } from '../core/votacao-api';
import { FalhaApi, Pauta, Sessao } from '../core/votacao.models';
import { aplicarErros, erroCampo, falhaApi, minutosValidos } from '../core/form-errors';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-abrir-sessao-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorNotice,
    Icon,
  ],
  template: ` <div class="dialog-heading">
      <h2 mat-dialog-title>Abrir sessão de votação</h2>
      <button mat-icon-button aria-label="Fechar" [disabled]="enviando()" (click)="ref.close()">
        <app-icon name="close" />
      </button>
    </div>
    @if (sessao(); as aberta) {
      <mat-dialog-content
        ><div class="notice success" role="status">
          <strong>Sessão aberta com sucesso!</strong>
          <p>Os associados já podem registrar seus votos.</p>
        </div>
        <p class="deadline">
          Encerramento em <strong>{{ aberta.encerraEm | date: 'dd/MM/yyyy, HH:mm:ss' }}</strong>
        </p></mat-dialog-content
      >
      <mat-dialog-actions align="end"
        ><button mat-flat-button (click)="ref.close(true)">Ver votação</button></mat-dialog-actions
      >
    } @else {
      <form [formGroup]="form" (ngSubmit)="abrir()">
        <mat-dialog-content>
          <p class="dialog-intro">
            Pauta: <strong>{{ pauta.titulo }}</strong>
          </p>
          @if (erro(); as falha) {
            <app-error-notice [error]="falha" />
          }
          <mat-form-field appearance="outline"
            ><mat-label>Duração em minutos</mat-label
            ><input
              matInput
              cdkFocusInitial
              type="number"
              formControlName="duracaoMinutos"
              min="1"
              max="2147483647"
              step="1"
              placeholder="1"
            /><mat-hint>Se deixar em branco, a sessão dura 1 minuto.</mat-hint
            ><mat-error>{{
              campo(form.controls.duracaoMinutos, 'Informe um número inteiro positivo de minutos.')
            }}</mat-error></mat-form-field
          >
          <div class="notice warning">
            <strong>A sessão só pode ser aberta uma vez.</strong>
            <p>Após o prazo, a votação encerra automaticamente e não pode ser reaberta.</p>
          </div> </mat-dialog-content
        ><mat-dialog-actions align="end"
          ><button mat-stroked-button type="button" [disabled]="enviando()" (click)="ref.close()">
            Cancelar
          </button>
          @if (erro()?.incerta || erro()?.codigo === 'SESSAO_JA_EXISTE') {
            <button mat-flat-button type="button" (click)="ref.close(true)">Consultar pauta</button>
          } @else {
            <button mat-flat-button type="submit" [disabled]="enviando() || form.invalid">
              {{ enviando() ? 'Abrindo…' : 'Abrir sessão' }}
            </button>
          }
        </mat-dialog-actions>
      </form>
    }`,
})
export class AbrirSessaoDialog {
  readonly pauta = inject<Pauta>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<AbrirSessaoDialog>);
  private readonly api = inject(VotacaoApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly enviando = signal(false);
  readonly erro = signal<FalhaApi | null>(null);
  readonly sessao = signal<Sessao | null>(null);
  readonly campo = erroCampo;
  readonly form = new FormGroup({
    duracaoMinutos: new FormControl<number | null>(1, minutosValidos),
  });
  abrir() {
    if (this.enviando() || this.erro()?.incerta || this.sessao()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.enviando.set(true);
    this.erro.set(null);
    this.ref.disableClose = true;
    this.api
      .abrirSessao(this.pauta.id, this.form.controls.duracaoMinutos.value)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.enviando.set(false);
          this.ref.disableClose = false;
        }),
      )
      .subscribe({
        next: (value) => this.sessao.set(value),
        error: (error) => {
          const falha = falhaApi(error, true);
          this.erro.set(falha);
          aplicarErros(this.form, falha);
        },
      });
  }
}
