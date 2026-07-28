ALTER TABLE operator_profile
    ADD COLUMN airsoft_started_at DATE;

COMMENT ON COLUMN operator_profile.airsoft_started_at IS
    'Data declarada pelo operador em que iniciou a prática de airsoft.';
