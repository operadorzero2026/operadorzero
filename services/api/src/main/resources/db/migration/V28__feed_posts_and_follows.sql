CREATE TABLE operator_follow (
    follower_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    followed_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(follower_user_id, followed_user_id),
    CONSTRAINT ck_operator_follow_distinct CHECK (follower_user_id <> followed_user_id)
);

CREATE INDEX idx_operator_follow_followed ON operator_follow(followed_user_id, created_at DESC);

-- Ordens de Missao: criada agora para que o post ja nasca vinculavel; a participacao vem na fase 2.
CREATE TABLE mission (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    hashtag VARCHAR(40) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    xp_reward INTEGER NOT NULL DEFAULT 0,
    badge_name VARCHAR(60),
    badge_media_asset_id BIGINT REFERENCES media_asset(id) ON DELETE SET NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_mission_status CHECK (status IN ('DRAFT','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT ck_mission_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_mission_xp CHECK (xp_reward BETWEEN 0 AND 10000),
    CONSTRAINT ck_mission_hashtag CHECK (hashtag ~ '^[a-z0-9_]{2,40}$')
);

CREATE UNIQUE INDEX uq_mission_hashtag ON mission(hashtag);
CREATE INDEX idx_mission_active ON mission(status, starts_at, ends_at);

CREATE TABLE feed_post (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    post_type VARCHAR(16) NOT NULL DEFAULT 'MEDIA',
    caption VARCHAR(2200),
    field_id BIGINT REFERENCES airsoft_field(id) ON DELETE SET NULL,
    team_id BIGINT REFERENCES team(id) ON DELETE SET NULL,
    -- Jogo do sistema: usado por Baixa Confirmada e Debriefing.
    operation_id BIGINT REFERENCES airsoft_operation(id) ON DELETE SET NULL,
    mission_id BIGINT REFERENCES mission(id) ON DELETE SET NULL,
    public_share BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_feed_post_author_idempotency UNIQUE(author_user_id, idempotency_key),
    CONSTRAINT ck_feed_post_type CHECK (post_type IN ('MEDIA','ARSENAL','DEBRIEFING')),
    CONSTRAINT ck_feed_post_status CHECK (status IN ('PROCESSING','PUBLISHED','FAILED','HIDDEN','SUSPENDED','DELETED')),
    CONSTRAINT ck_feed_post_debriefing CHECK (post_type <> 'DEBRIEFING' OR operation_id IS NOT NULL),
    CONSTRAINT ck_feed_post_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_feed_post_recent ON feed_post(published_at DESC, id DESC) WHERE status = 'PUBLISHED';
CREATE INDEX idx_feed_post_author ON feed_post(author_user_id, published_at DESC, id DESC) WHERE status = 'PUBLISHED';
CREATE INDEX idx_feed_post_field ON feed_post(field_id, published_at DESC) WHERE status = 'PUBLISHED' AND field_id IS NOT NULL;
CREATE INDEX idx_feed_post_operation ON feed_post(operation_id) WHERE operation_id IS NOT NULL;
CREATE INDEX idx_feed_post_mission ON feed_post(mission_id, published_at DESC) WHERE mission_id IS NOT NULL;

CREATE TABLE feed_post_media (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    media_asset_id BIGINT NOT NULL REFERENCES media_asset(id) ON DELETE RESTRICT,
    position SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY(post_id, position),
    CONSTRAINT uq_feed_post_media_asset UNIQUE(media_asset_id),
    CONSTRAINT ck_feed_post_media_position CHECK (position BETWEEN 0 AND 9)
);

CREATE TABLE feed_post_hashtag (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    tag VARCHAR(40) NOT NULL,
    PRIMARY KEY(post_id, tag),
    CONSTRAINT ck_feed_post_hashtag CHECK (tag ~ '^[a-z0-9_]{1,40}$')
);

CREATE INDEX idx_feed_post_hashtag_tag ON feed_post_hashtag(tag, post_id DESC);

CREATE TABLE feed_post_mention (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    PRIMARY KEY(post_id, user_id)
);

-- Operadores marcados em "Mais opcoes".
CREATE TABLE feed_post_tag (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    PRIMARY KEY(post_id, user_id)
);

CREATE INDEX idx_feed_post_tag_user ON feed_post_tag(user_id);

CREATE TABLE feed_reaction (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, user_id, kind),
    CONSTRAINT ck_feed_reaction_kind CHECK (kind IN ('ON_TARGET','ELIMINATED'))
);

CREATE INDEX idx_feed_reaction_user ON feed_reaction(user_id, created_at DESC);

CREATE TABLE feed_comment (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    author_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    parent_comment_id BIGINT REFERENCES feed_comment(id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_feed_comment_author_idempotency UNIQUE(author_user_id, idempotency_key),
    CONSTRAINT ck_feed_comment_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_feed_comment_status CHECK (status IN ('PUBLISHED','SUSPENDED','DELETED'))
);

CREATE INDEX idx_feed_comment_post ON feed_comment(post_id, status, created_at, id);
CREATE INDEX idx_feed_comment_parent ON feed_comment(parent_comment_id, created_at, id);

CREATE TABLE feed_save (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id, post_id)
);

CREATE INDEX idx_feed_save_user ON feed_save(user_id, created_at DESC);

CREATE TABLE feed_share (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_feed_share_channel CHECK (channel IN ('WHATSAPP','LINK','NATIVE'))
);

CREATE INDEX idx_feed_share_post ON feed_share(post_id, created_at DESC);

-- Uma linha por pessoa e post; atualizada a cada nova visualizacao (base do "nao repetir").
CREATE TABLE feed_view (
    post_id BIGINT NOT NULL REFERENCES feed_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    view_count INTEGER NOT NULL DEFAULT 1,
    watched_ms BIGINT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    first_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, user_id),
    CONSTRAINT ck_feed_view_values CHECK (view_count > 0 AND watched_ms >= 0)
);

CREATE INDEX idx_feed_view_user_recent ON feed_view(user_id, last_viewed_at DESC);

CREATE TABLE feed_post_metrics (
    post_id BIGINT PRIMARY KEY REFERENCES feed_post(id) ON DELETE CASCADE,
    views BIGINT NOT NULL DEFAULT 0,
    unique_viewers BIGINT NOT NULL DEFAULT 0,
    watched_ms BIGINT NOT NULL DEFAULT 0,
    completions BIGINT NOT NULL DEFAULT 0,
    on_target BIGINT NOT NULL DEFAULT 0,
    eliminated BIGINT NOT NULL DEFAULT 0,
    comments BIGINT NOT NULL DEFAULT 0,
    shares BIGINT NOT NULL DEFAULT 0,
    saves BIGINT NOT NULL DEFAULT 0,
    decorations BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Denuncias de conteudo do feed entram na fila de moderacao existente.
ALTER TABLE community_report ADD COLUMN feed_post_id BIGINT REFERENCES feed_post(id) ON DELETE CASCADE;
ALTER TABLE community_report ADD COLUMN feed_comment_id BIGINT REFERENCES feed_comment(id) ON DELETE CASCADE;
