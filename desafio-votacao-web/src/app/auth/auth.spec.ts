import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { cpfValido } from './cpf';
import { LoginPage } from './login-page';

describe('Validação de CPF', () => {
  it('aceita os dois formatos e confere ambos os dígitos', () => {
    expect(cpfValido('529.982.247-25')).toBe(true);
    expect(cpfValido('52998224725')).toBe(true);
    for (const cpf of ['11111111111','00000000000','52998224724','52998224715','529a98224725','123',null,''])
      expect(cpfValido(cpf)).toBe(false);
  });
});

describe('Sessão e autorização HTTP', () => {
  let http: HttpTestingController, client: HttpClient, auth: AuthService;
  const perfil = { id:'usuario-1',nome:'Avaliador',cpf:'***.982.247-**' };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),
      { provide:MatDialog,useValue:{ closeAll:vi.fn() } }] });
    http=TestBed.inject(HttpTestingController); client=TestBed.inject(HttpClient); auth=TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router),'navigate').mockResolvedValue(true);
  });
  afterEach(() => http.verify());
  function csrf() { http.expectOne('/api/v1/auth/csrf').flush({headerName:'X-XSRF-TOKEN',token:'csrf-teste'}); }
  function logar() {
    auth.entrar('52998224725','senha-segura').subscribe();
    csrf();
    const req=http.expectOne('/api/v1/auth/login');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-teste');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({accessToken:'jwt-original',tokenType:'Bearer',expiresIn:600,usuario:perfil});
  }
  it('mantém JWT em memória e só o envia à API protegida da aplicação', () => {
    const storage=vi.spyOn(Storage.prototype,'setItem');
    logar();
    client.get('/api/v1/pautas').subscribe();
    const api=http.expectOne('/api/v1/pautas');
    expect(api.request.headers.get('Authorization')).toBe('Bearer jwt-original'); api.flush([]);
    client.get('https://outro.test/api/v1/pautas').subscribe();
    const externo=http.expectOne('https://outro.test/api/v1/pautas');
    expect(externo.request.headers.has('Authorization')).toBe(false); externo.flush({});
    expect(storage).not.toHaveBeenCalled(); storage.mockRestore();
  });
  it('após 401 renova uma vez e repete com o novo JWT', async () => {
    logar();
    client.get('/api/v1/pautas').subscribe();
    http.expectOne('/api/v1/pautas').flush({}, {status:401,statusText:'Unauthorized'});
    await vi.waitFor(() => csrf());
    const refresh=http.expectOne('/api/v1/auth/renovar');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({accessToken:'jwt-novo',expiresIn:600,usuario:perfil});
    await vi.waitFor(() => {
      const retry=http.expectOne('/api/v1/pautas');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer jwt-novo'); retry.flush([]);
    });
  });
  it('não repete um voto após erro de servidor e evita duplicação incerta', () => {
    logar();
    client.post('/api/v1/pautas/1/votos',{escolha:'SIM'}).subscribe({error:()=>{}});
    http.expectOne('/api/v1/pautas/1/votos').flush({}, {status:503,statusText:'Unavailable'});
    http.expectNone('/api/v1/auth/csrf');
    http.expectNone('/api/v1/pautas/1/votos');
  });
  it('logout limpa o usuário e o token somente após revogação no servidor', () => {
    logar();
    auth.sair().subscribe(); csrf();
    expect(auth.usuario()).toEqual(perfil);
    http.expectOne('/api/v1/auth/logout').flush(null,{status:204,statusText:'No Content'});
    expect(auth.usuario()).toBeNull(); expect(auth.tokenAtual()).toBeNull();
  });
  it('falha ao restaurar a sessão não autoriza a navegação', async () => {
    let restaurada=true;
    auth.restaurar().subscribe(value=>restaurada=value);
    await vi.waitFor(() => csrf());
    http.expectOne('/api/v1/auth/renovar').flush({}, {status:401,statusText:'Unauthorized'});
    await vi.waitFor(()=>expect(restaurada).toBe(false));
  });
});

describe('Tela de login', () => {
  const auth={usuario:signal(null),entrar:vi.fn(()=>of({})),cadastrar:vi.fn(()=>of({}))};
  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({imports:[LoginPage],providers:[
      provideRouter([]),{provide:AuthService,useValue:auth},
      {provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:convertToParamMap({retorno:'https://outro.test'})}}}
    ]}).compileComponents();
  });
  it('rejeita CPF inválido antes de chamar a API', () => {
    const fixture=TestBed.createComponent(LoginPage);
    fixture.componentInstance.form.patchValue({cpf:'11111111111',senha:'senha-teste'});
    fixture.componentInstance.enviar();
    expect(auth.entrar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.controls.cpf.hasError('cpf')).toBe(true);
  });
  it('entra com CPF válido e rejeita redirecionamento externo', () => {
    const nav=vi.spyOn(TestBed.inject(Router),'navigateByUrl').mockResolvedValue(true);
    const page=TestBed.createComponent(LoginPage).componentInstance;
    page.form.patchValue({cpf:'529.982.247-25',senha:'senha-teste'});
    page.enviar();
    expect(auth.entrar).toHaveBeenCalledWith('529.982.247-25','senha-teste');
    expect(nav).toHaveBeenCalledWith('/pautas');
    expect(page.form.controls.senha.value).toBe('');
  });
  it('cadastro exige confirmação e muda para login ao criar a conta', () => {
    vi.spyOn(TestBed.inject(Router),'navigateByUrl').mockResolvedValue(true);
    const page=TestBed.createComponent(LoginPage).componentInstance;
    page.alternar();
    page.form.setValue({nome:'Teste',cpf:'52998224725',senha:'senha-teste-2026',confirmacao:'diferente'});
    page.enviar(); expect(auth.cadastrar).not.toHaveBeenCalled();
    page.form.controls.confirmacao.setValue('senha-teste-2026'); page.enviar();
    expect(auth.cadastrar).toHaveBeenCalledOnce(); expect(auth.entrar).toHaveBeenCalledOnce();
    expect(page.cadastro()).toBe(false);
  });
});
