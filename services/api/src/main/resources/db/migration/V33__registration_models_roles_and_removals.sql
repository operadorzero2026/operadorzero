-- Etapa A das operacoes: modelo de inscricao (lista unica ou por equipes), funcoes do jogador,
-- encerramento manual, remocao com justificativa e pedidos de troca de equipe. Tudo aditivo.

-- Operacoes que ja existem continuam como hoje (por equipes); as novas comecam em lista unica.
ALTER TABLE airsoft_operation
    ADD COLUMN registration_model VARCHAR(12) NOT NULL DEFAULT 'TEAMS',
    ADD COLUMN registration_closed_at TIMESTAMPTZ,
    ADD COLUMN registration_closed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN team_change_requests_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_operation_registration_model CHECK (registration_model IN ('SINGLE_LIST','TEAMS'));
ALTER TABLE airsoft_operation ALTER COLUMN registration_model SET DEFAULT 'SINGLE_LIST';

-- Funcao principal (obrigatoria nas novas inscricoes) e as duas complementares, independentes entre si.
ALTER TABLE operation_participant
    ADD COLUMN primary_role VARCHAR(8),
    ADD COLUMN is_medic BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_radio BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN role_updated_at TIMESTAMPTZ,
    ADD COLUMN removed_at TIMESTAMPTZ,
    ADD COLUMN removed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN removal_reason VARCHAR(500),
    ADD COLUMN return_authorized_at TIMESTAMPTZ,
    ADD COLUMN return_authorized_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_operation_participant_role CHECK (primary_role IS NULL OR primary_role IN ('ASSAULT','SNIPER','DMR'));

-- REMOVED: retirado pelo organizador; so volta a se inscrever depois de "Autorizar retorno".
ALTER TABLE operation_participant DROP CONSTRAINT ck_operation_participant_status;
ALTER TABLE operation_participant ADD CONSTRAINT ck_operation_participant_status
    CHECK (status IN ('REQUESTED','APPROVED','REJECTED','WAITING_LIST','CANCELLED','CONFIRMED','CHECKED_IN','REMOVED'));

-- Historico da inscricao (inscricao, cancelamento, remocao, liberacao, troca de equipe). Nunca e apagado.
CREATE TABLE operation_participant_event (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    participant_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    kind VARCHAR(24) NOT NULL,
    actor_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    reason VARCHAR(500),
    from_team_id BIGINT REFERENCES operation_team(id) ON DELETE SET NULL,
    to_team_id BIGINT REFERENCES operation_team(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_participant_event_kind CHECK (kind IN
        ('REGISTERED','CANCELLED','REMOVED','RETURN_AUTHORIZED','TEAM_CHANGED','ROLE_CHANGED','REGISTRATION_CLOSED','REGISTRATION_REOPENED'))
);
CREATE INDEX idx_operation_participant_event ON operation_participant_event(operation_id, participant_user_id, created_at DESC);

-- Pedido de troca de equipe: o jogador continua na equipe atual ate o organizador aprovar.
CREATE TABLE operation_team_change_request (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL REFERENCES operation_participant(id) ON DELETE CASCADE,
    from_team_id BIGINT REFERENCES operation_team(id) ON DELETE SET NULL,
    to_team_id BIGINT NOT NULL REFERENCES operation_team(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ,
    decided_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT ck_team_change_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
CREATE UNIQUE INDEX uq_team_change_pending ON operation_team_change_request(participant_id) WHERE status = 'PENDING';
CREATE INDEX idx_team_change_operation ON operation_team_change_request(operation_id, status, created_at DESC);
