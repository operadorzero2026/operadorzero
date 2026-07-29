ALTER TABLE operation_chat_channel
    ADD COLUMN operation_squad_id BIGINT REFERENCES operation_squad(id) ON DELETE CASCADE;

ALTER TABLE operation_chat_channel DROP CONSTRAINT operation_chat_channel_channel_type_check;
ALTER TABLE operation_chat_channel ADD CONSTRAINT operation_chat_channel_channel_type_check
    CHECK (channel_type IN ('GENERAL','TEAM','SQUAD'));
ALTER TABLE operation_chat_channel DROP CONSTRAINT ck_operation_channel_scope;
ALTER TABLE operation_chat_channel ADD CONSTRAINT ck_operation_channel_scope CHECK (
    (channel_type='GENERAL' AND operation_team_id IS NULL AND operation_squad_id IS NULL) OR
    (channel_type='TEAM' AND operation_team_id IS NOT NULL AND operation_squad_id IS NULL) OR
    (channel_type='SQUAD' AND operation_team_id IS NOT NULL AND operation_squad_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_operation_chat_squad
    ON operation_chat_channel(operation_squad_id) WHERE channel_type='SQUAD';

DROP TRIGGER IF EXISTS trg_operation_team_chat_channel ON operation_team;
DROP FUNCTION IF EXISTS create_operation_team_chat_channel();

CREATE OR REPLACE FUNCTION create_operation_squad_chat_channel() RETURNS trigger AS $$
BEGIN
    INSERT INTO operation_chat_channel(operation_id,operation_team_id,operation_squad_id,channel_type,public_read)
    SELECT t.operation_id,t.id,NEW.id,'SQUAD',FALSE FROM operation_team t WHERE t.id=NEW.operation_team_id
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operation_squad_chat_channel AFTER INSERT ON operation_squad
FOR EACH ROW EXECUTE FUNCTION create_operation_squad_chat_channel();

INSERT INTO operation_chat_channel(operation_id,operation_team_id,operation_squad_id,channel_type,public_read)
SELECT t.operation_id,t.id,s.id,'SQUAD',FALSE
FROM operation_squad s JOIN operation_team t ON t.id=s.operation_team_id
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN operation_chat_channel.operation_squad_id IS
    'Escopo do canal privado de esquadrão. Canais TEAM existentes são preservados somente como histórico e não possuem rota pública.';
