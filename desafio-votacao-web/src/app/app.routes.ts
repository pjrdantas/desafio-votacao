import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
export const routes: Routes = [
  { path: 'login', title: 'Entrar | Desafio Votação', loadComponent: () => import('./auth/login-page').then(m => m.LoginPage) },
  { path: 'mobile', canActivate: [authGuard], title: 'Assembleia mobile | Desafio Votação', loadComponent: () => import('./mobile/mobile-page').then(m => m.MobilePage) },
  { path: '', pathMatch: 'full', redirectTo: 'pautas' },
  {
    path: 'pautas', canActivate: [authGuard],
    title: 'Pautas da assembleia | Desafio Votação',
    loadComponent: () => import('./pautas/pautas-page').then((m) => m.PautasPage),
  },
  {
    path: 'pautas/:id', canActivate: [authGuard],
    title: 'Pauta | Desafio Votação',
    loadComponent: () => import('./pautas/pauta-page').then((m) => m.PautaPage),
  },
  {
    path: '**',
    loadComponent: () => import('./shared/not-found').then((m) => m.NotFound),
    title: 'Página não encontrada | Desafio Votação',
  },
];
