-- Recon com editor: elementos sobre a midia (texto, desenho, adesivos, mencoes) em coordenadas relativas (0..1).
-- Tudo aditivo: Recons antigos continuam com overlay_text e elements nulo.
ALTER TABLE recon ADD COLUMN elements JSONB;
ALTER TABLE recon ADD COLUMN audio_muted BOOLEAN NOT NULL DEFAULT FALSE;
-- Dossie escolhido nas configuracoes: o Recon entra nele assim que ficar pronto.
ALTER TABLE recon ADD COLUMN auto_dossier_id BIGINT REFERENCES dossier(id) ON DELETE SET NULL;

-- "Silenciar" no editor tira o audio do arquivo final, nao so da reproducao.
ALTER TABLE media_asset ADD COLUMN strip_audio BOOLEAN NOT NULL DEFAULT FALSE;

-- Operadores marcados com @: quem foi marcado pode ver aquele Recon; notified_at evita aviso repetido.
CREATE TABLE recon_mention (
    recon_id BIGINT NOT NULL REFERENCES recon(id) ON DELETE CASCADE,
    mentioned_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    notified_at TIMESTAMPTZ,
    PRIMARY KEY (recon_id, mentioned_user_id)
);

CREATE INDEX idx_recon_mention_user ON recon_mention(mentioned_user_id);
