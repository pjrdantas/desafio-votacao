import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';
import { falhaApi } from '../core/form-errors';

@Component({
  selector: 'app-auth-bar',
  imports: [RouterLink, MatButtonModule],
  template: `@if (auth.usuario(); as usuario) {
    <div class="account-bar"><div class="container account-inner">
      <span><strong>{{ usuario.nome }}</strong><span class="account-cpf">CPF {{ usuario.cpf }}</span></span>
      <nav aria-label="Conta"><a mat-button routerLink="/pautas">Pautas</a><a mat-button routerLink="/mobile">Fluxo mobile</a>
        <button mat-button [disabled]="saindo()" (click)="sair()">{{ saindo() ? 'Saindo…' : 'Sair' }}</button></nav>
    </div>@if (erro()) { <p class="container account-error" role="alert">{{ erro() }}</p> }</div>
  }`,
})
export class AuthBar {
  readonly auth = inject(AuthService);
  readonly saindo = signal(false);
  readonly erro = signal('');
  sair() {
    if (this.saindo()) return;
    this.saindo.set(true); this.erro.set('');
    this.auth.sair().pipe(finalize(() => this.saindo.set(false)))
      .subscribe({ error: error => this.erro.set(falhaApi(error).mensagem) });
  }
}
