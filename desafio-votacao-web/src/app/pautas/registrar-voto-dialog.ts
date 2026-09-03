import { AuthService } from '../auth/auth.service';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { VotacaoApi } from '../core/votacao-api';
import { Escolha, FalhaApi, Pauta } from '../core/votacao.models';
import { aplicarErros, erroCampo, falhaApi, naoVazio } from '../core/form-errors';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-registrar-voto-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorNotice,
    Icon,
  ],
  template: ` <div class="dialog-heading">
      <h2 mat-dialog-title>
        {{ etapa() === 'confirmacao' ? 'Confirmar voto' : 'Registrar voto' }}
      </h2>
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
    @if (etapa() === 'sucesso') {
      <mat-dialog-content
        ><div class="success-state" role="status">
          <span class="success-icon"><app-icon name="check" /></span>
          <h3>Voto registrado!</h3>
          <p>Seu voto foi contabilizado com sucesso.</p>
        </div></mat-dialog-content
      >
      <mat-dialog-actions align="end"
        ><button mat-flat-button (click)="ref.close(true)">
          Ver resultado
        </button></mat-dialog-actions
      >
    } @else {
      <form [formGroup]="form" (ngSubmit)="etapa() === 'dados' ? revisar() : votar()">
        <mat-dialog-content>
          @if (erro(); as falha) {
            <app-error-notice [error]="falha" />
          }
          @if (etapa() === 'dados') {
            <p class="dialog-intro">
              <span class="eyebrow">Pauta em votação</span><strong>{{ pauta.titulo }}</strong>
            </p>
            <div class="notice info"><p>Você está votando como <strong>{{ auth.usuario()?.nome }}</strong>.</p><p>CPF {{ auth.usuario()?.cpf }}</p></div>
            <p id="escolha-label" class="field-label">Como você deseja votar?</p>
            <mat-radio-group
              formControlName="escolha"
              aria-labelledby="escolha-label"
              class="vote-options"
            >
              <mat-radio-button
                value="SIM"
                class="vote-option yes"
                [class.selected]="form.controls.escolha.value === 'SIM'"
                ><app-icon name="check" /><span>Sim</span></mat-radio-button
              >
              <mat-radio-button
                value="NAO"
                class="vote-option no"
                [class.selected]="form.controls.escolha.value === 'NAO'"
                ><app-icon name="close" /><span>Não</span></mat-radio-button
              >
            </mat-radio-group>
            @if (form.controls.escolha.touched && form.controls.escolha.invalid) {
              <p class="field-error">
                {{ campo(form.controls.escolha, 'Escolha Sim ou Não para continuar.') }}
              </p>
            }
            <div class="notice warning">
              <p>
                Cada associado pode votar uma única vez nesta pauta. Após confirmar, o voto não
                poderá ser alterado.
              </p>
            </div>
          } @else {
            <p class="dialog-intro">
              Confira os dados antes de confirmar. Esta ação não pode ser desfeita.
            </p>
            <dl class="vote-summary">
              <div>
                <dt>Pauta</dt>
                <dd>{{ pauta.titulo }}</dd>
              </div>
              <div>
                <dt>Associado</dt>
                <dd>{{ auth.usuario()?.cpf }}</dd>
              </div>
              <div>
                <dt>Seu voto</dt>
                <dd class="vote-value" [class.no]="form.controls.escolha.value === 'NAO'">
                  {{ form.controls.escolha.value === 'SIM' ? 'Sim' : 'Não' }}
                </dd>
              </div>
            </dl>
          }</mat-dialog-content
        ><mat-dialog-actions align="end">
          @if (
            erro()?.incerta ||
            erro()?.codigo === 'SESSAO_ENCERRADA' ||
            erro()?.codigo === 'SESSAO_NAO_ABERTA'
          ) {
            <button mat-flat-button type="button" (click)="ref.close(true)">Consultar pauta</button>
          } @else {
            <button mat-stroked-button type="button" [disabled]="enviando()" (click)="voltar()">
              {{ etapa() === 'dados' ? 'Cancelar' : 'Voltar e corrigir' }}
            </button>
            <button
              mat-flat-button
              type="submit"
              [disabled]="enviando() || form.invalid || erro()?.codigo === 'VOTO_DUPLICADO'"
            >
              {{
                enviando()
                  ? 'Registrando…'
                  : etapa() === 'dados'
                    ? 'Revisar e confirmar'
                    : 'Confirmar voto'
              }}
            </button>
          }
        </mat-dialog-actions>
      </form>
    }`,
})
export class RegistrarVotoDialog {
  readonly auth = inject(AuthService);
  readonly pauta = inject<Pauta>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<RegistrarVotoDialog>);
  private readonly api = inject(VotacaoApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly etapa = signal<'dados' | 'confirmacao' | 'sucesso'>('dados');
  readonly enviando = signal(false);
  readonly erro = signal<FalhaApi | null>(null);
  readonly campo = erroCampo;
  readonly form = new FormGroup({
    escolha: new FormControl<Escolha | null>(null, Validators.required),
  });
  revisar() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      this.erro.set(null);
      this.etapa.set('confirmacao');
    }
  }
  voltar() {
    if (this.enviando()) return;
    if (this.etapa() === 'dados') this.ref.close();
    else {
      this.erro.set(null);
      this.etapa.set('dados');
    }
  }
  votar() {
    if (
      this.enviando() ||
      this.form.invalid ||
      this.etapa() !== 'confirmacao' ||
      this.erro()?.incerta ||
      this.erro()?.codigo === 'VOTO_DUPLICADO'
    )
      return;
    const { escolha } = this.form.getRawValue();
    if (!escolha) return;
    this.enviando.set(true);
    this.erro.set(null);
    this.ref.disableClose = true;
    this.api
      .votar(this.pauta.id, escolha)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.enviando.set(false);
          this.ref.disableClose = false;
        }),
      )
      .subscribe({
        next: () => this.etapa.set('sucesso'),
        error: (error) => {
          const falha = falhaApi(error, true);
          this.erro.set(falha);
          aplicarErros(this.form, falha);
          if (falha.campos.length) this.etapa.set('dados');
        },
      });
  }
}
