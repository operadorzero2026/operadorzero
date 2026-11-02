-- Condecorar: o saldo semanal e calculado por week_start (segunda-feira, horario de Brasilia), sem job de reset.
CREATE TABLE decoration (
    id BIGSERIAL PRIMARY KEY,
    giver_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_decoration_giver_post UNIQUE(giver_user_id, post_id),
    CONSTRAINT ck_decoration_week_start CHECK (extract(isodow FROM week_start) = 1)
);

CREATE INDEX idx_decoration_giver_week ON decoration(giver_user_id, week_start);
CREATE INDEX idx_decoration_week_post ON decoration(week_start, post_id);

-- Parametros da gamificacao (cota semanal, criterio do selo HONRA etc.).
CREATE TABLE gamification_setting (
    key VARCHAR(64) PRIMARY KEY,
    value JSONB NOT NULL,
    description VARCHAR(300) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL
);

INSERT INTO gamification_setting(key, value, description) VALUES
    ('decoration.weekly_quota', '3', 'Condecorações por operador por semana (não acumulam).'),
    ('xp.same_pair_window_days', '7', 'Janela para contar interações entre as mesmas duas contas.'),
    ('xp.same_pair_free_actions', '3', 'Interações entre o mesmo par que valem XP cheio dentro da janela.'),
    ('honor.min_confirmations', '5', 'Baixas confirmadas (como atingido) para o selo HONRA.'),
    ('honor.min_confirmation_rate', '0.8', 'Taxa mínima de confirmação das marcações recebidas para o selo HONRA.');

-- Valores de XP por acao; editaveis sem deploy.
CREATE TABLE xp_rule (
    action_code VARCHAR(40) PRIMARY KEY,
    xp INTEGER NOT NULL,
    daily_cap INTEGER,
    same_pair_factor NUMERIC(3,2) NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(200) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_xp_rule_values CHECK (xp >= 0 AND (daily_cap IS NULL OR daily_cap > 0) AND same_pair_factor BETWEEN 0 AND 1)
);

INSERT INTO xp_rule(action_code, xp, daily_cap, same_pair_factor, description) VALUES
    ('POST_PUBLISHED', 10, 50, 1, 'Publicar um post no feed.'),
    ('REACTION_RECEIVED', 1, 100, 0.25, 'Receber uma reação em um post.'),
    ('DECORATION_RECEIVED', 25, NULL, 0.25, 'Receber uma condecoração.'),
    ('COMMENT_PUBLISHED', 2, 20, 0.25, 'Comentar em um post de outro operador.'),
    ('OPERATION_COMPLETED', 50, NULL, 1, 'Participar de um jogo finalizado com presença confirmada.'),
    ('MISSION_COMPLETED', 0, NULL, 1, 'Cumprir uma Ordem de Missão (o XP vem da própria missão).'),
    ('KILL_CONFIRMED_SHOOTER', 15, 60, 0.25, 'Ter uma Baixa Confirmada.'),
    ('KILL_CONFIRMED_TARGET', 10, 60, 0.25, 'Confirmar honestamente que foi atingido.');

-- Livro-razao de XP: um lancamento por acao e origem; estornos entram como valor negativo.
CREATE TABLE xp_event (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    action_code VARCHAR(40) NOT NULL REFERENCES xp_rule(action_code) ON DELETE RESTRICT,
    source_type VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    counterpart_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    raw_xp INTEGER NOT NULL,
    awarded_xp INTEGER NOT NULL,
    reversal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_xp_event_source CHECK (source_type IN ('FEED_POST','FEED_REACTION','DECORATION','FEED_COMMENT','OPERATION','MISSION','KILL_CLAIM'))
);

CREATE UNIQUE INDEX uq_xp_event_award ON xp_event(user_id, action_code, source_type, source_id, counterpart_user_id) NULLS NOT DISTINCT
    WHERE NOT reversal;
CREATE UNIQUE INDEX uq_xp_event_reversal ON xp_event(user_id, action_code, source_type, source_id, counterpart_user_id) NULLS NOT DISTINCT
    WHERE reversal;
CREATE INDEX idx_xp_event_user_day ON xp_event(user_id, action_code, created_at DESC);
CREATE INDEX idx_xp_event_pair ON xp_event(user_id, counterpart_user_id, created_at DESC) WHERE counterpart_user_id IS NOT NULL;

CREATE TABLE rank_tier (
    code VARCHAR(24) PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    position SMALLINT NOT NULL UNIQUE,
    min_xp INTEGER NOT NULL UNIQUE,
    CONSTRAINT ck_rank_tier_min CHECK (min_xp >= 0)
);

INSERT INTO rank_tier(code, name, position, min_xp) VALUES
    ('RECRUTA', 'Recruta', 1, 0),
    ('SOLDADO', 'Soldado', 2, 100),
    ('CABO', 'Cabo', 3, 300),
    ('TERCEIRO_SARGENTO', '3º Sargento', 4, 700),
    ('SEGUNDO_SARGENTO', '2º Sargento', 5, 1300),
    ('PRIMEIRO_SARGENTO', '1º Sargento', 6, 2200),
    ('SUBTENENTE', 'Subtenente', 7, 3500),
    ('ASPIRANTE', 'Aspirante', 8, 5200),
    ('TENENTE', 'Tenente', 9, 7500),
    ('CAPITAO', 'Capitão', 10, 10500),
    ('MAJOR', 'Major', 11, 14500),
    ('CORONEL', 'Coronel', 12, 20000);

CREATE TABLE operator_xp (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    total_xp INTEGER NOT NULL DEFAULT 0,
    rank_code VARCHAR(24) NOT NULL DEFAULT 'RECRUTA' REFERENCES rank_tier(code) ON DELETE RESTRICT,
    promoted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_xp_total CHECK (total_xp >= 0)
);
