-- Chamado por seed-feed.sh. SOMENTE desenvolvimento: conteudo ficticio marcado com [DEV].
BEGIN;

INSERT INTO app_user(email, username, status, email_verified_at, terms_version, terms_accepted_at, privacy_version, privacy_accepted_at)
VALUES ('dev.alfa@operadorzero.test', 'dev_alfa', 'ACTIVE', now(), 'dev', now(), 'dev', now()),
       ('dev.bravo@operadorzero.test', 'dev_bravo', 'ACTIVE', now(), 'dev', now(), 'dev', now()),
       ('dev.charlie@operadorzero.test', 'dev_charlie', 'ACTIVE', now(), 'dev', now(), 'dev', now())
ON CONFLICT DO NOTHING;

INSERT INTO operator_profile(user_id, callsign, display_name, city, state_code)
SELECT u.id, upper(replace(u.username, 'dev_', '')) || ' [DEV]', 'Operador de exemplo', 'Curitiba', 'PR'
FROM app_user u WHERE u.username IN ('dev_alfa', 'dev_bravo', 'dev_charlie')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO operator_xp(user_id, total_xp, rank_code)
SELECT u.id, x.xp, x.rank FROM app_user u
JOIN (VALUES ('dev_alfa', 2400, 'PRIMEIRO_SARGENTO'), ('dev_bravo', 350, 'CABO'), ('dev_charlie', 0, 'RECRUTA')) x(username, xp, rank)
  ON x.username = u.username
ON CONFLICT (user_id) DO NOTHING;

-- Remove posts de seed anteriores para o script poder rodar de novo.
DELETE FROM feed_post WHERE caption LIKE '[DEV]%';
DELETE FROM media_asset WHERE storage_prefix LIKE 'dev-seed/%';

CREATE TEMP TABLE seed_post(slot int, username text, kind text, w int, h int, ms int, prefix text, renditions jsonb,
                            poster text, blur text, caption text, tags text[], minutes_ago int, on_target int, eliminated int, comments int);
INSERT INTO seed_post VALUES
 (1, 'dev_alfa', 'VIDEO', 720, 1280, 8000, 'dev-seed/v1', '[{"type":"mp4","path":"720.mp4","height":1280}]', 'poster.webp', :'blur_v1',
  '[DEV] Entrada pelo galpão norte, time azul segurando a bandeira até o fim. #cqb #milsim', '{cqb,milsim}', 12, 42, 7, 5),
 (2, 'dev_bravo', 'VIDEO', 720, 1280, 8000, 'dev-seed/v2', '[{"type":"mp4","path":"720.mp4","height":1280}]', 'poster.webp', :'blur_v2',
  '[DEV] Teste do novo setup de mira. O que acharam? #setup', '{setup}', 95, 18, 2, 3),
 (3, 'dev_charlie', 'IMAGE', 1080, 1350, NULL, 'dev-seed/i1', '[{"type":"image","path":"540.webp","width":540},{"type":"image","path":"1080.webp","width":1080}]', NULL, :'blur_i1',
  '[DEV] Equipamento conferido para o domingo. Descrição longa para testar o "ver mais": checklist completo com óculos, máscara, rádio, baterias carregadas, água, protetor solar e a réplica regulada dentro do limite de FPS do campo. #arsenal #domingo', '{arsenal,domingo}', 300, 9, 0, 1),
 (4, 'dev_alfa', 'VIDEO', 1280, 720, 8000, 'dev-seed/v4', '[{"type":"mp4","path":"720.mp4","height":720}]', 'poster.webp', :'blur_v4',
  '[DEV] Clipe horizontal para testar o fundo desfocado. #debriefing', '{debriefing}', 600, 25, 11, 8),
 (5, 'dev_bravo', 'IMAGE', 1080, 1080, NULL, 'dev-seed/i2', '[{"type":"image","path":"540.webp","width":540},{"type":"image","path":"1080.webp","width":1080}]', NULL, :'blur_i2',
  '[DEV] Foto quadrada do briefing.', '{}', 1440, 4, 0, 0),
 (6, 'dev_charlie', 'VIDEO', 720, 1280, 8000, 'dev-seed/v3', '[{"type":"mp4","path":"720.mp4","height":1280}]', 'poster.webp', :'blur_v3',
  '[DEV] Último clipe da página para testar a rolagem infinita. #speedsoft', '{speedsoft}', 2880, 3, 1, 0);

