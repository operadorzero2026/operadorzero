-- Marketplace do Mercado Pago (Split 1:1): cada organizador conecta a propria conta por OAuth; a inscricao e paga no
-- Checkout Pro do organizador e a comissao da plataforma sai no proprio pagamento (marketplace_fee).
-- Reaproveita operation_payment / operation_payment_event (Etapa F). Nada existente e removido.

-- Conta Mercado Pago de cada organizador. Tokens cifrados (AES-GCM com chave derivada de AUTH_HASH_KEY).
CREATE TABLE mercadopago_connection (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE RESTRICT,
    mp_user_id BIGINT,
    access_token_enc TEXT,
    refresh_token_enc TEXT,
    public_key VARCHAR(120),
    scope VARCHAR(300),
    live_mode BOOLEAN,
    expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(300),
    connected_at TIMESTAMPTZ,
    refreshed_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_mercadopago_connection_status CHECK (status IN ('CONNECTED', 'NEEDS_RECONNECT', 'DISCONNECTED', 'ERROR'))
);
-- Uma conta Mercado Pago so recebe por um organizador por vez (evita associacao indevida).
CREATE UNIQUE INDEX uq_mercadopago_connection_account ON mercadopago_connection(mp_user_id) WHERE status = 'CONNECTED';

-- Tentativas de conexao: state de uso unico (guardado como hash) e code_verifier do PKCE (cifrado). Vale 10 minutos.
CREATE TABLE mercadopago_oauth_attempt (
    state_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code_verifier_enc TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);
CREATE INDEX idx_mercadopago_oauth_attempt_user ON mercadopago_oauth_attempt(user_id, created_at DESC);

-- Comissao da plataforma: user_id NULL = padrao. Vale a linha mais recente com effective_from <= agora.
-- Linha de organizador com percent NULL = volta a seguir o padrao. Mudancas valem so para pagamentos novos:
-- cada pagamento guarda o percentual e o valor aplicados.
CREATE TABLE platform_commission (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    percent NUMERIC(5,2),
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    note VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_platform_commission_percent CHECK (percent IS NULL OR (percent >= 0 AND percent <= 50)),
    CONSTRAINT ck_platform_commission_default CHECK (user_id IS NOT NULL OR percent IS NOT NULL)
);
CREATE INDEX idx_platform_commission_lookup ON platform_commission(user_id, effective_from DESC, id DESC);

-- Pagamentos: provedor, checkout, comissao prevista x efetiva, tarifa efetiva e liquido do organizador.
ALTER TABLE operation_payment
    ADD COLUMN provider VARCHAR(24),
    ADD COLUMN organizer_user_id BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    ADD COLUMN collector_id BIGINT,
    ADD COLUMN preference_id VARCHAR(80),
    ADD COLUMN checkout_url VARCHAR(600),
    ADD COLUMN payment_type VARCHAR(32),
    ADD COLUMN commission_percent NUMERIC(5,2),
    ADD COLUMN commission_amount NUMERIC(10,2),
    ADD COLUMN commission_effective NUMERIC(10,2),
    ADD COLUMN provider_fee NUMERIC(10,2),
    ADD COLUMN refunded_amount NUMERIC(10,2),
    ADD COLUMN net_organizer NUMERIC(10,2),
    ADD COLUMN fees_confirmed BOOLEAN NOT NULL DEFAULT false;

UPDATE operation_payment SET provider = CASE WHEN method = 'PIX' THEN 'MERCADO_PAGO_DIRECT' ELSE 'ORGANIZER' END WHERE provider IS NULL;
UPDATE operation_payment p SET organizer_user_id = o.organizer_user_id FROM airsoft_operation o WHERE o.id = p.operation_id AND p.organizer_user_id IS NULL;
ALTER TABLE operation_payment ALTER COLUMN provider SET NOT NULL;

ALTER TABLE operation_payment DROP CONSTRAINT ck_operation_payment_method;
ALTER TABLE operation_payment ADD CONSTRAINT ck_operation_payment_method CHECK (method IN ('PIX', 'MANUAL', 'CHECKOUT'));
ALTER TABLE operation_payment DROP CONSTRAINT ck_operation_payment_status;
ALTER TABLE operation_payment ADD CONSTRAINT ck_operation_payment_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'CHARGEBACK'));
ALTER TABLE operation_payment ADD CONSTRAINT ck_operation_payment_provider
    CHECK (provider IN ('MERCADO_PAGO_DIRECT', 'MERCADO_PAGO_MARKETPLACE', 'ORGANIZER'));
CREATE INDEX idx_operation_payment_organizer ON operation_payment(organizer_user_id, created_at DESC);
CREATE INDEX idx_operation_payment_reconcile ON operation_payment(provider, status, created_at) WHERE status = 'PENDING';

-- Notificacoes recebidas (webhook): evita reprocessar a mesma entrega e guarda o resultado.
CREATE TABLE payment_notification (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(120) NOT NULL UNIQUE,
    topic VARCHAR(40),
    data_id VARCHAR(64),
    mp_user_id BIGINT,
    payment_id BIGINT REFERENCES operation_payment(id) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    detail VARCHAR(300),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_payment_notification_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
);
CREATE INDEX idx_payment_notification_data ON payment_notification(data_id, received_at DESC);

ALTER TABLE operation_payment_event DROP CONSTRAINT ck_operation_payment_event_source;
ALTER TABLE operation_payment_event ADD CONSTRAINT ck_operation_payment_event_source
    CHECK (source IN ('PLAYER', 'ORGANIZER', 'WEBHOOK', 'SYNC', 'RECONCILE', 'ADMIN'));
