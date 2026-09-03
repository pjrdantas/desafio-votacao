import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { aplicarErros, falhaApi, minutosValidos, naoVazio } from './form-errors';

describe('Validação e falhas da API', () => {
  it('rejeita identificação só com espaços', () => {
    expect(new FormControl('   ', naoVazio).invalid).toBe(true);
  });
  it.each([0, -1, 1.5, 2147483648])('rejeita a duração inválida %s', (value) => {
    expect(new FormControl(value, minutosValidos).invalid).toBe(true);
  });
  it.each([null, 1, 60])('aceita a duração %s', (value) => {
    expect(new FormControl(value, minutosValidos).valid).toBe(true);
  });
  it('associa os campos do backend ao formulário e mantém a correlação', () => {
    const form = new FormGroup({ titulo: new FormControl('Título') });
    const error = falhaApi(
      new HttpErrorResponse({
        status: 400,
        error: {
          error: 'VALIDATION_ERROR',
          message: 'Revise os dados.',
          fields: [{ field: 'titulo', message: 'Título inválido.' }],
          correlationId: 'teste-123',
        },
      }),
    );
    aplicarErros(form, error);
    expect(form.controls.titulo.getError('servidor')).toBe('Título inválido.');
    expect(error.correlationId).toBe('teste-123');
  });
  it('trata a perda de conexão durante escrita como resultado incerto', () => {
    const error = falhaApi(new HttpErrorResponse({ status: 0 }), true);
    expect(error.incerta).toBe(true);
    expect(error.mensagem).toContain('pode ter sido concluída');
  });
  it('não expõe o corpo HTML ou detalhes internos de erro 500', () => {
    const error = falhaApi(
      new HttpErrorResponse({ status: 500, error: '<html>stack trace secreto</html>' }),
    );
    expect(error.mensagem).not.toContain('secreto');
  });
});
