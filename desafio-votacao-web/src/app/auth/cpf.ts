import { ValidatorFn } from '@angular/forms';

export function cpfValido(value: unknown): boolean {
  if (typeof value !== 'string' || !/^(?:[0-9]{11}|[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2})$/.test(value)) return false;
  const cpf = value.replace(/[.-]/g, '');
  if (/^([0-9])\1{10}$/.test(cpf)) return false;
  const digito = (length: number) => {
    let soma = 0;
    for (let i = 0; i < length; i++) soma += Number(cpf[i]) * (length + 1 - i);
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  return digito(9) === Number(cpf[9]) && digito(10) === Number(cpf[10]);
}
export const validarCpf: ValidatorFn = control => cpfValido(control.value) ? null : { cpf: true };
