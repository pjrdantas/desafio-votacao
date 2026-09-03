import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { MAT_DIALOG_DEFAULT_OPTIONS, MatDialogConfig } from '@angular/material/dialog';
import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { correlationInterceptor } from './core/votacao-api';
registerLocaleData(localePt);
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([correlationInterceptor, authInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    {
      provide: MAT_DIALOG_DEFAULT_OPTIONS,
      useValue: {
        ...new MatDialogConfig(),
        ariaModal: true,
        width: '512px',
        maxWidth: 'calc(100vw - 32px)',
        panelClass: 'votacao-dialog',
        autoFocus: 'first-tabbable',
        restoreFocus: true,
      },
    },
  ],
};
