CREATE TABLE operation_team (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    operation_id BIGINT NOT NULL REFERENCES airsoft_operation(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_operation_team_name UNIQUE (operation_id, name),
    CONSTRAINT uq_operation_team_order UNIQUE (operation_id, sort_order)
);

ALTER TABLE operation_participant
    ADD COLUMN operation_team_id BIGINT REFERENCES operation_team(id) ON DELETE SET NULL;

INSERT INTO operation_team(operation_id, name, capacity, sort_order)
SELECT o.id,
       CASE n WHEN 1 THEN 'Time Alfa' WHEN 2 THEN 'Time Bravo' ELSE 'Time ' || n END,
       CEIL(o.participant_limit::numeric / LEAST(COALESCE(o.team_limit, 2), 20))::integer,
       n
FROM airsoft_operation o
CROSS JOIN LATERAL generate_series(1, LEAST(COALESCE(o.team_limit, 2), 20)) AS n;

WITH ranked AS (
    SELECT op.id AS participant_id, op.operation_id,
           row_number() OVER (PARTITION BY op.operation_id ORDER BY op.requested_at, op.id) AS position
    FROM operation_participant op
    WHERE op.operation_team_id IS NULL AND op.status NOT IN ('CANCELLED', 'REJECTED')
), assigned AS (
    SELECT ranked.participant_id, selected_team.id AS operation_team_id
    FROM ranked
    CROSS JOIN LATERAL (
        SELECT ot.id FROM operation_team ot
        WHERE ot.operation_id = ranked.operation_id
        ORDER BY ot.sort_order
        OFFSET ((ranked.position - 1) % (SELECT count(*) FROM operation_team count_team WHERE count_team.operation_id = ranked.operation_id))
        LIMIT 1
    ) selected_team
)
UPDATE operation_participant participant
SET operation_team_id = assigned.operation_team_id
FROM assigned
WHERE participant.id = assigned.participant_id;

CREATE INDEX idx_operation_team_operation ON operation_team(operation_id, sort_order);
CREATE INDEX idx_operation_participant_team ON operation_participant(operation_team_id, status);
