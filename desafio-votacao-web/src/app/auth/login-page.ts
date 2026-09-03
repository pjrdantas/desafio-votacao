import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize, switchMap, tap } from 'rxjs';
import { AuthService } from './auth.service';
import { validarCpf } from './cpf';
import { aplicarErros, erroCampo, falhaApi, naoVazio } from '../core/form-errors';
import { FalhaApi } from '../core/votacao.models';
import { ErrorNotice } from '../shared/error-notice';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, ErrorNotice, Icon],
  template: `
    <main id="conteudo" class="login-page" tabindex="-1">
      <section class="login-intro">
        <span class="brand-mark"><app-icon name="ballot" /></span>
        <p class="eyebrow">Desafio Votação</p>
        <h1>Sua voz faz parte<br />da decisão.</h1>
        <p>Acompanhe as pautas da assembleia e participe das votações com segurança.</p>
      </section>
      <section class="card login-card" aria-labelledby="login-titulo">
        <h2 id="login-titulo">{{ cadastro() ? 'Crie sua conta' : 'Bem-vindo de volta' }}</h2>
        <p class="muted">{{ cadastro() ? 'Informe seus dados para participar da assembleia.' : 'Entre com seu CPF e sua senha.' }}</p>
        @if (expirada && !cadastro()) { <p class="notice info" role="status">Sua sessão expirou. Entre novamente para continuar.</p> }
        @if (erro(); as falha) { <app-error-notice [error]="falha" /> }
        <form [formGroup]="form" (ngSubmit)="enviar()">
          @if (cadastro()) {
            <mat-form-field appearance="outline"><mat-label>Nome</mat-label>
              <input matInput formControlName="nome" autocomplete="name" maxlength="120" />
              <mat-error>{{ campo(form.controls.nome, 'Informe seu nome.') }}</mat-error>
            </mat-form-field>
          }
          <mat-form-field appearance="outline"><mat-label>CPF</mat-label>
            <input matInput formControlName="cpf" inputmode="numeric" autocomplete="username" maxlength="14" placeholder="000.000.000-00" required />
            <mat-error>{{ campo(form.controls.cpf, 'Informe um CPF válido.') }}</mat-error>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Senha</mat-label>
            <input matInput formControlName="senha" [type]="mostrarSenha() ? 'text' : 'password'"
              [attr.autocomplete]="cadastro() ? 'new-password' : 'current-password'" maxlength="72" required />
            <button mat-button matSuffix type="button" (click)="mostrarSenha.set(!mostrarSenha())"
              [attr.aria-label]="mostrarSenha() ? 'Ocultar senha' : 'Mostrar senha'">{{ mostrarSenha() ? 'Ocultar' : 'Mostrar' }}</button>
            @if (cadastro()) { <mat-hint>Use ao menos 10 caracteres.</mat-hint> }
            <mat-error>{{ campo(form.controls.senha, cadastro() ? 'Use ao menos 10 caracteres.' : 'Informe sua senha.') }}</mat-error>
          </mat-form-field>
          @if (cadastro()) {
            <mat-form-field appearance="outline"><mat-label>Confirme a senha</mat-label>
              <input matInput formControlName="confirmacao" type="password" autocomplete="new-password" required />
              <mat-error>Confirme sua senha.</mat-error>
            </mat-form-field>
            @if (form.hasError('senhasDiferentes') && form.controls.confirmacao.touched) {
              <p class="field-error" role="alert">As senhas precisam ser iguais.</p>
            }
          }
          <button class="login-submit" mat-flat-button type="submit" [disabled]="enviando()">
            {{ enviando() ? 'Aguarde…' : cadastro() ? 'Criar conta e entrar' : 'Entrar' }}
          </button>
        </form>
        <p class="login-switch">{{ cadastro() ? 'Já possui uma conta?' : 'Primeiro acesso?' }}
          <button mat-button [disabled]="enviando()" (click)="alternar()">{{ cadastro() ? 'Entrar' : 'Criar conta' }}</button></p>
      </section>
    </main>`,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly cadastro = signal(false);
  readonly enviando = signal(false);
  readonly mostrarSenha = signal(false);
  readonly erro = signal<FalhaApi | null>(null);
  readonly expirada = this.route.snapshot.queryParamMap.get('expirada') === '1';
  readonly campo = erroCampo;
  readonly form = new FormGroup({
    nome: new FormControl('', { nonNullable: true }),
    cpf: new FormControl('', { nonNullable: true, validators: [validarCpf] }),
    senha: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    confirmacao: new FormControl('', { nonNullable: true }),
  }, { validators: group => this.cadastro() && group.get('senha')?.value !== group.get('confirmacao')?.value ? { senhasDiferentes: true } : null });
  alternar() {
    this.cadastro.update(value => !value); this.erro.set(null);
    this.form.controls.nome.setValidators(this.cadastro() ? [naoVazio, Validators.maxLength(120)] : []);
    this.form.controls.senha.setValidators(this.cadastro() ? [Validators.required, Validators.minLength(10)] : [Validators.required]);
    this.form.controls.confirmacao.setValidators(this.cadastro() ? [Validators.required] : []);
    Object.values(this.form.controls).forEach(control => { control.updateValueAndValidity(); control.markAsUntouched(); });
    this.form.updateValueAndValidity();
  }
  enviar() {
    if (this.enviando()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.enviando.set(true); this.erro.set(null);
    const { nome, cpf, senha } = this.form.getRawValue();
    const request = this.cadastro()
      ? this.auth.cadastrar(nome.trim(), cpf, senha).pipe(tap(() => this.alternar()), switchMap(() => this.auth.entrar(cpf, senha)))
      : this.auth.entrar(cpf, senha);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.enviando.set(false))).subscribe({
      next: () => {
        this.form.controls.senha.reset(); this.form.controls.confirmacao.reset();
        const retorno = this.route.snapshot.queryParamMap.get('retorno') || '/pautas';
        void this.router.navigateByUrl(/^\/(pautas(?:\/|\?|$)|mobile(?:\?|$))/.test(retorno) ? retorno : '/pautas');
      },
      error: error => { const falha = falhaApi(error); this.erro.set(falha); aplicarErros(this.form, falha); },
    });
  }
}
