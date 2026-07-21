# Ranking Operador Zero

Relacionado a [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]], [[04-MODULOS-E-FLUXOS]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]] e [[99-HISTORICO-DE-ALTERACOES]].

## Diagnóstico em 2026-07-21

O projeto possui somente frontend React/Vite. Antes desta entrega, Ranking era uma tabela estática pública e indicadores no painel. Não existem API, banco, autenticação real, jobs ou migrations; portanto, a interface criada é protótipo transparente e não uma implementação segura/persistente.

Reuso disponível: operações e estados concluídos, identidade do operador, equipes, navegação autenticada e linguagem visual. Lacunas: homologação, elegibilidade, confirmações, contestações, catálogo de posições, fórmula, reputação secreta, snapshots e auditoria.

## Estados e prazos

Somente operação concluída, não cancelada e homologada gera ranking. Participante precisa ter inscrição, presença confirmada, aceite das regras, vínculo exigido e ausência de suspensão. Estados do registro: `PENDING`, `CONFIRMED`, `PARTIALLY_CONFIRMED`, `CONTESTED`, `CORRECTED`, `REJECTED`, `EXPIRED`, `UNDER_REVIEW`, `ORGANIZATION_VALIDATED`.

Padrões configuráveis: 7 dias para registro, confirmação e contestação; 15 dias para revisão da organização; 10 dias para reputação. Alterações futuras valem apenas para novos ciclos, salvo recalculação administrativa versionada.

## Modelo de dados proposto

- `position_definition` e `position_weight_version`: catálogo administrável, categoria, métricas válidas, vigência e pesos.
- `performance_record`, `performance_record_version` e `performance_position`: autor, operação, participação parcial, função primária/secundária e percentuais totalizando 100%.
- `performance_combat_event` e `performance_objective`: participantes referenciados, missão, rodada, horário aproximado, quantidade e estado.
- `performance_confirmation`, `performance_contest` e `performance_penalty`: decisão única por ator/evento, motivo, revisor e trilha.
- `secret_reputation_vote` e `reputation_aggregate`: voto cifrado/segregado, janela, amostra mínima e agregado sem exposição de autor.
- `ranking_formula_version`, `ranking_score_breakdown`, `ranking_snapshot` e `ranking_entry`: entrada normalizada, fórmula imutável, resultado reproduzível e publicação por escopo.
- `ranking_audit_log`: ator, ação, alvo público, antes/depois, motivo, data e correlação.

Todas as tabelas de domínio recebem UUID público, timestamps UTC, versionamento otimista e escopo organizacional quando aplicável. Nunca aceitar `user_id`, `team_id` ou permissões do body sem resolver no contexto autenticado.

Cada eliminação pode identificar o nick do adversário eliminado e cada morte pode identificar o nick de quem eliminou o usuário. O frontend pesquisa entre participantes confirmados da própria operação, mas o backend persiste o UUID público resolvido, não o texto livre. A quantidade de vínculos não pode superar o total declarado; registros sem identificação permanecem permitidos com peso inferior até confirmação, conforme a versão da fórmula.

## Migrações planejadas

1. `V010__ranking_positions_and_formula_versions.sql`.
2. `V011__performance_records_events_and_objectives.sql`.
3. `V012__confirmations_contests_and_penalties.sql`.
4. `V013__secret_reputation_votes_and_aggregates.sql`.
5. `V014__ranking_breakdowns_snapshots_and_audit.sql`.

Não foram criadas migrations nesta etapa porque ainda não há backend nem diretório Flyway.

## Contratos REST planejados

- `GET /api/rankings?scope=&location=&position=&modality=&season=&period=` e `GET /api/rankings/me`.
- `GET /api/operations/{operationId}/performance/me` e `POST/PUT` no mesmo recurso com chave de idempotência.
- `POST /api/performance-events/{eventId}/confirmations` e `POST /api/performance-events/{eventId}/contests`.
- `GET /api/ranking-entries/{entryId}/breakdown`.
- Administração: posições, fórmulas, homologação, correções, penalidades e recalculações protegidas por RBAC.

Respostas usam IDs públicos opacos, paginação, DTOs limitados e erros padronizados. O servidor verifica participante, operação, prazo, vínculo e autorização em cada mutação.

## Fórmula versão OZ-RANK 1.0

`Final = (Combate × Pc + Objetivos × Po + Função × Pf + Equipe × Pe + Confiabilidade × Pr) × Experiência - Penalidades`

Cada componente é normalizado de 0 a 100 por duração, participantes, rodadas, missões, modalidade, posição, tamanho de equipe e oportunidade de combate. Pesos somam 100% por posição. Exemplos: Comando 15/25/35/20/5; Assalto 45/25/10/15/5; Defesa 30/35/15/15/5. Médico, reconhecimento, suporte, sniper e demais posições têm versões próprias administráveis.

Experiência: 1–2 operações 40%; 3–5 60%; 6–10 80%; acima de 10, 100%. Evento contestado ou rejeitado vale zero até resolução. Morte zero exibe eliminações e “sem mortes registradas”, sem divisão por zero. Reputação é secreta, exige amostra mínima, tem influência limitada e filtros de conluio.

## Segurança e antifraude

- Autorização no servidor e consulta por escopo; proteção contra IDOR e escalação de privilégio.
- Constraint única para confirmação por ator/evento e idempotência nas mutações.
- Proibição de autovoto, autoconfirmação, posição inexistente e percentuais diferentes de 100%.
- Rate limit, detecção de padrões recíprocos/coordenados e revisão humana sem revelar votos secretos.
- Fórmulas e snapshots imutáveis; correção gera nova versão e auditoria, nunca edição silenciosa.
- Índices por operação, participante, estado, prazo, escopo, temporada e fórmula; filas/jobs idempotentes para cálculo.

## Testes

`npm run test:ranking` cobre 18 cenários: comando, assalto, defesa, médico, sniper, novato, veterano, contestação, coordenação, assédio, troca de equipe, posição falsa, operação longa/curta, ID adulterado, duplicidade, autovoto e penalidade contestada. Esses testes validam o motor demonstrativo; backend ainda exigirá testes unitários, integração PostgreSQL, concorrência, autorização, privacidade e carga.
