CREATE TABLE pauta (
    id UUID PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL CHECK (length(trim(titulo)) > 0),
    descricao VARCHAR(2000) NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE sessao_votacao (
    pauta_id UUID PRIMARY KEY REFERENCES pauta(id),
    aberta_em TIMESTAMPTZ NOT NULL,
    encerra_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sessao_periodo CHECK (encerra_em > aberta_em)
);

CREATE TABLE voto (
    pauta_id UUID NOT NULL REFERENCES sessao_votacao(pauta_id),
    associado_id VARCHAR(100) NOT NULL CHECK (length(trim(associado_id)) > 0),
    escolha VARCHAR(3) NOT NULL CHECK (escolha IN ('SIM', 'NAO')),
    registrado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_voto PRIMARY KEY (pauta_id, associado_id)
);

CREATE INDEX ix_pauta_criada_em ON pauta (criada_em DESC, id);
