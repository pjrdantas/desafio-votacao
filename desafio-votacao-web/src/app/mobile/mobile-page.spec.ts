import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MobilePage, caminhoMobile } from './mobile-page';

describe('Contrato mobile', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({imports:[MobilePage],providers:[
      provideHttpClient(),provideHttpClientTesting(),provideRouter([])
    ]}).compileComponents();
  });
  afterEach(()=>TestBed.inject(HttpTestingController).verify());
  it('restringe os callbacks ao caminho mobile e ao proxy local', () => {
    expect(caminhoMobile('https://backend.exemplo/api/v1/mobile/pautas')).toBe('/api/v1/mobile/pautas');
    for (const url of ['javascript:alert(1)','https://outro.test/admin','/api/v1/mobile/../auth/me','/api/v1/mobile?token=x'])
      expect(()=>caminhoMobile(url)).toThrow();
  });
  it('renderiza campos e envia só valores editáveis junto ao body da ação', async () => {
    const fixture=TestBed.createComponent(MobilePage), http=TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/mobile').flush({tipo:'FORMULARIO',titulo:'Nova pauta',itens:[
      {id:'titulo',tipo:'TEXTO',label:'Título',obrigatorio:true,somenteLeitura:false},
      {id:'associado',tipo:'TEXTO',label:'Associado',valor:'Usuário autenticado',somenteLeitura:true},
    ],botoes:[{label:'Salvar',url:'http://backend:8080/api/v1/mobile/pautas',body:{}}]});
    await fixture.whenStable();
    const acao={label:'Salvar',url:'http://backend:8080/api/v1/mobile/pautas',body:{}};
    fixture.componentInstance.executar(acao); http.expectNone('/api/v1/mobile/pautas');
    fixture.componentInstance.form.get('titulo')!.setValue('Assembleia');
    fixture.componentInstance.executar(acao);
    const req=http.expectOne('/api/v1/mobile/pautas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({titulo:'Assembleia'});
    req.flush({tipo:'SELECAO',titulo:'Pautas',opcoes:[]});
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Pautas');
  });
  it('navega sem exigir campos e bloqueia repetição após resultado incerto', () => {
    const page=TestBed.createComponent(MobilePage).componentInstance, http=TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/mobile').flush({tipo:'FORMULARIO',titulo:'Nova',itens:[
      {id:'titulo',tipo:'TEXTO',label:'Título',obrigatorio:true,somenteLeitura:false}
    ]});
    page.executar({label:'Voltar',url:'/api/v1/mobile',body:{}});
    http.expectOne('/api/v1/mobile').flush({tipo:'FORMULARIO',titulo:'Votar',itens:[]});
    page.executar({label:'Sim',url:'/api/v1/mobile/pautas/1/votos',body:{escolha:'SIM'}});
    http.expectOne('/api/v1/mobile/pautas/1/votos').flush({}, {status:503,statusText:'Unavailable'});
    expect(page.incerta()).toBe(true);
  });
});
