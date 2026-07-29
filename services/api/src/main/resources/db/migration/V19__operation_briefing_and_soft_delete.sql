ALTER TABLE airsoft_operation
    ADD COLUMN briefing VARCHAR(12000),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT;

CREATE INDEX idx_operation_active_discovery
    ON airsoft_operation(status, operation_date, state_code, city)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN airsoft_operation.briefing IS
    'Briefing textual editavel pelo organizador; deve ser renderizado como texto, nunca como HTML.';
COMMENT ON COLUMN airsoft_operation.deleted_at IS
    'Exclusao logica que preserva participantes, chats e trilha de auditoria.';
