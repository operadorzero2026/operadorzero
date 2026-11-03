-- Missoes e documentos (PDF) da operacao. O arquivo fica numa pasta privada do servidor (nunca servida direto pelo Nginx);
-- cada leitura passa pela API, que confere inscricao, pagamento e horario de inicio.
CREATE TABLE operation_document (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    file_name VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_operation_document_size CHECK (size_bytes > 0 AND size_bytes <= 9437184),
    CONSTRAINT ck_operation_document_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 120)
);
CREATE INDEX idx_operation_document ON operation_document(operation_id, created_at) WHERE deleted_at IS NULL;

-- Quem enviou, substituiu ou removeu cada documento, e quando.
CREATE TABLE operation_document_event (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES operation_document(id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL,
    actor_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    file_name VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_operation_document_event_kind CHECK (kind IN ('UPLOADED','REPLACED','REMOVED'))
);
CREATE INDEX idx_operation_document_event ON operation_document_event(document_id, created_at DESC);
