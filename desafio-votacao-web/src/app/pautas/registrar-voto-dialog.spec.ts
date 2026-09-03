import { AuthService } from '../auth/auth.service';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { RegistrarVotoDialog } from './registrar-voto-dialog';

describe('Confirmação de voto', () => {
  let fixture: ComponentFixture<RegistrarVotoDialog>;
  let http: HttpTestingController;
  const ref = { close: vi.fn(), disableClose: false };
  beforeEach(async () => {
    ref.disableClose = false;
    ref.close.mockClear();
    await TestBed.configureTestingModule({
      imports: [RegistrarVotoDialog],
      providers: [
        { provide: AuthService, useValue: { usuario: () => ({ id: 'usuario-1', nome: 'Associado de teste', cpf: '***.123.456-**' }) } },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: ref },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { id: 'pauta-1', titulo: 'Reforma do salão', criadaEm: '2026-09-02T12:00:00Z' },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RegistrarVotoDialog);
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());
  async function submit() {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
  }
  async function revisar() {
    fixture.componentInstance.form.setValue({ escolha: 'SIM' });
    await fixture.whenStable();
    await submit();
  }
  it('só envia após a revisão, evita envio duplo e aceita sucesso sem corpo', async () => {
    await revisar();
    http.expectNone('/api/v1/pautas/pauta-1/votos');
    expect(fixture.nativeElement.textContent).toContain('Confirmar voto');
    await submit();
    await submit();
    const request = http.expectOne('/api/v1/pautas/pauta-1/votos');
    expect(ref.disableClose).toBe(true);
    expect(request.request.body).toEqual({ escolha: 'SIM' });
    expect(fixture.nativeElement.querySelector('[formControlName=associadoId]')).toBeNull();
    request.flush(null, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Voto registrado!');
    expect(ref.disableClose).toBe(false);
  });
  it('exibe conflito de voto duplicado sem anunciar sucesso', async () => {
    await revisar();
    await submit();
    http
      .expectOne('/api/v1/pautas/pauta-1/votos')
      .flush(
        { error: 'VOTO_DUPLICADO', message: 'O associado já votou nesta pauta.', fields: [] },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('O associado já votou nesta pauta.');
    expect(fixture.nativeElement.textContent).not.toContain('Voto registrado!');
    await submit();
    http.expectNone('/api/v1/pautas/pauta-1/votos');
  });
  it('permite consultar a pauta após timeout sem reenviar o voto', async () => {
    await revisar();
    await submit();
    http.expectOne('/api/v1/pautas/pauta-1/votos').error(new ProgressEvent('timeout'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Consultar pauta');
    await submit();
    http.expectNone('/api/v1/pautas/pauta-1/votos');
  });
});
