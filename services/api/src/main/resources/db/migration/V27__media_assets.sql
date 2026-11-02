-- Midias do feed (posts, Recons, Arsenal, clipes de Debriefing e insignias).
-- Os arquivos ficam fora do banco (MediaStorage); aqui fica so o estado do processamento.
CREATE TABLE media_asset (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL,
    kind VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING',
    detected_type VARCHAR(48),
    original_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    duration_ms INTEGER,
    -- Horario de gravacao lido antes de remover os metadados; e o unico dado de metadado guardado.
    captured_at TIMESTAMPTZ,
    filter_code VARCHAR(32),
    crop_x NUMERIC(6,3),
    crop_y NUMERIC(6,3),
    crop_zoom NUMERIC(5,3),
    storage_prefix VARCHAR(160),
    renditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    poster_key VARCHAR(200),
    blur_placeholder VARCHAR(2000),
    error_code VARCHAR(48),
    attempts SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_media_asset_purpose CHECK (purpose IN ('POST','RECON','ARSENAL','DEBRIEF_CLIP','MISSION_BADGE')),
    CONSTRAINT ck_media_asset_kind CHECK (kind IN ('IMAGE','VIDEO')),
    CONSTRAINT ck_media_asset_status CHECK (status IN ('UPLOADING','QUEUED','PROCESSING','READY','FAILED','DELETED')),
    CONSTRAINT ck_media_asset_size CHECK (original_bytes > 0 AND original_bytes <= 314572800),
    CONSTRAINT ck_media_asset_dimensions CHECK ((width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)),
    CONSTRAINT ck_media_asset_duration CHECK (duration_ms IS NULL OR duration_ms > 0),
    CONSTRAINT ck_media_asset_crop CHECK (crop_zoom IS NULL OR (crop_zoom >= 1 AND crop_zoom <= 5))
);

CREATE INDEX idx_media_asset_owner ON media_asset(owner_user_id, created_at DESC);
CREATE INDEX idx_media_asset_queue ON media_asset(status, created_at, id) WHERE status IN ('QUEUED','PROCESSING');

-- Envio em pedacos, retomavel. Os bytes ficam em disco temporario ate a montagem final.
CREATE TABLE media_upload (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    media_asset_id BIGINT NOT NULL UNIQUE REFERENCES media_asset(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    declared_type VARCHAR(80) NOT NULL,
    total_bytes BIGINT NOT NULL,
    received_bytes BIGINT NOT NULL DEFAULT 0,
    chunk_bytes INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_media_upload_status CHECK (status IN ('OPEN','COMPLETE','ABORTED','EXPIRED')),
    CONSTRAINT ck_media_upload_bytes CHECK (total_bytes > 0 AND received_bytes >= 0 AND received_bytes <= total_bytes),
    CONSTRAINT ck_media_upload_chunk CHECK (chunk_bytes BETWEEN 1048576 AND 16777216)
);

CREATE INDEX idx_media_upload_open ON media_upload(expires_at) WHERE status = 'OPEN';
