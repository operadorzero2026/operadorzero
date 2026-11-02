CREATE TABLE recon (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    media_asset_id BIGINT NOT NULL UNIQUE REFERENCES media_asset(id) ON DELETE RESTRICT,
    overlay_text VARCHAR(140),
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recon_status CHECK (status IN ('PROCESSING','ACTIVE','FAILED','EXPIRED','SUSPENDED','DELETED')),
    CONSTRAINT ck_recon_expiry CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '25 hours')
);

CREATE INDEX idx_recon_active ON recon(author_user_id, expires_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_recon_expiry ON recon(expires_at) WHERE status IN ('PROCESSING','ACTIVE');

CREATE TABLE recon_view (
    recon_id BIGINT NOT NULL REFERENCES recon(id) ON DELETE CASCADE,
    viewer_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(recon_id, viewer_user_id)
);

-- Destaques fixos no perfil; um Recon dentro de um Dossie nao e apagado ao expirar.
CREATE TABLE dossier (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    title VARCHAR(40) NOT NULL,
    cover_media_asset_id BIGINT REFERENCES media_asset(id) ON DELETE SET NULL,
    position SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dossier_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 40)
);

CREATE INDEX idx_dossier_user ON dossier(user_id, position, id);

CREATE TABLE dossier_recon (
    dossier_id BIGINT NOT NULL REFERENCES dossier(id) ON DELETE CASCADE,
    recon_id BIGINT NOT NULL REFERENCES recon(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL DEFAULT 0,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(dossier_id, recon_id)
);

CREATE INDEX idx_dossier_recon_recon ON dossier_recon(recon_id);

ALTER TABLE community_report ADD COLUMN recon_id BIGINT REFERENCES recon(id) ON DELETE CASCADE;

ALTER TABLE community_report DROP CONSTRAINT ck_community_report_target;
ALTER TABLE community_report ADD CONSTRAINT ck_community_report_target CHECK (
    num_nonnulls(post_id, comment_id, feed_post_id, feed_comment_id, recon_id) = 1
);

CREATE UNIQUE INDEX uq_community_report_open_feed_post
    ON community_report(reporter_user_id, feed_post_id) WHERE feed_post_id IS NOT NULL AND status = 'OPEN';
CREATE UNIQUE INDEX uq_community_report_open_feed_comment
    ON community_report(reporter_user_id, feed_comment_id) WHERE feed_comment_id IS NOT NULL AND status = 'OPEN';
CREATE UNIQUE INDEX uq_community_report_open_recon
    ON community_report(reporter_user_id, recon_id) WHERE recon_id IS NOT NULL AND status = 'OPEN';
