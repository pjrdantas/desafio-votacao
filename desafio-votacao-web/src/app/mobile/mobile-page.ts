import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ErrorNotice } from '../shared/error-notice';
import { FalhaApi } from '../core/votacao.models';
import { aplicarErros, erroCampo, falhaApi } from '../core/form-errors';

interface Acao { label: string; url: string; body: Record<string, unknown>; }
interface Campo { id: string; tipo: 'TEXTO' | 'NUMERO' | 'DATA'; label: string; obrigatorio: boolean; valor?: string | number; somenteLeitura: boolean; }
interface Tela { tipo: 'FORMULARIO' | 'SELECAO'; titulo: string; itens?: Campo[]; botoes?: Acao[]; opcoes?: Acao[]; }

export function caminhoMobile(url: string): string {
  const parsed = new URL(url, window.location.origin);
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password
      || !/^\/api\/v1\/mobile(?:\/|$)/.test(parsed.pathname) || parsed.search || parsed.hash)
    throw new Error('Callback mobile inválido.');
  // As ações passam pelo proxy da aplicação; nenhuma credencial é enviada ao host informado no JSON.
  return parsed.pathname;
}
@Component({
  selector: 'app-mobile-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink, ErrorNotice],
  template: `
    <main id="conteudo" class="container main-content mobile-contract" tabindex="-1">
      <a mat-button routerLink="/pautas">Voltar às pautas</a>
      <section class="card mobile-contract-card">
        <p class="eyebrow">Assembleia</p>
        <h1>{{ tela()?.titulo || 'Carregando…' }}</h1>
        @if (erro(); as falha) { <app-error-notice [error]="falha" /> }
        @if (incerta()) {
          <p class="notice info">Consulte a pauta para confirmar a operação antes de tentar novamente.</p>
          <button mat-flat-button [disabled]="enviando()" (click)="inicio()">Voltar ao menu</button>
        } @else if (tela(); as t) {
          @if (t.tipo === 'FORMULARIO') {
            <form [formGroup]="form">
              @for (item of t.itens || []; track item.id) {
                @if (item.somenteLeitura) { <dl class="mobile-value"><dt>{{ item.label }}</dt><dd>{{ item.valor }}</dd></dl> }
                @else {
                  <mat-form-field appearance="outline"><mat-label>{{ item.label }}</mat-label>
                    <input matInput [formControlName]="item.id" [type]="item.tipo === 'NUMERO' ? 'number' : item.tipo === 'DATA' ? 'date' : 'text'"
                      [required]="item.obrigatorio" />
                    <mat-error>{{ campo(form.get(item.id)!, 'Verifique este campo.') }}</mat-error>
                  </mat-form-field>
                }
              }
              <div class="mobile-actions">@for (acao of t.botoes || []; track acao.label) {
                <button mat-stroked-button type="button" [disabled]="enviando()" (click)="executar(acao)">{{ acao.label }}</button>
              }</div>
            </form>
          } @else {
            <div class="mobile-options">@for (acao of t.opcoes || []; track $index) {
              <button mat-stroked-button [disabled]="enviando()" (click)="executar(acao)">{{ acao.label }}</button>
            }</div>
          }
        }
        @if (enviando()) { <p role="status">Carregando…</p> }
      </section>
    </main>`,
})
export class MobilePage {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  readonly tela = signal<Tela | null>(null);
  readonly erro = signal<FalhaApi | null>(null);
  readonly enviando = signal(false);
  readonly incerta = signal(false);
  readonly campo = erroCampo;
  form = new FormGroup<Record<string, FormControl>>({});
  constructor() { this.inicio(); }
  inicio() {
    this.carregar('/api/v1/mobile', {}, false);
  }
  executar(acao: Acao) {
    if (this.enviando()) return;
    let path: string;
    try { path = caminhoMobile(acao.url); } catch {
      this.erro.set({ status: 0, codigo: 'CALLBACK_INVALIDO', mensagem: 'Não foi possível abrir esta ação.', campos: [], incerta: false }); return;
    }
    const escrita = /(?:\/pautas|\/sessao|\/votos)$/.test(path);
    if (escrita) { this.form.markAllAsTouched(); if (this.form.invalid) return; }
    this.carregar(path, { ...acao.body, ...(escrita ? this.form.getRawValue() : {}) }, escrita);
  }
  private carregar(path: string, body: unknown, escrita: boolean) {
    this.enviando.set(true); this.erro.set(null);
    this.http.post<Tela>(path, body, { timeout: 15000 }).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.enviando.set(false))).subscribe({
        next: tela => {
          this.tela.set(tela); this.incerta.set(false);
          const controls: Record<string, FormControl> = Object.create(null);
          for (const item of tela.itens || []) {
            if (!item.somenteLeitura && /^[a-zA-Z][a-zA-Z0-9]*$/.test(item.id) && !['constructor', 'prototype', '__proto__'].includes(item.id))
              controls[item.id] = new FormControl(item.valor ?? null, item.obrigatorio ? [Validators.required] : []);
          }
          this.form = new FormGroup(controls);
        },
        error: error => { const falha = falhaApi(error, escrita); this.erro.set(falha); this.incerta.set(falha.incerta); aplicarErros(this.form, falha); },
      });
  }
}
