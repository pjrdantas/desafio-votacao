import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, MatButtonModule],
  template:
    '<main id="conteudo" class="container empty-state"><h1>Página não encontrada</h1><p>Volte para as pautas da assembleia para continuar.</p><a mat-flat-button routerLink="/pautas">Voltar para pautas</a></main>',
})
export class NotFound {}
