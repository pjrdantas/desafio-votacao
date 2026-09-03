import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { correlationInterceptor, VotacaoApi } from './votacao-api';

describe('Contrato HTTP da votação', () => {
  let api: VotacaoApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(VotacaoApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('envia paginação e correlação, e aceita a lista sem metadados', () => {
    const result = vi.fn();
    api.listar(2).subscribe(result);
    const request = http.expectOne('/api/v1/pautas?pagina=2&tamanho=20');
    expect(request.request.headers.get('X-Correlation-ID')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush([]);
    expect(result).toHaveBeenCalledWith([]);
  });
  it('deixa o backend aplicar a duração padrão quando o campo está vazio', () => {
    api.abrirSessao('pauta-1', null).subscribe();
    const request = http.expectOne('/api/v1/pautas/pauta-1/sessao');
    expect(request.request.body).toEqual({});
    request.flush({
      pautaId: 'pauta-1',
      abertaEm: '2026-09-02T12:00:00Z',
      encerraEm: '2026-09-02T12:01:00Z',
    });
  });
  it('aceita 201 sem corpo ao votar e preserva a escolha NAO', () => {
    const result = vi.fn();
    api.votar('pauta-1', 'NAO').subscribe(result);
    const request = http.expectOne('/api/v1/pautas/pauta-1/votos');
    expect(request.request.body).toEqual({ escolha: 'NAO' });
    request.flush(null, { status: 201, statusText: 'Created' });
    expect(result).toHaveBeenCalledOnce();
  });
});
