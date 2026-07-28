CREATE TABLE airsoft_field (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    responsible_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    phone VARCHAR(24),
    contact_email VARCHAR(254),
    address_line VARCHAR(160) NOT NULL,
    address_number VARCHAR(20),
    complement VARCHAR(80),
    district VARCHAR(80),
    city VARCHAR(80) NOT NULL,
    state_code CHAR(2) NOT NULL,
    postal_code VARCHAR(9),
    region VARCHAR(40),
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    rules VARCHAR(4000),
    opening_hours VARCHAR(1000),
    amenities VARCHAR(1000),
    maximum_capacity INTEGER,
    average_price NUMERIC(10,2),
    payment_methods VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_airsoft_field_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_airsoft_field_capacity CHECK (maximum_capacity IS NULL OR maximum_capacity > 0),
    CONSTRAINT ck_airsoft_field_price CHECK (average_price IS NULL OR average_price >= 0)
);

CREATE INDEX idx_airsoft_field_location ON airsoft_field(state_code, city) WHERE status = 'ACTIVE';
CREATE INDEX idx_airsoft_field_responsible ON airsoft_field(responsible_user_id, updated_at DESC);

CREATE TABLE field_map (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    field_id BIGINT NOT NULL REFERENCES airsoft_field(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    terrain_type VARCHAR(24) NOT NULL,
    description VARCHAR(1000),
    approximate_size VARCHAR(80),
    capacity INTEGER,
    respawn_areas VARCHAR(1000),
    bases VARCHAR(1000),
    objectives VARCHAR(1000),
    strategic_points VARCHAR(1000),
    neutral_areas VARCHAR(1000),
    prohibited_areas VARCHAR(1000),
    routes VARCHAR(1000),
    notes VARCHAR(1000),
    specific_rules VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_field_map_terrain CHECK (terrain_type IN ('CQB','URBAN','FOREST','MIXED','INDUSTRIAL','OPEN','NIGHT','INDOOR','OUTDOOR')),
    CONSTRAINT ck_field_map_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_field_map_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE UNIQUE INDEX uq_field_map_active_name ON field_map(field_id, lower(name)) WHERE status = 'ACTIVE';
CREATE INDEX idx_field_map_field ON field_map(field_id, status, name);

CREATE TABLE airsoft_operation (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organizer_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    field_id BIGINT NOT NULL REFERENCES airsoft_field(id) ON DELETE RESTRICT,
    map_id BIGINT REFERENCES field_map(id) ON DELETE SET NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state_code CHAR(2) NOT NULL,
    operation_date DATE NOT NULL,
    presentation_time TIME NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    modality VARCHAR(40) NOT NULL,
    custom_modality VARCHAR(100),
    rules VARCHAR(6000),
    participant_limit INTEGER NOT NULL,
    team_limit INTEGER,
    registration_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    payment_methods VARCHAR(500),
    minimum_age SMALLINT NOT NULL DEFAULT 18,
    required_equipment VARCHAR(2000),
    fps_limit INTEGER,
    entry_mode VARCHAR(24) NOT NULL DEFAULT 'INDIVIDUAL',
    approval_required BOOLEAN NOT NULL DEFAULT TRUE,
    waiting_list_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_operation_status CHECK (status IN ('DRAFT','PUBLISHED','REGISTRATION_OPEN','FULL','CONFIRMATION_PENDING','IN_PROGRESS','FINISHED','CANCELLED')),
    CONSTRAINT ck_operation_entry_mode CHECK (entry_mode IN ('INDIVIDUAL','TEAM','BOTH','INVITATION')),
    CONSTRAINT ck_operation_times CHECK (start_time < end_time),
    CONSTRAINT ck_operation_limits CHECK (participant_limit > 0 AND (team_limit IS NULL OR team_limit > 0)),
    CONSTRAINT ck_operation_price CHECK (registration_price >= 0),
    CONSTRAINT ck_operation_age CHECK (minimum_age BETWEEN 12 AND 99),
    CONSTRAINT ck_operation_fps CHECK (fps_limit IS NULL OR fps_limit BETWEEN 100 AND 1000)
);

CREATE INDEX idx_operation_discovery ON airsoft_operation(status, operation_date, state_code, city);
CREATE INDEX idx_operation_field ON airsoft_operation(field_id, operation_date DESC);
CREATE INDEX idx_operation_organizer ON airsoft_operation(organizer_user_id, updated_at DESC);

CREATE TABLE operation_participant (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    team_id BIGINT REFERENCES team(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    payment_confirmed_at TIMESTAMPTZ,
    presence_confirmed_at TIMESTAMPTZ,
    checked_in_at TIMESTAMPTZ,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_operation_participant UNIQUE (operation_id, user_id),
    CONSTRAINT ck_operation_participant_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','WAITING_LIST','CANCELLED','CONFIRMED','CHECKED_IN'))
);

CREATE INDEX idx_operation_participant_user ON operation_participant(user_id, status, requested_at DESC);
CREATE INDEX idx_operation_participant_operation ON operation_participant(operation_id, status, requested_at);

COMMENT ON COLUMN airsoft_operation.registration_price IS
    'Valor apenas informativo; o OperadorZero nao processa nem intermedeia pagamentos.';
COMMENT ON COLUMN airsoft_operation.payment_methods IS
    'Formas aceitas pelo organizador, exibidas apenas como informacao.';
