import { inject, Injectable, InjectionToken } from '@angular/core';
import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Pauta, Resultado, Sessao, Escolha } from './votacao.models';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api/v1',
});
export const correlationInterceptor: HttpInterceptorFn = (request, next) => {
  const base = inject(API_BASE_URL);
  return next(
    request.url.startsWith(base + '/')
      ? request.clone({ setHeaders: { 'X-Correlation-ID': crypto.randomUUID() } })
      : request,
  );
};

@Injectable({ providedIn: 'root' })
export class VotacaoApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  listar(pagina: number, tamanho = 20) {
    return this.http.get<Pauta[]>(`${this.base}/pautas`, {
      params: { pagina, tamanho },
      timeout: 15000,
    });
  }
  buscar(id: string) {
    return this.http.get<Pauta>(`${this.base}/pautas/${encodeURIComponent(id)}`, {
      timeout: 15000,
    });
  }
  criar(dados: { titulo: string; descricao?: string }) {
    return this.http.post<Pauta>(`${this.base}/pautas`, dados, { timeout: 15000 });
  }
  abrirSessao(id: string, duracaoMinutos: number | null) {
    return this.http.post<Sessao>(
      `${this.base}/pautas/${encodeURIComponent(id)}/sessao`,
      duracaoMinutos === null ? {} : { duracaoMinutos },
      { timeout: 15000 },
    );
  }
  votar(id: string, escolha: Escolha) {
    return this.http.post<void>(
      `${this.base}/pautas/${encodeURIComponent(id)}/votos`,
      { escolha },
      { timeout: 15000 },
    );
  }
  resultado(id: string) {
    return this.http.get<Resultado>(`${this.base}/pautas/${encodeURIComponent(id)}/resultado`, {
      timeout: 15000,
    });
  }
}
