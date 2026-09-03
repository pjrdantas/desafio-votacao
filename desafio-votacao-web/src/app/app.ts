import { Component } from '@angular/core';
import { AuthBar } from './auth/auth-bar';
import { RouterOutlet } from '@angular/router';
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AuthBar],
  template: '<a class="skip-link" href="#conteudo">Ir para o conteúdo</a><app-auth-bar /><router-outlet />',
})
export class App {}
