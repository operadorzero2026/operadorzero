CREATE TABLE community_category (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_order SMALLINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO community_category(slug, name, display_order) VALUES
    ('jogos-e-operacoes', 'Jogos e Operações', 1),
    ('equipamentos', 'Equipamentos', 2),
    ('duvidas', 'Dúvidas', 3),
    ('manutencao-e-customizacao', 'Manutenção e Customização', 4),
    ('milsim', 'MilSim', 5),
    ('speedsoft', 'Speedsoft', 6),
    ('equipes-e-recrutamento', 'Equipes e Recrutamento', 7),
    ('campos-de-airsoft', 'Campos de Airsoft', 8),
    ('fotos-e-videos', 'Fotos e Vídeos', 9),
    ('compra-venda-e-troca', 'Compra, Venda e Troca', 10),
    ('legislacao-e-transporte', 'Legislação e Transporte', 11),
    ('outros-assuntos', 'Outros assuntos relacionados ao airsoft', 12);

CREATE TABLE community_post (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    category_id BIGINT NOT NULL REFERENCES community_category(id) ON DELETE RESTRICT,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(10000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    comments_locked BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_community_post_author_idempotency UNIQUE(author_user_id, idempotency_key),
    CONSTRAINT ck_community_post_title CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    CONSTRAINT ck_community_post_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 10000),
    CONSTRAINT ck_community_post_status CHECK (status IN ('PUBLISHED','SUSPENDED','DELETED'))
);

CREATE INDEX idx_community_post_feed ON community_post(status, created_at DESC, id DESC);
CREATE INDEX idx_community_post_category ON community_post(category_id, status, created_at DESC);
CREATE INDEX idx_community_post_author ON community_post(author_user_id, created_at DESC);

CREATE TABLE community_post_media (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    post_id BIGINT NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    image_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_media_type CHECK (content_type IN ('image/png','image/jpeg')),
    CONSTRAINT ck_community_media_size CHECK (octet_length(image_data) BETWEEN 1 AND 2097152)
);

CREATE INDEX idx_community_post_media_post ON community_post_media(post_id, id);

CREATE TABLE community_comment (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    post_id BIGINT NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    parent_comment_id BIGINT REFERENCES community_comment(id) ON DELETE CASCADE,
    body VARCHAR(3000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_community_comment_author_idempotency UNIQUE(author_user_id, idempotency_key),
    CONSTRAINT ck_community_comment_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 3000),
    CONSTRAINT ck_community_comment_status CHECK (status IN ('PUBLISHED','SUSPENDED','DELETED'))
);

CREATE INDEX idx_community_comment_post ON community_comment(post_id, status, created_at, id);
CREATE INDEX idx_community_comment_parent ON community_comment(parent_comment_id, created_at, id);

CREATE TABLE community_vote (
    post_id BIGINT NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, user_id)
);

CREATE TABLE community_bookmark (
    post_id BIGINT NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, user_id)
);

CREATE TABLE community_report (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    reporter_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    post_id BIGINT REFERENCES community_post(id) ON DELETE CASCADE,
    comment_id BIGINT REFERENCES community_comment(id) ON DELETE CASCADE,
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    reviewed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    resolution VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT ck_community_report_target CHECK (
        (post_id IS NOT NULL AND comment_id IS NULL) OR
        (post_id IS NULL AND comment_id IS NOT NULL)
    ),
    CONSTRAINT ck_community_report_reason CHECK (
        reason IN ('SPAM','HARASSMENT','HATE','THREAT','PERSONAL_DATA','FRAUD','ILLEGAL','OTHER')
    ),
    CONSTRAINT ck_community_report_status CHECK (status IN ('OPEN','REVIEWED','DISMISSED','ACTIONED'))
);

CREATE UNIQUE INDEX uq_community_report_open_post
    ON community_report(reporter_user_id, post_id)
    WHERE post_id IS NOT NULL AND status = 'OPEN';
CREATE UNIQUE INDEX uq_community_report_open_comment
    ON community_report(reporter_user_id, comment_id)
    WHERE comment_id IS NOT NULL AND status = 'OPEN';
CREATE INDEX idx_community_report_queue ON community_report(status, created_at, id);
