# Conquistas e Medalhas

Relacionado a [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]], [[04-MODULOS-E-FLUXOS]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[09-OPERACOES]], [[10-MINHA-EQUIPE]], [[11-RANKING]] e [[99-HISTORICO-DE-ALTERACOES]].

## Diagnóstico e limite atual

O repositório possui apenas frontend React/Vite. Não há banco, API, migrations, fila, autenticação real ou painel administrativo protegido. A entrega atual é uma experiência funcional em memória com 100 conquistas; concessões e segurança definitivas dependem do backend abaixo.

## Modelo de dados

- `achievement_category` e `achievement_rarity`: nome, descrição, ordem, tokens visuais, pontuação opcional, ativo.
- `achievement`: UUID público, escopo (`OPERATOR`, `TEAM`, `ORGANIZER`, `FIELD`), nome, descrições, tipo, imagem/ícone, visibilidade, repetível, ordem e ativo.
- `achievement_rule`, `achievement_rule_condition` e `achievement_rule_version`: métrica, operador, valor, agregação, janela, posição, versão imutável e vigência.
- `achievement_progress`: sujeito, regra, atual, alvo, percentual, última fonte, versão otimista e atualização.
- `operator_achievement` e `team_achievement`: sujeito, definição, status, data, regra, origem e relações opcionais com operação, campeonato, temporada e equipe.
- `achievement_grant` e `achievement_revocation`: tipo automático/manual, ator, motivo, fonte, correlação e histórico append-only.
- `achievement_notification`, `achievement_share` e `achievement_audit_log`: agrupamento, conteúdo público mínimo e trilha completa.

Constraints: uma concessão não repetível por conquista/sujeito; repetíveis incluem também evento gerador. FK para fontes; check de progresso não negativo; raridade/categoria/regra sempre versionadas; `version` para concorrência otimista.

## Migrations planejadas

1. `V015__achievement_taxonomy_and_catalog.sql` — categorias, raridades e 100 definições iniciais.
2. `V016__achievement_rules_and_versions.sql` — motor configurável e condições.
3. `V017__achievement_progress_and_grants.sql` — progresso, concessões e índices únicos.
4. `V018__achievement_revocations_notifications_and_shares.sql`.
5. `V019__achievement_audit_and_incremental_events.sql` — outbox, correlação e checkpoints.

Não foram criados arquivos SQL porque o projeto não contém backend ou Flyway. Criá-los no frontend daria falsa impressão de persistência.

## Motor de regras

Métricas permitidas vivem em catálogo administrativo, por exemplo `OPERATIONS_COMPLETED`, `CONFIRMED_ELIMINATIONS`, `OBJECTIVES_COMPLETED`, `MEDIC_ASSISTS`, `OPERATIONS_AS_COMMANDER`, `TEAM_WINS`, `REPUTATION_POSITIVE_VOTES`, `RANK_POSITION`, `TEAM_MEMBERSHIP_DAYS`, `CHAMPIONSHIPS_WON` e `DISTINCT_POSITIONS_PLAYED`.

Condições usam operadores `GTE`, `EQ`, `LTE`, `SEQUENCE`, `PERIOD`, `PERCENTAGE`, `DISTINCT_COUNT`, `ALL` e `ANY`. A regra é JSON validado contra schema, compilada pelo servidor e versionada. Novas conquistas são cadastradas pelo painel administrativo escolhendo taxonomia, métrica, operador, alvo e fontes; não exigem alteração ou deploy de código enquanto usarem métricas catalogadas. Métrica realmente nova exige implementação backend e revisão de segurança.

## Processamento incremental

Eventos aceitos: `OperationHomologated`, `PerformanceConfirmed`, `RankingSnapshotPublished`, `ChampionshipClosed`, `TeamMembershipChanged`, `ReputationAggregateUpdated` e `AchievementReprocessRequested`. Cada evento tem ID idempotente, versão e correlação. Consumidor atualiza somente regras dependentes da métrica alterada, grava progresso e concessão na mesma transação e publica notificação via outbox. Não recalcula todos os usuários no acesso.

Contestação suspende a contribuição. Rejeição posterior reprocessa a origem, coloca a conquista em análise e pode gerar revogação motivada sem apagar a concessão original. Fórmulas antigas continuam reproduzíveis.

## Endpoints planejados

- `GET /api/achievements` e `GET /api/operators/me/achievements` com filtros/paginação.
- `GET /api/operators/{publicId}/achievement-showcase` e `PUT /api/operators/me/achievement-showcase` com limite 3.
- `POST /api/operator-achievements/{id}/share-preview` retornando apenas dados públicos.
- Administração: `POST/PUT /api/admin/achievements`, raridades, categorias, regras, simulação, concessão, revogação, histórico e reprocessamento.
- Concessão/revogação exigem MFA recente, motivo, idempotency key e RBAC; nenhum endpoint aceita progresso calculado pelo cliente.

## Segurança e privacidade

IDs públicos opacos não concedem acesso. O servidor deriva ator e permissões da sessão, valida fontes e impede autoconcessão. Rate limit e auditoria cobrem simulação, concessão, revogação e reprocessamento. Compartilhamento limita-se a callsign, conquista, ícone, raridade, data e logo; exclui votos secretos, contestações e dados administrativos. Imagens administrativas seguem quarentena e política de upload.

## Catálogo e interface

`src/achievement-catalog.ts` contém exatamente as 100 definições solicitadas para demonstração. A tela conectada oferece busca, categoria, posição, temporada, status, raridade, progresso, segredo, detalhe e destaque. O perfil apresenta até três conquistas. Cores de raridade são protótipo; produção carregará tokens visuais sanitizados do banco com fallback acessível.

## Testes

`npm run test:achievements` cobre os 20 casos solicitados: primeira/quinta operação, eliminação confirmada/contestada, K/D, objetivo, comando, duplicidade médica, equipe/troca, reputação/votos anulados, campeonato, Top 10/queda, duplicidade, manual, revogação, nova versão e concorrência. Backend exigirá Testcontainers/PostgreSQL, autorização BOLA, outbox, rollback, carga e privacidade.