CREATE TEMP TABLE seed_map AS
WITH assets AS (
    INSERT INTO media_asset(owner_user_id, purpose, kind, status, detected_type, original_bytes, width, height, duration_ms,
                            storage_prefix, renditions, poster_key, blur_placeholder, ready_at)
    SELECT u.id, 'POST', s.kind, 'READY', CASE s.kind WHEN 'VIDEO' THEN 'video/mp4' ELSE 'image/webp' END, 1000000,
           s.w, s.h, s.ms, s.prefix, s.renditions, s.poster, s.blur, now()
    FROM seed_post s JOIN app_user u ON u.username = s.username
    RETURNING id, storage_prefix
)
SELECT s.slot, a.id asset_id FROM assets a JOIN seed_post s ON s.prefix = a.storage_prefix;

WITH posts AS (
    INSERT INTO feed_post(author_user_id, caption, status, idempotency_key, created_at, published_at)
    SELECT u.id, s.caption, 'PUBLISHED', gen_random_uuid(), now() - make_interval(mins => s.minutes_ago), now() - make_interval(mins => s.minutes_ago)
    FROM seed_post s JOIN app_user u ON u.username = s.username
    ORDER BY s.slot
    RETURNING id, caption
)
INSERT INTO feed_post_media(post_id, media_asset_id, position)
SELECT p.id, m.asset_id, 0 FROM posts p JOIN seed_post s ON s.caption = p.caption JOIN seed_map m ON m.slot = s.slot;

INSERT INTO feed_post_hashtag(post_id, tag)
SELECT fp.id, t.tag FROM feed_post fp JOIN seed_post s ON s.caption = fp.caption CROSS JOIN LATERAL unnest(s.tags) t(tag);

INSERT INTO feed_post_metrics(post_id, on_target, eliminated, comments)
SELECT fp.id, s.on_target, s.eliminated, s.comments FROM feed_post fp JOIN seed_post s ON s.caption = fp.caption;

-- Recons de exemplo (24 h a partir de agora): dev_alfa com video e foto, dev_bravo com foto.
DELETE FROM recon WHERE overlay_text LIKE '[DEV]%';
DELETE FROM media_asset a WHERE a.purpose = 'RECON' AND a.storage_prefix LIKE 'dev-seed/%'
  AND NOT EXISTS (SELECT 1 FROM recon r WHERE r.media_asset_id = a.id);
WITH recon_media AS (
    INSERT INTO media_asset(owner_user_id, purpose, kind, status, original_bytes, width, height, duration_ms, storage_prefix, renditions, poster_key, ready_at)
    SELECT u.id, 'RECON', x.kind, 'READY', 1000000, x.w, x.h, x.ms, x.prefix, x.renditions::jsonb, x.poster, now()
    FROM (VALUES
        ('dev_alfa', 'VIDEO', 720, 1280, 8000, 'dev-seed/v2', '[{"type":"mp4","path":"720.mp4","height":1280}]', 'poster.webp', '[DEV] Chegando no campo', 90),
        ('dev_alfa', 'IMAGE', 1080, 1350, NULL, 'dev-seed/i1', '[{"type":"image","path":"1080.webp","width":1080}]', NULL, '[DEV] Briefing às 9h', 30),
        ('dev_bravo', 'IMAGE', 1080, 1080, NULL, 'dev-seed/i2', '[{"type":"image","path":"1080.webp","width":1080}]', NULL, '[DEV] Equipamento pronto', 200)
    ) x(username, kind, w, h, ms, prefix, renditions, poster, overlay, minutes_ago)
    JOIN app_user u ON u.username = x.username
    RETURNING id, owner_user_id, storage_prefix
)
INSERT INTO recon(author_user_id, media_asset_id, overlay_text, status, created_at, published_at, expires_at)
SELECT m.owner_user_id, m.id, x.overlay, 'ACTIVE', now() - make_interval(mins => x.minutes_ago), now() - make_interval(mins => x.minutes_ago),
       now() - make_interval(mins => x.minutes_ago) + interval '24 hours'
FROM recon_media m JOIN (VALUES ('dev-seed/v2', '[DEV] Chegando no campo', 90), ('dev-seed/i1', '[DEV] Briefing às 9h', 30),
                                ('dev-seed/i2', '[DEV] Equipamento pronto', 200)) x(prefix, overlay, minutes_ago) ON x.prefix = m.storage_prefix;

-- Todo usuario local que nao e de seed passa a seguir dev_alfa, para a aba "Seguindo" ter conteudo.
INSERT INTO operator_follow(follower_user_id, followed_user_id)
SELECT u.id, alfa.id FROM app_user u, app_user alfa
WHERE alfa.username = 'dev_alfa' AND u.username NOT LIKE 'dev\_%' AND u.status = 'ACTIVE'
ON CONFLICT DO NOTHING;

COMMIT;
