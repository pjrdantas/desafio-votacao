import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FalhaApi } from './votacao.models';

export const naoVazio: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0 ? null : { required: true };
export const minutosValidos: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  if (value === null || value === '') return null;
  return Number.isInteger(value) && value > 0 && value <= 2147483647 ? null : { minutos: true };
};

export function falhaApi(error: unknown, escrita = false): FalhaApi {
  const http = error instanceof HttpErrorResponse ? error : null;
  const body = http?.error;
  const api = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const incerta = escrita && (!http || http.status === 0 || http.status >= 500);
  let mensagem =
    typeof api.message === 'string' && http && http.status < 500
      ? api.message
      : 'Não foi possível concluir a solicitação. Tente novamente em instantes.';
  if (http?.status === 0)
    mensagem = 'Não foi possível acessar o serviço. Verifique sua conexão e tente novamente.';
  if (http?.status === 404 && api.error !== 'UNABLE_TO_VOTE' && api.error !== 'CPF_NAO_ENCONTRADO')
    mensagem = 'Pauta não encontrada. Volte para a lista e selecione uma pauta disponível.';
  if (incerta)
    mensagem =
      'Não foi possível confirmar a operação. Ela pode ter sido concluída. Consulte a pauta antes de tentar novamente.';
  return {
    status: http?.status ?? 0,
    codigo: typeof api.error === 'string' ? api.error : 'CONNECTION_ERROR',
    mensagem,
    campos: Array.isArray(api.fields)
      ? api.fields.filter(
          (f: unknown): f is { field: string; message: string } =>
            !!f &&
            typeof f === 'object' &&
            'field' in f &&
            typeof f.field === 'string' &&
            'message' in f &&
            typeof f.message === 'string',
        )
      : [],
    correlationId:
      typeof api.correlationId === 'string'
        ? api.correlationId
        : (http?.headers.get('X-Correlation-ID') ?? undefined),
    incerta,
  };
}

export function aplicarErros(form: FormGroup, falha: FalhaApi): void {
  for (const campo of falha.campos) {
    const control = form.get(campo.field);
    control?.setErrors({ ...control.errors, servidor: campo.message });
    control?.markAsTouched();
  }
}
export function erroCampo(control: AbstractControl, padrao: string): string {
  return (
    control.getError('servidor') ??
    (control.hasError('maxlength')
      ? `Use até ${control.getError('maxlength').requiredLength} caracteres.`
      : padrao)
  );
}
