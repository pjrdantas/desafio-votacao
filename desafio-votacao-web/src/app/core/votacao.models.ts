export interface Pauta {
  id: string;
  titulo: string;
  descricao: string | null;
  criadaEm: string;
}
export interface Sessao {
  pautaId: string;
  abertaEm: string;
  encerraEm: string;
}
export type Escolha = 'SIM' | 'NAO';
export type Situacao = 'NAO_ABERTA' | 'ABERTA' | 'ENCERRADA';
export type Decisao = 'PENDENTE' | 'APROVADA' | 'REJEITADA' | 'EMPATE' | 'SEM_VOTOS';
export interface Resultado {
  pautaId: string;
  situacao: Situacao;
  sim: number;
  nao: number;
  total: number;
  decisao: Decisao;
  apuradoEm: string;
  abertaEm?: string | null;
  encerraEm?: string | null;
}
export interface FalhaApi {
  status: number;
  codigo: string;
  mensagem: string;
  campos: { field: string; message: string }[];
  correlationId?: string;
  incerta: boolean;
}
export const SITUACOES: Record<Situacao, string> = {
  NAO_ABERTA: 'Sessão não iniciada',
  ABERTA: 'Votação aberta',
  ENCERRADA: 'Votação encerrada',
};
export const DECISOES: Record<Decisao, string> = {
  PENDENTE: 'Pendente',
  APROVADA: 'Aprovada',
  REJEITADA: 'Rejeitada',
  EMPATE: 'Empate',
  SEM_VOTOS: 'Sem votos',
};
