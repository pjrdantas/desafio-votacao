import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/v1/') || /^\/api\/v1\/auth\/(csrf|login|cadastro|renovar|logout)$/.test(request.url))
    return next(request);
  const auth = inject(AuthService);
  const token = auth.tokenAtual();
  const autenticada = token ? request.clone({ setHeaders: { Authorization: 'Bearer ' + token } }) : request;
  return next(autenticada).pipe(catchError(error => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401) return throwError(() => error);
    return auth.renovar().pipe(
      catchError(refreshError => { auth.sessaoExpirada(); return throwError(() => refreshError); }),
      switchMap(() => next(request.clone({ setHeaders: { Authorization: 'Bearer ' + auth.tokenAtual() } })).pipe(
        catchError(retryError => {
          if (retryError instanceof HttpErrorResponse && retryError.status === 401) auth.sessaoExpirada();
          return throwError(() => retryError);
        }),
      )),
    );
  }));
};
