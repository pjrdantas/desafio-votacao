import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { VotacaoApi } from '../core/votacao-api';
import { FalhaApi } from '../core/votacao.models';
import { aplicarErros, erroCampo, falhaApi, naoVazio } from '../core/form-errors';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-nova-pauta-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorNotice,
    Icon,
  ],
  template: ` <div class="dialog-heading">
      <h2 mat-dialog-title>Nova pauta</h2>
      <button
        mat-icon-button
        type="button"
        aria-label="Fechar"
        [disabled]="enviando()"
        (click)="ref.close()"
      >
        <app-icon name="close" />
      </button>
    </div>
    <form [formGroup]="form" (ngSubmit)="salvar()">
      <mat-dialog-content>
        <p class="dialog-intro">Qual assunto vamos colocar em votação?</p>
        @if (erro(); as falha) {
          <app-error-notice [error]="falha" />
        }
        <mat-form-field appearance="outline"
          ><mat-label>Título</mat-label
          ><input
            matInput
            cdkFocusInitial
            formControlName="titulo"
            maxlength="200"
            placeholder="Ex.: Reforma do salão de eventos"
            required
          /><mat-hint align="end">{{ form.controls.titulo.value.length }}/200</mat-hint
          ><mat-error>{{
            campo(form.controls.titulo, 'Informe o título da pauta.')
          }}</mat-error></mat-form-field
        >
        <mat-form-field appearance="outline"
          ><mat-label>Descrição</mat-label
          ><textarea
            matInput
            formControlName="descricao"
            rows="4"
            maxlength="2000"
            placeholder="Descreva o assunto para ajudar os associados a decidir."
          ></textarea
          ><mat-hint>Opcional</mat-hint
          ><mat-hint align="end">{{ form.controls.descricao.value.length }}/2000</mat-hint
          ><mat-error>{{
            campo(form.controls.descricao, 'Revise a descrição.')
          }}</mat-error></mat-form-field
        >
      </mat-dialog-content>
      <mat-dialog-actions align="end"
        ><button mat-stroked-button type="button" [disabled]="enviando()" (click)="ref.close()">
          Cancelar
        </button>
        @if (erro()?.incerta) {
          <button mat-flat-button type="button" (click)="ref.close('consultar')">
            Consultar pautas
          </button>
        } @else {
          <button mat-flat-button type="submit" [disabled]="enviando() || form.invalid">
            {{ enviando() ? 'Criando…' : 'Criar pauta' }}
          </button>
        }
      </mat-dialog-actions>
    </form>`,
})
export class NovaPautaDialog {
  readonly ref = inject(MatDialogRef<NovaPautaDialog>);
  private readonly api = inject(VotacaoApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly enviando = signal(false);
  readonly erro = signal<FalhaApi | null>(null);
  readonly campo = erroCampo;
  readonly form = new FormGroup({
    titulo: new FormControl('', {
      nonNullable: true,
      validators: [naoVazio, Validators.maxLength(200)],
    }),
    descricao: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }),
  });
  salvar() {
    if (this.enviando() || this.erro()?.incerta) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.enviando.set(true);
    this.erro.set(null);
    this.ref.disableClose = true;
    const { titulo, descricao } = this.form.getRawValue();
    this.api
      .criar({ titulo: titulo.trim(), descricao: descricao.trim() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.enviando.set(false);
          this.ref.disableClose = false;
        }),
      )
      .subscribe({
        next: (pauta) => this.ref.close(pauta),
        error: (error) => {
          const falha = falhaApi(error, true);
          this.erro.set(falha);
          aplicarErros(this.form, falha);
        },
      });
  }
}
