-- Pagamento das inscricoes: Pix pelo Mercado Pago ou confirmacao manual do organizador.
-- O status financeiro e independente do status da inscricao e nunca e apagado (cancelar a inscricao nao mexe aqui).

CREATE TABLE operation_payment (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE RESTRICT,
    participant_id BIGINT NOT NULL REFERENCES operation_participant(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    amount NUMERIC(10,2) NOT NULL,
    method VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider_payment_id VARCHAR(64) UNIQUE,
    provider_status VARCHAR(32),
    provider_status_detail VARCHAR(64),
    pix_code TEXT,
    pix_qr_base64 TEXT,
    ticket_url VARCHAR(500),
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    recorded_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    note VARCHAR(300),
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_operation_payment_method CHECK (method IN ('PIX', 'MANUAL')),
    CONSTRAINT ck_operation_payment_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'REFUNDED'))
);

-- No maximo um Pix pendente por inscricao (evita cobrancas duplicadas).
CREATE UNIQUE INDEX uq_operation_payment_pending ON operation_payment(participant_id) WHERE status = 'PENDING';
CREATE INDEX idx_operation_payment_operation ON operation_payment(operation_id, status, created_at DESC);
CREATE INDEX idx_operation_payment_user ON operation_payment(user_id, created_at DESC);

-- Historico de cada mudanca (webhook, consulta, organizador, jogador).
CREATE TABLE operation_payment_event (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES operation_payment(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    actor_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    detail VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_payment_event_source CHECK (source IN ('PLAYER', 'ORGANIZER', 'WEBHOOK', 'SYNC'))
);
CREATE INDEX idx_operation_payment_event ON operation_payment_event(payment_id, created_at);

COMMENT ON COLUMN airsoft_operation.registration_price IS
    'Valor da inscricao. Com o Mercado Pago configurado, o jogador paga por Pix; o organizador tambem pode confirmar pagamentos manuais.';
COMMENT ON COLUMN operation_participant.payment_confirmed_at IS
    'Momento da confirmacao real do pagamento (Pix aprovado ou confirmacao do organizador). Libera os documentos de operacoes pagas.';
