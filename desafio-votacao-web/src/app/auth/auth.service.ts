import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Observable, catchError, defer, finalize, firstValueFrom, from, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';

export interface Usuario { id: string; nome: string; cpf: string; }
interface TokenResponse { accessToken: string; tokenType: string; expiresIn: number; usuario: Usuario; }
interface Csrf { headerName: string; token: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly dialogs = inject(MatDialog);
  readonly usuario = signal<Usuario | null>(null);
  private token: string | null = null;
  private expiraEm = 0;
  private renovacao?: Observable<TokenResponse>;
  private geracao = 0;
  tokenAtual() { return this.token; }
  private csrf() { return this.http.get<Csrf>('/api/v1/auth/csrf', { timeout: 15000 }); }
  private post<T>(path: string, body: unknown) {
    return this.csrf().pipe(switchMap(csrf => this.http.post<T>('/api/v1/auth/' + path, body,
      { headers: { [csrf.headerName]: csrf.token }, timeout: 15000 })));
  }
  entrar(cpf: string, senha: string) {
    return this.post<TokenResponse>('login', { cpf, senha }).pipe(tap(response => { this.geracao++; this.aceitar(response); }));
  }
  cadastrar(nome: string, cpf: string, senha: string) {
    return this.post<Usuario>('cadastro', { nome, cpf, senha });
  }
  renovar(): Observable<TokenResponse> {
    if (!this.renovacao) {
      const geracao = this.geracao;
      const executar = () => firstValueFrom(this.post<TokenResponse>('renovar', {}));
      this.renovacao = defer(() => from(
        typeof navigator !== 'undefined' && navigator.locks
          ? navigator.locks.request('desafio-votacao-renovacao', executar)
          : executar()
      )).pipe(
        tap(response => { if (geracao === this.geracao) this.aceitar(response); }),
        catchError(error => { if (geracao === this.geracao) this.limpar(); return throwError(() => error); }),
        finalize(() => { this.renovacao = undefined; }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.renovacao;
  }
  restaurar(): Observable<boolean> {
    if (this.token && Date.now() < this.expiraEm) return of(true);
    return this.renovar().pipe(map(() => true), catchError(() => of(false)));
  }
  sair() {
    return this.post<void>('logout', {}).pipe(tap(() => {
      this.limpar();
      this.dialogs.closeAll();
      void this.router.navigate(['/login']);
    }));
  }
  sessaoExpirada() {
    this.limpar();
    this.dialogs.closeAll();
    if (!this.router.url.startsWith('/login'))
      void this.router.navigate(['/login'], { queryParams: { retorno: this.router.url, expirada: '1' } });
  }
  private aceitar(response: TokenResponse) {
    this.token = response.accessToken;
    this.expiraEm = Date.now() + Math.max(0, response.expiresIn - 10) * 1000;
    this.usuario.set(response.usuario);
  }
  private limpar() { this.geracao++; this.token = null; this.expiraEm = 0; this.usuario.set(null); }
}
