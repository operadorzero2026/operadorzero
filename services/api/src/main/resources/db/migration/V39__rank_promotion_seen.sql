-- Patentes visiveis: a comemoracao da promocao aparece uma unica vez por patente (o app marca como vista).
ALTER TABLE operator_xp ADD COLUMN promotion_seen_at TIMESTAMPTZ;
-- Promocoes anteriores a esta versao ja foram avisadas por notificacao: nao mostrar a comemoracao de novo.
UPDATE operator_xp SET promotion_seen_at = promoted_at WHERE promoted_at IS NOT NULL;
