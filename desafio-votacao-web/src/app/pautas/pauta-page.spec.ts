import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PautaPage } from './pauta-page';

describe('Situação da pauta e resultado', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PautaPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: 'pauta-1' })),
            snapshot: { queryParamMap: convertToParamMap({}) },
          },
        },
      ],
    }).compileComponents();
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('preserva a última apuração e bloqueia o voto quando a atualização falha', async () => {
    const fixture = TestBed.createComponent(PautaPage);
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne('/api/v1/pautas/pauta-1')
      .flush({
        id: 'pauta-1',
        titulo: 'Assembleia',
        criadaEm: '2026-09-02T12:00:00Z',
        descricao: '',
      });
    http
      .expectOne('/api/v1/pautas/pauta-1/resultado')
      .flush({
        pautaId: 'pauta-1',
        situacao: 'ABERTA',
        sim: 2,
        nao: 1,
        total: 3,
        decisao: 'PENDENTE',
        apuradoEm: '2026-09-02T12:00:00Z',
      });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Registrar voto');
    const button = [...fixture.nativeElement.querySelectorAll('button')].find((node: any) =>
      node.textContent.includes('Atualizar resultado'),
    ) as HTMLButtonElement;
    button.click();
    http
      .expectOne('/api/v1/pautas/pauta-1/resultado')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Exibindo a última consulta');
    expect(fixture.nativeElement.textContent).not.toContain('Registrar voto');
    expect(fixture.componentInstance.resultado()?.total).toBe(3);
  });
  it('informa pauta inexistente e oferece retorno para a lista', async () => {
    const fixture = TestBed.createComponent(PautaPage);
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne('/api/v1/pautas/pauta-1')
      .flush({ message: 'Pauta não encontrada.' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Pauta não encontrada');
    expect(fixture.nativeElement.textContent).toContain('Voltar para pautas');
    http.expectNone('/api/v1/pautas/pauta-1/resultado');
  });
  it('atualiza automaticamente, respeita o relógio do servidor e para após encerramento', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2030-01-01T00:00:00Z'));
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const fixture = TestBed.createComponent(PautaPage);
    const http = TestBed.inject(HttpTestingController);
    try {
      http.expectOne('/api/v1/pautas/pauta-1').flush({id:'pauta-1',titulo:'Pauta',descricao:'',criadaEm:'2026-09-02T12:00:00Z'});
      const parcial={pautaId:'pauta-1',situacao:'ABERTA',sim:1,nao:0,total:1,decisao:'PENDENTE',
        apuradoEm:'2026-09-02T12:00:00Z',abertaEm:'2026-09-02T12:00:00Z',encerraEm:'2026-09-02T12:00:07Z'};
      http.expectOne('/api/v1/pautas/pauta-1/resultado').flush(parcial);
      expect(fixture.componentInstance.segundosRestantes()).toBe(7);
      vi.advanceTimersByTime(5000);
      http.expectOne('/api/v1/pautas/pauta-1/resultado').flush({...parcial,sim:2,total:2,apuradoEm:'2026-09-02T12:00:05Z'});
      expect(fixture.componentInstance.resultado()?.total).toBe(2);
      vi.advanceTimersByTime(2000);
      expect(fixture.componentInstance.segundosRestantes()).toBe(0);
      http.expectOne('/api/v1/pautas/pauta-1/resultado').flush({...parcial,situacao:'ENCERRADA',decisao:'APROVADA',apuradoEm:'2026-09-02T12:00:07Z'});
      vi.advanceTimersByTime(15000);
      http.expectNone('/api/v1/pautas/pauta-1/resultado');
    } finally {
      fixture.destroy(); vi.useRealTimers(); vi.restoreAllMocks();
    }
  });
});
