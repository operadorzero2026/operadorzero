-- Operações médias e grandes publicadas antes da obrigatoriedade de esquadrões
-- precisam de um destino válido sem apagar ou mover participantes existentes.
INSERT INTO operation_squad(operation_team_id,name,acronym,description,capacity,sort_order,status,created_at,updated_at)
SELECT t.id,
       left('Esquadrão ' || t.name,80),
       left(t.acronym,12),
       'Esquadrão inicial criado para compatibilidade com operação publicada anteriormente.',
       t.capacity,
       1,
       t.status,
       now(),
       now()
FROM operation_team t
JOIN airsoft_operation o ON o.id=t.operation_id
WHERE o.game_size IN ('MEDIUM','LARGE')
  AND o.status <> 'DRAFT'
  AND o.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM operation_squad s WHERE s.operation_team_id=t.id);

-- Participantes antigos sem esquadrão passam ao esquadrão compatível do próprio time.
UPDATE operation_participant p
SET operation_squad_id=s.id,updated_at=now()
FROM operation_squad s
WHERE p.operation_team_id=s.operation_team_id
  AND p.operation_squad_id IS NULL
  AND s.sort_order=(SELECT min(s2.sort_order) FROM operation_squad s2 WHERE s2.operation_team_id=s.operation_team_id)
  AND s.id=(SELECT min(s3.id) FROM operation_squad s3 WHERE s3.operation_team_id=s.operation_team_id AND s3.sort_order=s.sort_order);
