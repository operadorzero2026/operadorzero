ALTER TABLE airsoft_field
    ALTER COLUMN address_line DROP NOT NULL,
    ADD COLUMN location_url VARCHAR(500);

COMMENT ON COLUMN airsoft_field.location_url IS
    'Link HTTPS de provedor de mapas informado pelo responsavel pelo campo.';
