# Histórico de alterações

## 2026-07-30 - Lista compacta e regional de operações

### Arquivos alterados
- `src/OperationsPage.tsx`
- `src/styles.css`
- `scripts/stage-two-scenarios.mjs`
- `.vercelignore`
- [[06-FRONTEND-WEB]] e [[09-OPERACOES]]

### O que foi feito
- A listagem de operações foi simplificada para data, nome, cidade, UF e horário.
- A ordenação prioriza cidade do operador, depois estado e data/horário.
- O detalhe mantém capa e dados completos.
- A publicação Vercel ignora backend, documentação e artefatos locais que não pertencem ao bundle web.

### Motivo
- Melhorar leitura e navegação em computador e celular conforme a referência visual.

### Impacto
- Frontend; sem alteração de API, banco ou dados.

### Testes
- `npm run check`.

### Pendências
- Nenhuma.

## 2026-07-30 - Identificação de operadores pela sigla da equipe

### Arquivos alterados
- `src/operator-label.ts` e `src/api.ts`
- módulos backend `operator`, `operation` e `community`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `scripts/stage-two-scenarios.mjs`
- [[09-OPERACOES]], [[10-MINHA-EQUIPE]] e [[14-COMUNIDADE]]

### O que foi feito
- A API passou a devolver a sigla da equipe ativa em busca, inscritos, comunidade e chat de operações.
- O formatador compartilhado exibe `CALLSIGN - SIGLA` e usa `CALLSIGN - SEM TIME` sem equipe ativa.

### Motivo
- Padronizar a identificação pública solicitada sem expor nome civil nem depender de montagem diferente em cada tela.

### Impacto
- Backend, frontend e contratos de API; sem alteração de schema ou perda de dados.

### Testes
- `npm run check` e `mvn -B clean verify`.

### Pendências
- Nenhuma.

## 2026-07-30 - Correção do envio de mensagens no chat geral

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/operation/OperationStructureRepository.java`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- o parâmetro opcional da mensagem respondida passou a ser tipado como UUID no PostgreSQL;
- mensagens raiz agora são persistidas sem erro de tipo;
- proteção automatizada impede o retorno da consulta ambígua.

### Motivo
- o PostgreSQL não conseguia inferir o tipo de `parentMessageId` nulo e devolvia erro 500 ao enviar uma mensagem simples.

### Impacto
- backend do chat de operações, sem migration ou perda de dados.

### Testes
- executar `mvn -B clean verify`.

### Pendências
- nenhuma.

## 2026-07-30 - Chat geral aberto e com rolagem própria

### Arquivos alterados
- `src/OperationCommandCenter.tsx`
- `src/functional-modules.css`
- `scripts/stage-two-scenarios.mjs`
- [[06-FRONTEND-WEB]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- removido o botão intermediário para abrir a comunicação;
- chat geral passou a carregar aberto e antes da estrutura da operação;
- histórico recebeu altura responsiva e rolagem própria;
- organizador mantém o envio quando o canal possui bloqueio administrativo.

### Motivo
- tornar a comunicação imediatamente disponível sem esticar a página.

### Impacto
- frontend web de operações, sem alteração de banco ou contrato.

### Testes
- executar `npm run check`.

### Pendências
- nenhuma.

## 2026-07-30 - Correção da autorização do chat geral das operações

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/operation/OperationStructureService.java`
- `services/api/src/test/java/br/com/operadorzero/operation/OperationStructureServiceTest.java`
- [[05-SEGURANCA-E-PRIVACIDADE]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- o chat geral passou a aceitar leitura e envio por qualquer usuário autenticado com acesso à operação;
- canais privados continuam protegidos pela participação real no esquadrão;
- teste automatizado cobre usuário autenticado ainda não inscrito.

### Motivo
- a interface oferecia o chat geral a todos, mas o backend bloqueava usuários antes da inscrição.

### Impacto
- backend e autorização do chat de operações, sem alteração de banco.

### Testes
- executar `mvn -B clean verify` e `npm run check`.

### Pendências
- nenhuma.

## 2026-07-30 - Visualização pública com edição explícita para o organizador

### Arquivos alterados
- `src/OperationsPage.tsx`
- `src/OperationCommandCenter.tsx`
- `src/functional-modules.css`
- `scripts/stage-two-scenarios.mjs`
- [[06-FRONTEND-WEB]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- operações próprias agora abrem no mesmo modo de visualização dos participantes;
- o botão `Editar operação` no topo libera os controles administrativos existentes;
- salvar, encerrar a edição ou fechar o detalhe restaura o modo de visualização;
- a configuração de times e esquadrões de rascunhos respeita o mesmo modo explícito.

### Motivo
- separar a experiência de consulta da operação das ações administrativas do organizador.

### Impacto
- frontend web de operações, sem alteração de contrato ou banco.

### Testes
- cenário estático adicionado ao conjunto `test:stage-two`;
- executar `npm run check`.

### Pendências
- nenhuma.

## 2026-07-29 - Login resiliente após suspensão ou deploy do Render

### Arquivos alterados
- `src/api.ts`
- `scripts/auth-flow-scenarios.mjs`
- [[06-FRONTEND-WEB]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- O CSRF preparado no navegador passou a expirar em cinco minutos e ser renovado antes de novas ações de autenticação.
- O timeout exclusivo de login, cadastro, confirmação e recuperação passou para 45 segundos.

### Motivo
- Evitar falha prematura quando o formulário permanece aberto enquanto a instância gratuita adormece ou é substituída durante um deploy.

### Impacto
- Frontend de autenticação; sem alteração de senha, sessão, banco ou backend.

### Testes
- Cenários de autenticação, lint, build e validação do bundle/deploy.

### Pendências
- O primeiro preparo após suspensão completa ainda depende do tempo de retomada oferecido pelo plano gratuito do Render.

## 2026-07-29 - Remarcação, briefing e exclusão segura de operações

### Arquivos alterados
- `src/OperationsPage.tsx`, `src/api.ts`, `src/functional-modules.css`
- `services/api/src/main/java/br/com/operadorzero/operation/*`
- `services/api/src/main/java/br/com/operadorzero/performance/PerformanceRepository.java`
- `services/api/src/main/resources/db/migration/V19__operation_briefing_and_soft_delete.sql`
- `services/api/src/test/java/br/com/operadorzero/operation/OperationServiceTest.java`
- `scripts/real-content-scenarios.mjs`
- [[06-FRONTEND-WEB]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Adicionada edição persistente de data, horários e briefing pelo organizador, com motivo, validação no servidor e controle otimista de versão.
- Adicionada exclusão lógica autorizada, com confirmação visual, motivo obrigatório e preservação de registros relacionados e auditoria.
- Operações excluídas foram retiradas das consultas e dos acessos diretos aos sub-recursos.

### Motivo
- Permitir que o organizador corrija ou remarque o jogo e remova uma operação sem perda silenciosa de histórico.

### Impacto
- Frontend, backend, PostgreSQL, autorização por objeto e auditoria de Operações.

### Testes
- Testes unitários de horários inválidos, conflito otimista e exclusão lógica.
- Build frontend, cenários de regressão e suíte Maven.

### Pendências
- Aplicar a migration e publicar frontend/backend apenas mediante solicitação de deploy.

## 2026-07-29 - Contraste dos botões de times e esquadrões

### Arquivos alterados
- `src/functional-modules.css`
- `scripts/real-content-scenarios.mjs`
- [[06-FRONTEND-WEB]]

### O que foi feito
- definido texto escuro sobre fundo oliva para **Esquadrão** e **Criar time**;
- adicionados estados legíveis de hover e carregamento;
- ampliado o botão de criação no layout móvel.

### Motivo
- o texto herdava a mesma tonalidade clara do fundo do botão e ficava ilegível.

### Impacto
- frontend responsivo do gerenciamento de operações.

### Testes
- cenário automatizado de contraste, lint e build do frontend.

### Pendências
- nenhuma.

## 2026-07-29 - Correção do carregamento e inscrição em operação publicada

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/operation/OperationStructureRepository.java`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `src/OperationsPage.tsx`
- `scripts/real-content-scenarios.mjs`
- [[06-FRONTEND-WEB]]
- [[09-OPERACOES]]

### O que foi feito
- incluídas no `GROUP BY` as colunas de ordenação das consultas de times e esquadrões;
- separados os resultados de participantes e estrutura no frontend;
- adicionada mensagem visível com nova tentativa em falhas parciais.

### Motivo
- o PostgreSQL rejeitava a consulta da estrutura e a tela mantinha os dois blocos em carregamento.

### Impacto
- backend, frontend e fluxo de inscrição em operações publicadas.

### Testes
- teste de regressão da consulta e cenário de resiliência do detalhe; suítes completas executadas antes da publicação.

### Pendências
- nenhuma para esta falha.

## 2026-07-29 - Redução da latência ao abrir operações

### Arquivos alterados
- `src/OperationCommandCenter.tsx`
- `src/OperationsPage.tsx`
- `src/functional-modules.css`
- [[06-FRONTEND-WEB]]

### O que foi feito
- removida consulta duplicada da estrutura;
- chat carregado sob demanda;
- polling alterado de 5 para 15 segundos e ativo somente com o chat aberto.

### Motivo
- evitar quatro requisições simultâneas e pressão desnecessária no pool do Render Free.

### Impacto
- abertura mais rápida do detalhe e menor uso de PostgreSQL e Redis.

### Testes
- `npm run check` e verificação em produção após deploy.

### Pendências
- acompanhar cold start inerente ao plano gratuito após períodos de inatividade.

## 2026-07-29 - Estrutura completa e comunicação de Operações

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V18__operation_structure_roles_and_chat.sql`
- `services/api/src/main/java/br/com/operadorzero/operation/*`
- `services/api/src/test/java/br/com/operadorzero/operation/*`
- `src/api.ts`, `src/OperationsPage.tsx`, `src/OperationCommandCenter.tsx`, `src/functional-modules.css`
- notas [[04-MODULOS-E-FLUXOS]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]] e [[09-OPERACOES]]

### O que foi feito
- tamanhos pequeno, médio e grande, times, esquadrões e funções locais;
- inscrição e movimentação com bloqueio transacional;
- chat geral e por time com persistência, idempotência, moderação, denúncias, paginação, polling e rate limit;
- prévia, fallback e remoção de capa.

### Motivo
- permitir organização operacional real sem estados somente visuais.

### Impacto
- frontend, backend, PostgreSQL e Redis.

### Testes
- build React e testes Maven executados; Testcontainers indisponível porque Docker não está instalado nesta estação.

### Pendências
- aplicar a V18 primeiro em staging com backup e validar o fluxo real antes de produção.

## 2026-07-29 - Diagnóstico e correção da latência de login

### Arquivos alterados
- `src/api.ts`
- `src/App.tsx`
- `scripts/auth-flow-scenarios.mjs`
- `services/api/src/main/java/br/com/operadorzero/identity/AuthRateLimiter.java`
- `services/api/src/main/java/br/com/operadorzero/identity/AuthService.java`
- `services/api/src/test/java/br/com/operadorzero/identity/AuthRateLimiterTest.java`
- `AUTHENTICATION.md`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/20-DIAGNOSTICO-DE-LATENCIA-DO-LOGIN-2026-07-29.md`

### O que foi feito
- Medida a latência direta, via proxy e no navegador oficial.
- Antecipado e deduplicado o bootstrap CSRF ao abrir o formulário.
- Consolidado o rate limit IP/sujeito em um script Redis atômico.
- Adicionadas métricas sanitizadas do clique até a tela utilizável e das fases internas do backend.
- Mantidos Argon2id, CSRF, cookie HttpOnly, confirmação de e-mail e rate limit.

### Motivo
- Separar cold start, proxy e processamento real e reduzir trabalho serial no caminho crítico do login.

### Impacto
- Frontend, backend, Redis, observabilidade e documentação; sem migration ou alteração de schema.

### Testes
- `npm run check` aprovado.
- `mvn -B clean verify` aprovado com 65 testes.
- Smoke remoto aquecido e tentativa inválida não destrutiva no navegador oficial.

### Pendências
- A coleta aquecida pós-deploy foi concluída. Ainda falta uma janela controlada após hibernação real e um login bem-sucedido com conta de homologação confirmada.
- Publicado no commit `4217e65`; Vercel `dpl_HoPipi7bP8ZtpA4fbPJnZt4txsKy`; Render `Live` após 2m44s.

## 2026-07-28 - Organizador pode participar da própria operação

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/operation/OperationRepository.java`
- `src/OperationsPage.tsx`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/09-OPERACOES.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Removido o bloqueio que impedia o organizador de se inscrever.
- Liberada a escolha de time no detalhe da própria operação publicada.
- Definida aprovação automática apenas para o organizador da operação.

### Motivo
- Permitir que quem organiza também participe como jogador no mesmo evento.

### Impacto
- Backend, frontend e documentação.

### Testes
- Build, testes automatizados e deploy de produção.

### Pendências
- Nenhuma.

## 2026-07-28 - Imagem de capa persistente para operações

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V15__operation_cover_images.sql`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationController.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationDtos.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationRepository.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationService.java`
- `services/api/src/test/java/br/com/operadorzero/operation/OperationServiceTest.java`
- `src/api.ts`
- `src/OperationsPage.tsx`
- `src/styles.css`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/09-OPERACOES.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Incluído upload opcional de capa no cadastro da operação.
- Criado armazenamento persistente com versão para invalidação de cache.
- Incluídos endpoint protegido de gravação e endpoint de leitura com visibilidade por objeto.
- Exibida a capa nos cartões e no detalhe responsivo da operação.

### Motivo
- Permitir que organizadores identifiquem visualmente suas operações e que operadores reconheçam o evento na agenda.

### Impacto
- Backend, frontend, banco PostgreSQL e documentação.

### Testes
- `npm run check`.
- `mvn test`.
- Migração e health check de produção devem ser confirmados após o deploy.

### Pendências
- Nenhuma pendência de código para o upload inicial de capa.

## 2026-07-28 - Participantes e escolha de time nas operações

### Arquivos alterados
- `src/OperationsPage.tsx`, `src/api.ts`, `src/styles.css`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationController.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationService.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationRepository.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationDtos.java`
- `services/api/src/main/resources/db/migration/V14__operation_teams_and_participant_roster.sql`
- `services/api/src/test/java/br/com/operadorzero/operation/OperationServiceTest.java`
- [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Detalhe clicável com lista de participantes agrupada por time.
- Escolha obrigatória de time na inscrição, com capacidade e lista de espera validadas no backend.
- Criação automática de times e migração segura das inscrições existentes.

### Motivo
- Permitir que jogadores consultem a composição e escolham o time desejado antes de entrar.

### Impacto
- Frontend, backend e banco de dados; migration aditiva com preservação de inscrições.

### Testes
- `npm run check` e `mvn test`, incluindo propagação do time escolhido.

### Pendências
- Renomear e reorganizar times pelo organizador permanece para uma evolução posterior.

## 2026-07-28 - Publicação de operações

### Arquivos alterados
- `src/OperationsPage.tsx`, `src/api.ts`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationController.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationService.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationRepository.java`
- `services/api/src/main/java/br/com/operadorzero/operation/OperationDtos.java`
- `services/api/src/test/java/br/com/operadorzero/operation/OperationServiceTest.java`
- [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Adicionadas as opções de salvar rascunho e publicar imediatamente.
- Rascunhos próprios passam a aparecer ao organizador com publicação posterior.
- Publicação move atomicamente o rascunho para inscrições abertas e libera participação de outros operadores.

### Motivo
- Completar o fluxo real de organização, que anteriormente encerrava apenas no rascunho.

### Impacto
- Frontend e backend; sem migration e sem alteração destrutiva de dados.

### Testes
- `npm run check` e `mvn test`, incluindo bloqueio de publicação fora de um rascunho próprio.

### Pendências
- Edição detalhada de rascunhos permanece fora deste incremento.

## 2026-07-28 - Tempo de atividade no airsoft e equipe na busca

### Arquivos alterados
- `src/OperatorPage.tsx`, `src/OperatorSearch.tsx`, `src/api.ts`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorDtos.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorRepository.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorService.java`
- `services/api/src/main/resources/db/migration/V13__operator_airsoft_start_date.sql`
- `services/api/src/test/java/br/com/operadorzero/operator/OperatorServiceTest.java`
- [[06-FRONTEND-WEB]], [[10-MINHA-EQUIPE]], [[13-MEU-OPERADOR]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Adicionado cadastro da data de início no airsoft e resumo calculado em anos e meses.
- Busca enriquecida com callsign, nome da equipe ativa e tempo no esporte.
- Tratamento explícito para operador sem equipe ou sem data informada.

### Motivo
- Apresentar experiência e vínculo atual do operador diretamente nos resultados de busca.

### Impacto
- Frontend, backend e banco de dados; migration aditiva e campo opcional.

### Testes
- `npm run check` e `mvn test`, incluindo rejeição de data futura.

### Pendências
- Nenhuma para o escopo solicitado.

## 2026-07-28 - Foto de perfil do operador

### Arquivos alterados
- `src/OperatorPage.tsx`, `src/api.ts`, `src/styles.css`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorController.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorService.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorRepository.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorDtos.java`
- `services/api/src/main/java/br/com/operadorzero/shared/image/SafeRasterImageProcessor.java`
- `services/api/src/main/resources/db/migration/V12__operator_profile_photos.sql`
- `services/api/src/test/java/br/com/operadorzero/shared/image/SafeRasterImageProcessorTest.java`
- `FILE-UPLOAD-POLICY.md`, [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[13-MEU-OPERADOR]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Cadastro e substituição da foto no Meu Operador, com atualização sem cache antigo.
- Upload e leitura autenticados, persistência PostgreSQL separada, auditoria e incremento de versão do perfil.
- Validação do conteúdo real, limite de 2 MB/2048 px e reprocessamento de PNG/JPEG.

### Motivo
- Permitir identidade visual persistente do operador sem usar o disco efêmero do Render.

### Impacto
- Frontend, backend e banco de dados; sem alteração nos demais cadastros.

### Testes
- `npm run check` e `mvn test`.

### Pendências
- Migrar a mídia para object storage privado quando o serviço estiver disponível.

## 2026-07-28 - Upload seguro de logo da equipe

### Arquivos alterados
- `src/TeamPage.tsx`
- `src/api.ts`
- `src/functional-modules.css`
- `services/api/src/main/java/br/com/operadorzero/team/TeamController.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamService.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamRepository.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamDtos.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/db/migration/V11__team_logos.sql`
- `services/api/src/test/java/br/com/operadorzero/team/TeamServiceTest.java`
- `FILE-UPLOAD-POLICY.md`
- [[05-SEGURANCA-E-PRIVACIDADE]]
- [[06-FRONTEND-WEB]]
- [[10-MINHA-EQUIPE]]
- [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Adicionada logo opcional na criação da equipe e substituição por capitão/gestor.
- Implementados upload multipart, leitura autenticada e persistência binária em tabela separada.
- PNG/JPEG são identificados pelo conteúdo, limitados a 2 MB e 2048 × 2048, decodificados e reencodados antes da persistência.
- Metadados, nome original e conteúdo adicional não são preservados; formatos ativos ou não suportados são recusados.

### Motivo
- Permitir identidade visual real para as equipes sem usar o disco efêmero do Render nem aceitar arquivos não validados.

### Impacto
- Frontend, backend, banco, segurança e documentação.

### Testes
- `npm run check`: lint, testes de autenticação, conteúdo real, SEO e etapa 2, build e validações de bundle/deploy aprovados.
- `mvn test` em `services/api`: 57 testes aprovados, incluindo PNG real reprocessado e rejeição de arquivo falso.
- Render confirmou 11 migrations válidas e aplicou `V11__team_logos.sql`; commit `01c2b02` ficou `live` e readiness respondeu `UP`.
- Frontend publicado no deploy Vercel `dpl_EXXs7puQE5Ud9A79ovuUKGsu4AHS`, associado a `https://operadorzero.com.br`.

### Pendências
- Homologar upload e substituição visual com uma conta real; nenhum arquivo artificial foi persistido em produção.
- Migrar os binários para object storage privado quando a infraestrutura estiver disponível.

## 2026-07-28 - Siglas de equipe com pontos e erro de campo claro

### Arquivos alterados
- `src/TeamPage.tsx`
- `src/api.ts`
- `services/api/src/main/java/br/com/operadorzero/team/TeamDtos.java`
- `services/api/src/test/java/br/com/operadorzero/team/TeamDtosTest.java`
- [[10-MINHA-EQUIPE]]
- [[06-FRONTEND-WEB]]
- [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- Permitidos pontos em siglas de equipe no frontend e no backend, mantendo o limite de 2 a 12 caracteres e a exigência de pelo menos uma letra ou número.
- A interface passou a apresentar o campo e a regra específica quando a API retorna erro de validação.
- Adicionados testes para aceitar `A.T.A.C.` e rejeitar siglas formadas somente por pontuação.

### Motivo
- O cadastro apresentado pelo usuário era rejeitado pela sigla `A.T.A.C.`, mas exibia apenas a mensagem genérica “Revise os campos informados”.

### Impacto
- Frontend, backend e documentação; nenhuma migration é necessária porque a coluna existente já comporta 12 caracteres.

### Testes
- `npm run check`: lint, testes de autenticação, conteúdo real, SEO e etapa 2, build e validações de bundle/deploy aprovados.
- `mvn test` em `services/api`: 55 testes aprovados, incluindo aceitação de `A.T.A.C.` e rejeição de sigla somente com pontuação.
- Backend Render confirmado `live` no commit `b3fc12d`; readiness oficial respondeu `UP`.
- Frontend Vercel publicado no deploy `dpl_7YReY7M7kFdFSiZPoZBCVz98czxu` e associado a `https://operadorzero.com.br`.

### Pendências
- Repetir o cadastro com a conta real e os dados apresentados pelo usuário; nenhum cadastro artificial foi criado em produção.

## 2026-07-28 - Correção de acesso negado ao criar equipe

### Arquivos alterados
- `src/api.ts`
- `scripts/auth-flow-scenarios.mjs`
- [[05-SEGURANCA-E-PRIVACIDADE]]
- [[06-FRONTEND-WEB]]
- [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- A camada HTTP passou a renovar o token CSRF e repetir uma mutação somente uma vez quando recebe `403 ACCESS_DENIED`.
- Adicionado contrato automatizado para impedir a remoção acidental dessa recuperação.

### Motivo
- O token mantido em memória podia divergir do cookie CSRF renovado por outra aba ou ciclo de autenticação, fazendo o Spring negar a criação antes de chegar ao controller de equipes.

### Impacto
- Frontend e segurança das mutações autenticadas; regras de autorização, backend e banco permanecem inalterados.

### Testes
- `npm run check`: lint, testes de autenticação, conteúdo real, SEO e etapa 2, build e validações de bundle/deploy aprovados.
- `mvn test` em `services/api`: 53 testes aprovados, inclusive regras de autorização de equipes.
- Versão publicada carregada com sucesso no domínio oficial.
- Deploy Vercel de produção `dpl_DzmiEmumHiWF5PaHUfyRNm9dXALk`.

### Pendências
- Repetir a criação com a conta real que apresentou o erro; o navegador de homologação não possui sessão autenticada e nenhum usuário/equipe artificial foi criado em produção.

## 2026-07-28 - Login como ação principal da página inicial

### Arquivos alterados
- `src/App.tsx`
- `scripts/real-content-scenarios.mjs`
- [[06-FRONTEND-WEB]]
- [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- `Entrar` passou a ser o botão principal no cabeçalho, menu móvel e hero.
- `Criar perfil gratuito` passou a ser a segunda opção, com apresentação visual secundária.
- Adicionado teste de regressão para preservar a ordem e a hierarquia das ações na primeira dobra.

### Motivo
- Priorizar o retorno de usuários que já possuem conta sem remover o acesso ao cadastro.

### Impacto
- Frontend público e documentação; autenticação, backend e banco não foram alterados.

### Testes
- `npm run check`: lint, testes de autenticação, conteúdo real, SEO e etapa 2, build e validações de bundle/deploy aprovados.
- Homologação no navegador da versão publicada: ordem das ações confirmada e modais de login e cadastro abertos corretamente.
- Deploy Vercel de produção `dpl_AxPL9BWWHXMUDpKKnjQRmmniqjEP`, publicado em `https://operadorzero.com.br`.

### Pendências
- Nenhuma pendência identificada para esta alteração.

## 2026-07-28 - Localização GPS e endereço opcional nos campos

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V10__field_optional_address_and_gps_link.sql`
- `services/api/src/main/java/br/com/operadorzero/venue/VenueDtos.java`
- `services/api/src/main/java/br/com/operadorzero/venue/VenueRepository.java`
- `services/api/src/test/java/br/com/operadorzero/venue/VenueDtosTest.java`
- `src/VenuesPage.tsx`, `src/api.ts`, `src/functional-modules.css`
- [[09-OPERACOES]] e [[99-HISTORICO-DE-ALTERACOES]]

### O que foi feito
- O endereço textual deixou de ser obrigatório no cadastro de campo.
- Adicionado link opcional de localização GPS, persistido e exibido como “Abrir localização”.
- Permitidos somente links HTTPS de Google Maps, Waze, Apple Maps e OpenStreetMap.
- O link externo usa nova aba com proteção `noopener noreferrer`.

### Motivo
- Permitir que o responsável informe a localização precisa mesmo quando não houver endereço postal aplicável.

### Impacto
- Frontend, backend, banco PostgreSQL e documentação.

### Testes
- Validação automatizada de endereço ausente, link Google Maps válido e URLs inseguras.
- `npm run check`: aprovado.
- `mvn -B clean verify`: 53 testes aprovados, zero falhas.
- Render: migration `V10` aplicada e serviço `5a24224` em estado live.
- Vercel: deployment `dpl_A7P7FPmHdyeY6XkdeYHryoKKgFPV` em estado `READY` e associado ao domínio oficial.
- Navegador real: formulário exibiu endereço opcional e campo de link GPS com exemplo do Google Maps.

### Pendências
- Nenhuma pendência de código identificada para esta alteração.

## 2026-07-28 - Injeção dos módulos funcionais em produção

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/operation/OperationService.java`
- `services/api/src/main/java/br/com/operadorzero/operator/OperatorService.java`
- `services/api/src/main/java/br/com/operadorzero/performance/PerformanceService.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamService.java`
- `services/api/src/main/java/br/com/operadorzero/venue/VenueService.java`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Marcados explicitamente os construtores de produção dos services que também possuem construtor alternativo para relógio de teste.
- Adicionado teste de regressão que exige exatamente um ponto de injeção em cada service afetado.
- Criada `AUTH_HASH_KEY` aleatória somente no ambiente Render, sem registrar ou expor o valor.
- Confirmada no log real a aplicação transacional das migrations `V5` a `V9` no PostgreSQL.

### Motivo
- O Spring não selecionava automaticamente o construtor quando a classe possuía dois construtores, impedindo a inicialização após as migrations.

### Impacto
- Inicialização do backend e ambiente Render. A chave nova invalida sessões e links de autenticação emitidos anteriormente; usuários e dados permanecem preservados.

### Testes
- `mvn -B clean verify`: 51 testes aprovados, zero falhas.
- Flyway: nove migrations validadas e schema atualizado de `V4` para `V9` com sucesso.
- Readiness direto no Render e pelo proxy oficial: HTTP 200.
- Navegador real: sessão persistida após recarga e abas Operações, Campos, Minha Equipe, Desempenho e Rankings carregadas sem erro interno.
- Deployment Vercel `dpl_2Ji4SKSYppBqA6ANK7KZ5g6QrsXT`: `READY` e associado a `https://operadorzero.com.br`.

### Pendências
- PostgreSQL Free expira em 2026-08-20 e não possui PITR/exportação automática; migrar ou atualizar o plano antes dessa data.
- Homologar cadastro de um novo usuário com endereço e conta Google que nunca tenham sido usados no sistema; os testes atuais reutilizaram uma conta real já autenticada.

## 2026-07-28 - Correção da validação dentro da imagem Docker

### Arquivos alterados
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `scripts/validate-deployment-config.mjs`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Removida do teste interno da API a dependência de `../../render.yaml`, arquivo que não pertence ao contexto Docker de `services/api`.
- Mantida a validação de `AUTH_HASH_KEY` e da política `noeviction` no validador de infraestrutura executado a partir da raiz.

### Motivo
- A aplicação e os 50 testes Java passavam, mas a repetição dos testes dentro da imagem falhava ao tentar acessar um arquivo fora do contexto Docker.

### Impacto
- CI e empacotamento da API; nenhuma regra de negócio, migration, dado ou segredo foi alterado.

### Testes
- `mvn -B clean verify`: 50 testes aprovados.
- `npm run validate:deploy`: aprovado.

### Pendências
- Aguardar o novo CI e o deploy real para registrar a homologação externa.

## 2026-07-28 - Reenvio da confirmação e diagnóstico de entregabilidade

### Arquivos alterados
- `src/App.tsx`
- `scripts/auth-flow-scenarios.mjs`
- `AUTHENTICATION.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionado reenvio neutro da confirmação após o cadastro e orientação para consultar Lixo Eletrônico.
- Confirmado no Resend que a mensagem recente foi aceita pelo servidor destinatário.

### Motivo
- A API já possuía reenvio seguro, mas a interface não o expunha; o domínio também não possui DMARC e o provedor aponta risco de entregabilidade.

### Impacto
- Frontend e documentação; sem alteração de token, banco, chave ou regra anti-enumeração.

### Testes
- Suíte frontend, build e contrato estático de reenvio.

### Pendências
- O TXT `_dmarc.mail` com política inicial `p=none` foi publicado e validado nos servidores autoritativos e no Google Public DNS.
- Acompanhar relatórios e entregabilidade antes de evoluir DMARC para `quarantine` ou `reject`.

## 2026-07-28 - Recuperação da tela após cancelar o Google

### Arquivos alterados
- `src/App.tsx`
- `scripts/auth-flow-scenarios.mjs`
- `AUTHENTICATION.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- O botão agora exibe o estado real de conexão durante o cold start da API.
- O retorno pelo botão Voltar ou pelo cancelamento do Google limpa o estado pendente restaurado pelo cache do navegador.

### Motivo
- A versão publicada desabilitava o formulário sem alterar o texto e podia restaurar esse estado indefinidamente via bfcache, aparentando congelamento.

### Impacto
- Frontend de autenticação; sem alteração de sessão, token, banco ou segredo.

### Testes
- Regressão estática do fluxo Google, suíte frontend, build e reprodução no domínio oficial.

### Pendências
- Nenhuma pendência de código identificada para este defeito.
## 2026-07-28 - Registro, confirmação e contestação de desempenho

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/performance/*`
- `services/api/src/test/java/br/com/operadorzero/performance/PerformanceServiceTest.java`
- `services/api/src/main/java/br/com/operadorzero/ranking/RankingRepository.java`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `src/PerformancePage.tsx`, `src/api.ts`, `src/App.tsx`, `src/functional-modules.css`
- [[11-RANKING]] e [[19-EXPANSAO-FUNCIONAL-OPERADORZERO-2026-07-28]]

### O que foi feito
- Implementado registro de desempenho para participantes confirmados de operações finalizadas.
- Implementadas confirmação do organizador, confirmação da equipe, correção, rejeição e contestação.
- Adicionada equipe representada e controle de versão otimista.
- Registros contestados passaram a ficar suspensos do ranking até decisão do organizador.
- Criada a aba Desempenho dentro da navegação existente.

### Motivo
- Alimentar o ranking oficial com registros reais, revisáveis e auditáveis.

### Impacto
- Frontend, backend e documentação. Usa as tabelas da migration `V9`; nenhum deploy ou migration remota.

### Testes
- `npm run check`: aprovado.
- `mvn -B clean verify`: 50 testes aprovados.
- Testes adicionais cobrem bloqueio de registro sem presença confirmada e contestação por usuário alheio à operação.

### Pendências
- Validar `V8` e `V9` em PostgreSQL real após backup/preflight.
- Homologar o fluxo completo no navegador com contas distintas de operador, organizador e liderança de equipe.
- Substituir diálogos nativos de correção por modal dedicado na etapa de refinamento visual.

## 2026-07-28 - Nova fórmula oficial de ranking e criação aberta

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V9__performance_records_and_ranking.sql`
- `services/api/src/main/java/br/com/operadorzero/ranking/*`
- `services/api/src/test/java/br/com/operadorzero/ranking/RankingCalculatorTest.java`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `src/RankingsPage.tsx`, `src/api.ts`, `src/App.tsx`, `src/functional-modules.css`
- [[11-RANKING]] e [[19-EXPANSAO-FUNCIONAL-OPERADORZERO-2026-07-28]]

### O que foi feito
- Tornada oficial a fórmula solicitada em 2026-07-28, substituindo OZ-RANK 1.0.
- Definida pontuação final como `(bruta + bônus - penalidades) × fator`, com bônus calculado apenas sobre registros confirmados por cada responsável.
- Criadas tabelas versionadas de desempenho e contestação.
- Implementado ranking real de operadores com filtros, detalhamento dos componentes e estado vazio.
- Confirmado que qualquer usuário autenticado pode criar equipe, campo e operação; a administração posterior continua protegida pelo vínculo com o objeto.

### Motivo
- Aplicar as decisões explícitas de produto sem misturar fórmulas ou criar bloqueios de criação não solicitados.

### Impacto
- Backend, frontend, banco PostgreSQL e documentação. Nenhum deploy, commit ou aplicação remota de migration.

### Testes
- `npm run check`: aprovado.
- `mvn -B clean verify`: 48 testes aprovados, incluindo três cenários específicos da fórmula.

### Pendências
- Aplicar `V8` e `V9` somente após backup/preflight em PostgreSQL real.
- Implementar o fluxo completo de registro, confirmação, correção e contestação que alimenta o ranking.
- Persistir snapshots periódicos para calcular variação histórica de posição; até lá a variação retornada é zero.

## 2026-07-28 - Base persistente de campos, mapas e operações

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V8__fields_maps_and_operations.sql`
- `services/api/src/main/java/br/com/operadorzero/operation/*`
- `services/api/src/main/java/br/com/operadorzero/venue/*`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `src/api.ts`, `src/App.tsx`, `src/OperationsPage.tsx`, `src/VenuesPage.tsx`, `src/functional-modules.css`
- [[00-LEIA-ANTES-CODEX]], [[09-OPERACOES]] e [[19-EXPANSAO-FUNCIONAL-OPERADORZERO-2026-07-28]]

### O que foi feito
- Auditadas a produção pública, as abas autenticadas do código, a documentação, migrations e contratos existentes.
- Criados modelos persistentes para campos, mapas, operações e participantes.
- Adicionadas busca, criação e visualização de campos/mapas/operações e solicitação/cancelamento de participação.
- Ampliado o menu existente com Campos, Mapas, Operadores e Notificações, sem criar uma segunda navegação ou plataforma.
- Mantidos uploads bloqueados e valores de inscrição estritamente informativos.

### Motivo
- Iniciar a expansão ampla solicitada sobre dados reais, autorização no backend e auditoria, evitando telas preenchidas com conteúdo fictício.

### Impacto
- Frontend, backend, banco PostgreSQL e documentação. Nenhum deploy, commit, push ou alteração de segredo.

### Testes
- Baseline: `npm run check` aprovado; `mvn -B clean verify` aprovado com 45 testes.
- Após a alteração: `npm run check` aprovado; `mvn -B clean verify` aprovado com 45 testes.

### Pendências
- Aplicar `V8` somente após backup/preflight e validar a migration em PostgreSQL real.
- Homologar o fluxo autenticado completo em navegador com uma conta de teste.
- Concluir as etapas restantes detalhadas em [[19-EXPANSAO-FUNCIONAL-OPERADORZERO-2026-07-28]].
- Implementar storage seguro antes de fotos, capas e plantas.

## 2026-07-27 - Auditoria de telas e remoção de mensagens técnicas

### Arquivos alterados
- `src/App.tsx`
- `src/api.ts`
- `scripts/real-content-scenarios.mjs`
- `scripts/auth-flow-scenarios.mjs`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Mapeadas as rotas públicas, nove destinos autenticados, protótipos desconectados, formulários, botões, endpoints existentes/ausentes e migrations necessárias.
- Removidas da interface oficial mensagens sobre dados reais/fictícios, demonstração, API, backend, persistência, produção, sessão técnica e armazenamento de credenciais.
- Reescritos landing, painel e estados vazios com linguagem natural, curta e orientada à jornada do operador.
- Adicionados testes de regressão para impedir a volta dessas mensagens ao bundle oficial.

### Motivo
- Separar comunicação de produto de detalhes internos e preparar a funcionalização progressiva dos módulos.

### Impacto
- Frontend e documentação. Backend, banco e infraestrutura não foram alterados.

### Testes
- Baseline anterior: `npm run check` aprovado e `mvn -B clean verify` aprovado com 40 testes.
- Pós-alteração: `npm run check` e busca global por mensagens técnicas.

### Pendências
- Implementar [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]] a partir da Etapa 2, começando por Meu Operador, Equipes, Convites e busca segura.
- Remover código morto somente depois que as novas superfícies funcionais substituírem todas as referências necessárias.
- Nenhum deploy, push ou merge foi realizado.

## 2026-07-27 - Cadastro Google tolerante ao cold start real

### Arquivos alterados
- `src/api.ts`
- `scripts/auth-flow-scenarios.mjs`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/17-DIAGNOSTICO-E-PLANO-DE-PRODUCAO-2026-07-27.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Confirmado nos logs que o Render Free levou cerca de 115 segundos para iniciar, acima do limite anterior de 45 segundos.
- Adicionada espera de readiness com tentativas limitadas por até três minutos antes de CSRF e intent Google.
- Publicado o frontend diretamente na Vercel; deployment `dpl_HoRvhgGQnpD1mMauGiPLRwVK3Df1` ficou `READY` com todos os aliases oficiais.

### Motivo
- Impedir que o cadastro Google seja encerrado pelo navegador enquanto a API gratuita ainda está despertando.

### Impacto
- Frontend oficial e Vercel. Nenhum backend, banco, segredo, migration ou configuração Render foi alterado.

### Testes
- `npm run check` aprovado.
- Bundle oficial confirmado com `/actuator/health/readiness`.
- `https://operadorzero.com.br/` e readiness da API responderam `200`.
- Logs do backend sem falha OIDC correspondente; causa confirmada como timeout anterior ao redirecionamento.

### Pendências
- Revalidar com uma conta Google ainda não cadastrada depois de a API entrar novamente em repouso.
- Infraestrutura gratuita continua sujeita a espera longa; serviço sem hibernação elimina essa latência.

## 2026-07-27 - Endurecimento dos fluxos reais de autenticação

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/identity/AuthService.java`
- `services/api/src/main/java/br/com/operadorzero/identity/AuthRateLimiter.java`
- `services/api/src/main/java/br/com/operadorzero/identity/TokenSupport.java`
- `services/api/src/main/java/br/com/operadorzero/identity/IdentityRepository.java`
- `services/api/src/main/java/br/com/operadorzero/identity/GoogleAuthorizationRequestGate.java`
- `services/api/src/main/java/br/com/operadorzero/shared/config/GatedOAuth2AuthorizationRequestResolver.java`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `services/api/src/main/resources/db/migration/V5__oauth_intent_cleanup_index.sql`
- testes backend, `.env.example`, `render.yaml` e documentação de autenticação/deploy

### O que foi feito
- Uniformizado o trabalho criptográfico de cadastro, recuperação e reenvio para reduzir enumeração por tempo.
- Removido o bloqueio global de conta pelo limitador e exigida preparação CSRF/rate limited de uso único antes do Google OIDC.
- Adicionada limpeza indexada de intenções OAuth consumidas ou expiradas.
- Substituído SHA-256 determinístico por HMAC-SHA-256 com chave obrigatória no backend.
- Alterado o Redis de `allkeys-lru` para `noeviction`.

### Motivo
- Fechar os oito achados de baixa severidade confirmados na auditoria integral sem enfraquecer login, cadastro ou OIDC legítimos.

### Impacto
- Backend, autenticação, Redis, migration aditiva, configuração de staging e documentação; nenhum deploy, push, merge ou alteração remota.

### Testes
- `mvn -B clean verify`: 40 testes aprovados.
- `npm run check`: lint, cenários, build, bundle e configuração aprovados.
- Teste direto confirma `404` sem intent e redirecionamento OIDC único com PKCE/nonce depois da preparação.

### Pendências
- Definir `AUTH_HASH_KEY` segura no Render antes de publicar; a ativação invalida sessões e links antigos.
- Validar `V5` em PostgreSQL isolado; Docker não está instalado nesta máquina.
- Executar smoke E2E em staging e nova verificação de segurança antes de promover o domínio oficial.

## 2026-07-27 - Sessão não bloqueante, início Google recuperável e indexação oficial

### Arquivos alterados
- `src/App.tsx`
- `src/api.ts`
- `src/styles.css`
- `index.html`
- `vercel.json`
- `public/robots.txt`
- `public/sitemap.xml`
- `public/manifest.webmanifest`
- `scripts/auth-flow-scenarios.mjs`
- `scripts/seo-scenarios.mjs`
- `package.json`
- `services/api/src/main/java/br/com/operadorzero/shared/config/RenderDatabaseUrlEnvironmentPostProcessor.java`
- `services/api/src/test/java/br/com/operadorzero/RenderDatabaseUrlEnvironmentPostProcessorTest.java`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/17-DIAGNOSTICO-E-PLANO-DE-PRODUCAO-2026-07-27.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Removido o bloqueio global “Validando sessão segura”; a restauração agora ocorre em segundo plano.
- O início Google ganhou progresso específico, tolerância controlada ao cold start e recuperação em erro.
- A saída só é confirmada depois que a API revoga a sessão.
- A landing, a primeira dobra móvel e os links institucionais foram reorganizados sem dados fictícios.
- O domínio oficial deixou de bloquear indexação e recebeu canonical, metadados sociais, JSON-LD, robots, sitemap e manifest reais.
- URLs PostgreSQL do ambiente agora exigem TLS no mínimo com `sslmode=require`.

### Motivo
- Corrigir a percepção de travamento no cadastro Google, tornar a sessão segura em falhas de rede e preparar o domínio oficial para descoberta e compartilhamento.

### Impacto
- Frontend, autenticação, banco em trânsito, SEO, Vercel e documentação; sem migration, exclusão de dados ou alteração de segredo.

### Testes
- `npm run check` aprovado.
- `mvn -B clean verify` aprovado com 30 testes.
- Navegador real em 320 x 640 sem erro de console; CTA principal visível na primeira tela e modal rolável.

### Pendências
- Concluir a auditoria de segurança e o callback E2E com conta de teste antes de publicar.
- Corrigir os riscos remanescentes de timing, rate limiting e autorização Google direta.
- Aplicar política `noindex` ou proteção de acesso aos previews públicos da Vercel.
## 2026-07-24 - Recuperação do cadastro Google quando a API demora

### Arquivos alterados
- `src/api.ts`
- `scripts/auth-flow-scenarios.mjs`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Centralizado o limite de espera das chamadas de autenticação.
- CSRF e preparação do redirecionamento Google agora expiram após quinze segundos.
- Em falha de tempo, o botão volta a aceitar clique e exibe orientação amigável.
- A validação inicial de sessão continua silenciosa e limitada a sete segundos.

### Motivo
- Evitar que `Continuar com Google` permaneça indefinidamente em `Aguarde...` durante a inicialização do serviço gratuito no Render.

### Impacto
- Frontend de autenticação; nenhuma alteração em credenciais, cookies, OAuth ou autorização do backend.

### Testes
- Cenários automatizados, build e validação do fluxo Google em navegador real.

### Pendências
- O primeiro acesso ainda pode levar alguns segundos enquanto o serviço gratuito desperta.

## 2026-07-24 - Saída segura da validação inicial de sessão

### Arquivos alterados
- `src/api.ts`
- `src/App.tsx`
- `src/styles.css`
- `scripts/auth-flow-scenarios.mjs`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionado cancelamento da consulta `/api/auth/session` após sete segundos.
- A expiração da consulta passa a abrir a landing pública sem criar sessão ou usuário fictício.
- Adicionado botão `Continuar no site` durante a validação.

### Motivo
- Impedir que o site fique preso quando o serviço gratuito do Render estiver despertando.

### Impacto
- Bootstrap da autenticação no frontend; login, cadastro, cookies e validações do backend permanecem inalterados.

### Testes
- Cenários de autenticação, build e reprodução em navegador real.

### Pendências
- Avaliar serviço sem suspensão automática quando o volume de usuários justificar.

## 2026-07-23 - Remoção de conteúdo fictício da aplicação oficial

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `scripts/real-content-scenarios.mjs`
- `scripts/validate-production-bundle.mjs`
- `package.json`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Removidos da aplicação oficial eventos, operadores, equipes, pontuações, anúncios, publicações, convites, alertas, conquistas e destaques fictícios.
- Landing e painel autenticado passaram a exibir estados vazios honestos até existirem dados persistidos no backend.
- O perfil autenticado usa somente nome, callsign, username e e-mail retornados pela sessão real.
- A validação do bundle passou a bloquear novamente os principais nomes fictícios usados no protótipo.

### Motivo
- Garantir que o domínio oficial apresente exclusivamente dados reais gerados por contas reais.

### Impacto
- Frontend, testes e documentação. Autenticação real preservada; módulos ainda sem backend permanecem visíveis apenas como estados vazios.

### Testes
- `npm run check` e inspeção do bundle de produção.

### Pendências
- Criar APIs persistentes e autorização por módulo antes de habilitar publicação de operações, equipes, classificados, comunidade, ranking e conquistas.

## 2026-07-23 - Remocao da monetizacao e destaques gratuitos

### Arquivos alterados
- `src/App.tsx`, `src/CommunityHighlights.tsx` e `src/styles.css`
- `services/api/src/main/resources/db/migration/V4__remove_financial_role.sql`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `scripts/free-platform-scenarios.mjs` e `package.json`
- documentacao raiz e notas Obsidian relacionadas

### O que foi feito
- Removidos painel financeiro, checkout demonstrativo, Pix, cartao, comissao, split, publicidade paga e residuos de gateway.
- Operacoes passaram a exibir somente inscricao gratuita, sem preco ou status financeiro.
- Informacoes adicionais do organizador passaram a bloquear valores, meios de pagamento, dados bancarios, QR Codes e links.
- Destaques patrocinados foram substituidos por Destaques da comunidade, com curadoria gratuita e gestao demonstrativa auditavel em memoria.
- A migration `V4` audita atribuicoes e remove o papel legado `FINANCE_MANAGER` sem apagar usuarios ou outros papeis.

### Motivo
- Manter o Operador Zero como plataforma gratuita, sem movimentacao financeira ou prioridade comprada.

### Impacto
- Frontend, banco, RBAC, testes, seguranca e documentacao; sem deploy, push, merge ou alteracao de producao.

### Testes
- Cenarios da plataforma gratuita, lint, build, testes backend, busca de residuos e varredura de seguranca.

### Pendencias
- Persistir operacoes, inscricoes, Classificados e destaques somente apos APIs com autorizacao por objeto, auditoria e testes de seguranca.
- Aplicar `V4` apenas em ambiente aprovado e com backup; nenhuma migration remota foi executada nesta alteracao.

## 2026-07-23 - Homologacao externa de e-mail e bloqueio de producao

### Arquivos alterados
- Configuracao externa do Resend e DNS do remetente
- `AUTHENTICATION.md`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Confirmado que `mail.operadorzero.com.br` esta verificado e pronto para envio no Resend.
- Confirmadas entregas externas dos e-mails de confirmacao e recuperacao sem registrar destinatarios ou tokens na documentacao.
- Identificado no Render que o PostgreSQL gratuito expira em 20 de agosto de 2026, a API gratuita hiberna e o Redis gratuito nao possui persistencia.

### Motivo
- Separar identidade tecnicamente homologada de infraestrutura realmente apta a armazenar cadastros permanentes.

### Impacto
- E-mail, arquitetura, seguranca e documentacao; sem alteracao de segredo, migration ou plano pago.

### Testes
- `POST /api/auth/password-recovery` retornou `202` com CORS do dominio oficial.
- O Resend classificou o envio como `delivered` e a readiness da API permaneceu `UP`.

### Pendencias
- Autorizar e configurar recursos persistentes antes de mudar `VITE_APP_ENV` para `production` ou remover os avisos de staging.
- Configurar DMARC, webhook de bounce, backup/restore, termos e privacidade aprovados.

## 2026-07-23 - Google OAuth publicado para publico externo

### Arquivos alterados
- Publico-alvo do cliente no Google Auth Platform
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Identificado que o cliente OAuth estava em `Testando` e nao possuia usuarios de teste, bloqueando contas Google comuns.
- Publicado o cliente como `Em producao`, mantendo o tipo de usuario `Externo`, origens HTTPS explicitas e callback na API Render.

### Motivo
- Permitir que usuarios reais criem conta com Google sem depender de inclusao manual em uma lista de teste.

### Impacto
- Google OAuth e documentacao; nenhum segredo, codigo, migration ou dado pessoal foi alterado.

### Testes
- Executado E2E no dominio oficial: sair da conta de homologacao, abrir Criar conta, aceitar Termos, continuar com Google, retornar pelo callback e restaurar a sessao autenticada.
- Readiness da API permaneceu `UP` apos o teste.

### Pendencias
- Remover o segredo antigo do cliente Google depois de confirmar que nao ha consumidores remanescentes.
- Reavaliar verificacao do Google antes de solicitar qualquer escopo adicional, confidencial ou restrito.

## 2026-07-23 - Dominio oficial no staging

### Arquivos alterados
- Configuracao DNS de `operadorzero.com.br` no Registro.br
- Dominios do projeto Operador Zero na Vercel
- Variaveis `CORS_ALLOWED_ORIGINS` e `FRONTEND_BASE_URL` no Render
- Origens JavaScript do cliente Google OAuth
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Publicado `https://operadorzero.com.br` como endereco principal do frontend de staging, com DNS e HTTPS validos.
- Configurado redirecionamento permanente de `https://www.operadorzero.com.br` para o dominio raiz.
- Mantidas somente origens HTTPS explicitas no CORS da API, preservando temporariamente o endereco legado da Vercel.
- Atualizado o retorno da autenticacao para o dominio oficial e autorizadas as novas origens no cliente Google OAuth, sem expor secrets.

### Motivo
- Tornar o dominio registrado o ponto oficial de acesso e manter cadastro, sessao e Google OIDC funcionais entre Vercel e Render.

### Impacto
- DNS, frontend Vercel, backend Render, Google OAuth e documentacao; sem migration ou alteracao de segredo no frontend.

### Testes
- Resolucao conferida nos servidores autoritativos e em resolvedores publicos.
- Vercel confirmou configuracao valida para dominio raiz e `www`; HTTPS e redirecionamento foram validados no navegador.
- Readiness da API retornou `200`; CORS, sessao e inicio do Google OIDC foram verificados a partir do dominio oficial apos o deploy.

### Pendencias
- Manter o endereco legado da Vercel na allowlist apenas durante a transicao.
- O ambiente continua identificado como staging e os modulos de negocio demonstrativos nao estao liberados para dados reais.

## 2026-07-23 - Persistencia do handshake Google OAuth no Redis

### Arquivos alterados
- `services/api/pom.xml`
- `services/api/src/main/resources/application-prod.yml`
- `services/api/src/main/java/br/com/operadorzero/identity/GoogleAuthSuccessHandler.java`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `services/api/src/test/java/br/com/operadorzero/identity/GoogleAuthSuccessHandlerTest.java`
- `services/api/src/test/java/br/com/operadorzero/shared/config/RedisOAuthSessionConfigurationTest.java`
- `src/App.tsx`, `src/styles.css` e `scripts/auth-flow-scenarios.mjs`
- `AUTHENTICATION.md` e notas relacionadas de arquitetura, seguranca, roadmap e producao

### O que foi feito
- Persistida no Redis a `HttpSession` temporaria usada pelo Spring Security durante Google OIDC, com namespace isolado, cookie dedicado e expiracao de 10 minutos.
- Preservados `state`, `nonce` e verificador PKCE entre reinicios ou troca de instancia do Render, sem alterar a sessao opaca de usuario persistida no PostgreSQL.
- Adicionado log sanitizado de falha Google contendo somente codigo tecnico validado e classe da excecao.
- Adicionados testes da configuracao Redis, serializacao completa do pedido OAuth e ausencia de detalhes sensiveis no log.
- Adicionada orientacao especifica quando uma conta Google nova tenta usar o fluxo Entrar sem aceite dos Termos.
- Eliminada a referencia visual remanescente da marca AirOps nos destaques patrocinados, reutilizando a identidade Operador Zero.

### Motivo
- O primeiro callback real falhou porque o Render Free reiniciou a API durante o consentimento e a nova instancia nao possuia o pedido de autorizacao mantido apenas em memoria.

### Impacto
- Backend, Redis, Google OIDC, observabilidade segura, testes e documentacao; sem migration e sem alteracao do frontend.

### Testes
- Testes de regressao falharam antes do patch por ausencia da sessao Redis e do diagnostico sanitizado.
- `mvn -B clean verify`: 27 testes aprovados.
- `npm run check`: lint, cenarios, build, bundle e configuracao de deploy aprovados.
- Deploy `12ca58c` Live; readiness `200`; cookie temporario seguro presente; cadastro Google, callback e sessao autenticada validados E2E.

### Pendencias
- Desativar o segredo Google anterior agora que o E2E foi concluido.
- Concluir DNS e entrega E2E da Resend antes de liberar usuarios reais.

## 2026-07-23 - Homologacao inicial do Google OAuth no staging

### Arquivos alterados
- `services/api/src/test/java/br/com/operadorzero/shared/config/GoogleOAuthSecurityRouteTest.java`
- `AUTHENTICATION.md`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Configurado e habilitado o Google OIDC de staging apenas por variaveis protegidas no backend Render, sem copiar segredos para codigo, frontend ou documentacao.
- Validado remotamente o fluxo de intent com CSRF, o CORS explicito da Vercel e o redirecionamento `302` para o host oficial do Google.
- Adicionado teste de regressao da rota `/oauth2/authorization/google`, exigindo OIDC, PKCE e `nonce` quando o provedor estiver habilitado.

### Motivo
- Tornar o login Google executavel no staging e impedir regressao da rota de autorizacao quando a configuracao externa estiver ativa.

### Impacto
- Backend, autenticacao Google, configuracao de staging no Render, testes e documentacao; sem alteracao de dados persistidos.

### Testes
- `mvn -B clean verify`.
- `npm run check`.
- Readiness remoto `200`, intent Google autenticado por CSRF, redirect oficial `302` e preflight CORS da origem Vercel.

### Pendencias
- Concluir callback e criacao da sessao com conta Google de teste, entao desativar o segredo anterior do cliente OAuth.
- Homologar DNS, entrega, confirmacao e recuperacao via Resend antes de liberar usuarios reais.

## 2026-07-22 - Migração do e-mail de autenticação para Resend

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/identity/AuthMailGateway.java`
- `services/api/src/main/java/br/com/operadorzero/identity/ResendAuthMailGateway.java`
- `services/api/src/main/java/br/com/operadorzero/identity/ResendProperties.java`
- `services/api/src/main/java/br/com/operadorzero/identity/ResendMailHealthIndicator.java`
- `services/api/src/main/java/br/com/operadorzero/identity/AuthMailListener.java`
- `services/api/src/main/resources/application.yml`, `services/api/pom.xml` e testes
- `.env.example`, `render.yaml`, `compose.dev.yml` e `scripts/validate-production-bundle.mjs`
- documentacao de autenticacao, arquitetura, deploy e producao

### O que foi feito
- Substituido o SMTP pela API HTTPS da Resend para funcionar no Render Free sem portas SMTP.
- Mantida `RESEND_API_KEY` somente no backend, com timeout, bloqueio de redirecionamento, resposta externa descartada e logs sem chave ou destinatario em claro.
- Adicionada idempotencia por evento/token para reduzir envios duplicados e health de configuracao separado da readiness.
- Removidos dependencia Spring Mail, variaveis SMTP e Mailpit que deixaram de ser usados.
- Tornada explicita a injecao do construtor de producao e adicionado teste de contexto Spring apos diagnostico do primeiro deploy.

### Motivo
- O Render Free bloqueia trafego SMTP nas portas 25, 465 e 587, impedindo o Gmail de enviar confirmacao e recuperacao.

### Impacto
- Backend, e-mail transacional, configuracao do Render, ambiente local, seguranca do bundle e documentacao.

### Testes
- `mvn -B clean verify`: testes de contrato HTTP Resend, idempotencia, falha generica, ausencia de chave, health de configuracao e criacao real do bean Spring.
- `npm run check`: lint, cenarios funcionais, build, bundle sem identificadores sensiveis e configuracoes de deploy validadas.

### Pendências
- Criar a conta Resend, salvar a chave no Render, verificar o subdominio de envio por DNS e executar cadastro, confirmacao, login e recuperacao ponta a ponta.

## 2026-07-22 - Identidade funcional e endurecimento da cadeia de entrega

### Arquivos alterados
- `src/App.tsx`, `src/api.ts`, `src/styles.css`
- `services/api/src/main/java/br/com/operadorzero/identity/*`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `services/api/src/main/java/br/com/operadorzero/shared/web/GlobalExceptionHandler.java`
- `services/api/src/main/resources/db/migration/V3__functional_identity.sql`
- `services/api/src/main/resources/application.yml`, `application-prod.yml`, `pom.xml` e `Dockerfile`
- `services/api/src/test/java/br/com/operadorzero/identity/*`
- `.github/workflows/ci.yml`, `render.yaml`, `.env.example`, `compose.dev.yml` e scripts de validacao
- `AUTHENTICATION.md`, `ARCHITECTURE.md`, `BACKEND-ARCHITECTURE.md`, `DEPLOYMENT.md`, `PRODUCTION-CHECKLIST.md` e documentos Obsidian conectados

### O que foi feito
- Implementados cadastro por e-mail/senha, confirmacao por e-mail, login, restauracao de sessao, logout, recuperacao e troca de senha.
- Implementado Google OIDC com Authorization Code, PKCE e validacoes do Spring Security; credenciais permanecem exclusivas do backend.
- Adicionados Argon2id, tokens opacos armazenados somente como hash, cookie de sessao `HttpOnly`, CSRF com rotacao apos login, rate limit Redis e auditoria.
- Adicionadas tabelas de token, sessao, identidade OIDC e aceite de cadastro, mais papeis/permissoes iniciais.
- Conectada a identidade real ao dashboard, sem credencial de teste ou persistencia em Web Storage.
- Fixadas por SHA/digest as Actions, imagens Docker de build/runtime e Gitleaks; validacao impede regressao para referencias mutaveis.
- Adicionadas configuracoes de SMTP, Google, cookies e staging sem registrar valores sensiveis.

### Motivo
- Permitir homologar o recebimento de contas reais sem manter autenticacao simulada no navegador e corrigir o achado confirmado de cadeia de fornecimento antes de ampliar o acesso remoto.

### Impacto
- Frontend, backend, PostgreSQL, Redis, e-mail, Google OIDC, CI e configuracao de staging.
- Os modulos de negocio apos o login continuam demonstrativos e nao persistem dados.

### Testes
- `mvn -B test`: testes unitarios de identidade, seguranca e infraestrutura.
- Fluxo HTTP local com PostgreSQL/Redis reais: cadastro pendente, recusa antes da confirmacao, login, cookie `HttpOnly`, restauracao de sessao, senha incorreta, CSRF e logout.
- `npm run check`, validacao do bundle e configuracao de deploy.

### Pendencias
- Homologar SMTP e Google no staging, configurar DNS de e-mail, executar E2E externo e restauracao de backup.
- Aprovar termos/privacidade e implementar MFA administrativo antes de usuarios reais.
- Migrar cada modulo demonstrativo para APIs persistentes e autorizacao por objeto antes de anunciar funcionalidade de negocio.

## 2026-07-21 - Resposta segura para rotas inexistentes

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/shared/web/GlobalExceptionHandler.java`
- `services/api/src/test/java/br/com/operadorzero/shared/web/GlobalExceptionHandlerTest.java`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionado tratamento específico de `NoResourceFoundException` com resposta `404`, código `NOT_FOUND` e mensagem genérica.
- Adicionado teste unitário para impedir regressão do contrato de rota inexistente.

### Motivo
- O smoke test remoto identificou que `/v3/api-docs`, corretamente desabilitado no staging, era convertido pelo fallback global em falso `500`.

### Impacto
- Backend e observabilidade: clientes passam a distinguir recurso ausente de falha interna sem receber detalhes sensíveis.

### Testes
- Testes Maven, CI e nova verificação remota após o deploy.

### Pendências
- Nenhuma para este ajuste.

## 2026-07-21 - Compatibilidade da migration V1 com PostgreSQL 17

### Arquivos alterados
- `services/api/src/main/resources/db/migration/V1__identity_access_foundation.sql`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Substituidas constraints unicas com expressoes por indices unicos funcionais sobre `lower(email)` e `lower(username)`.

### Motivo
- PostgreSQL nao aceita expressoes dentro de uma constraint `UNIQUE` declarada na tabela; o primeiro deploy real foi revertido sem deixar schema parcial.

### Impacto
- Banco e backend de staging; preserva a unicidade case-insensitive prevista no modelo.

### Testes
- Build Maven e aplicacao real das migrations no PostgreSQL 17 do Render.

### Pendencias
- Confirmar a conclusao das migrations V1 e V2 no health check remoto.

## 2026-07-21 - Correção da URL PostgreSQL interna do Render

### Arquivos alterados
- `services/api/src/main/java/br/com/operadorzero/shared/config/RenderDatabaseUrlEnvironmentPostProcessor.java`
- `services/api/src/test/java/br/com/operadorzero/RenderDatabaseUrlEnvironmentPostProcessorTest.java`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/16-ARQUITETURA-DE-PRODUCAO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Separados usuário e senha da URL JDBC gerada a partir de `DATABASE_URL`.
- Aplicada a porta PostgreSQL padrão `5432` quando o Render a omite.
- Mantidas mensagens de erro genéricas, sem repetir a URL recebida.
- Adicionado teste para o formato real de URL interna do Render sem porta explícita.

### Motivo
- Corrigir a falha de inicialização observada no primeiro deploy gratuito e evitar que o driver repita credenciais ao diagnosticar uma URL JDBC malformada.

### Impacto
- Backend e deploy de staging no Render; sem alteração no frontend ou em dados persistidos.

### Testes
- Testes Maven, validação de deploy e health check remoto após novo deploy.

### Pendências
- Confirmar health check e conectividade com PostgreSQL e Redis no ambiente gratuito.

## 2026-07-21 - Endurecimento visual e funcional do staging

### Arquivos alterados
- `src/App.tsx`, `src/styles.css`, `src/vite-env.d.ts`, `index.html`
- `.env.example`, `vercel.json`
- teste de adaptacao da URL PostgreSQL do Render
- documentacao Vercel e notas Obsidian relacionadas

### O que foi feito
- Adicionado banner permanente de ambiente de testes e bloqueio de indexacao.
- Aplicado fail-safe: toda build otimizada permanece identificada como staging ate `VITE_APP_ENV=production` ser definido explicitamente.
- Google e formularios remotos permanecem desabilitados no staging ate os endpoints reais existirem.
- Removida uma URL ficticia com formato de credencial de uma fixture de teste.
- Registrado no `.gitleaksignore` um falso positivo historico e imutavel da linha de arquitetura que apenas enumera tecnologias, mantendo a varredura ativa para todo o restante.
- Atualizado `actions/checkout` para a versao com runtime atual, eliminando o aviso de Node.js 20 no GitHub Actions.

### Motivo
- Atender aos gates de staging sem simular autenticacao ou manter strings com formato de segredo.

### Impacto
- Frontend de staging, configuracao Vercel, testes e documentacao; sem alteracao de producao.

### Testes
- Lint, cenarios frontend, build, bundle scan, configuracoes de deploy e testes Maven.

### Pendencias
- Implementar e testar identidade, rate limiting e autorizacao por objeto no backend.

## 2026-07-21 - Preparacao segura para GitHub, Vercel e Render

### Arquivos alterados
- `src/App.tsx`, `src/api.ts`, `src/vite-env.d.ts`, `src/styles.css`
- `services/api/src/main`, `services/api/src/test`, `services/api/Dockerfile`
- `vercel.json`, `render.yaml`, `.github/workflows/ci.yml`, `.env.example`, `.gitignore`
- documentacao de desenvolvimento, deploy, rollback, seguranca e producao na raiz
- `docs/obsidian/02-ARQUITETURA-E-STACK.md`, `05-SEGURANCA-E-PRIVACIDADE.md`, `06-FRONTEND-WEB.md`, `16-ARQUITETURA-DE-PRODUCAO.md`

### O que foi feito
- Removidos usuario e senha fixos do frontend de producao e criado cliente de autenticacao sem persistencia de credenciais.
- Adaptados porta, PostgreSQL, Redis e CORS da API ao Render.
- Criados build Docker, configuracao Vercel, Blueprint gratuito de staging e CI com testes, build e secret scan.
- Documentado desenvolvimento local, implantacao, limites dos planos gratuitos e rollback.

### Motivo
- Preparar uma publicacao verificavel sem expor dados sensiveis no frontend ou no repositorio.

### Impacto
- Frontend, backend, CI/CD e documentacao; sem criacao de recurso pago ou liberacao para producao.

### Testes
- Lint, cenarios frontend, build e validacao do bundle; testes Maven e empacotamento da API.

### Pendencias
- Implementar os endpoints reais de identidade/OAuth, validar migrations em PostgreSQL real e concluir os gates de producao.

## 2026-07-21 - Remocao do CEP no cadastro de operacoes

### Arquivos alterados
- `src/App.tsx`
- `docs/obsidian/09-OPERACOES.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Removido o campo CEP da etapa de localizacao ao criar uma operacao.
- Mantidos Estado, Cidade, local e instrucoes de chegada.

### Motivo
- Adequar o cadastro de operacoes ao requisito de localizacao por Estado e Cidade.

### Impacto
- Frontend demonstrativo, sem alteracao de persistencia.

### Testes
- Build TypeScript/Vite.

### Pendencias
- Nenhuma para este ajuste visual.

## 2026-07-21 - Estados e cidades brasileiras dependentes

### Arquivos alterados
- `src/BrazilLocationFields.tsx`, `src/App.tsx`, `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`, `08-CLASSIFICADOS.md`, `13-MEU-OPERADOR.md` e `99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criado seletor reutilizavel com as 27 UFs e municipios da UF consultados no IBGE.
- Aplicado em Classificados, propostas, Operacoes, Minha Equipe e Meu Operador.
- Cidade permanece bloqueada ate a escolha do Estado e e limpa ao trocar a UF.

### Motivo
- Impedir localizacoes inconsistentes e cobrir todo o territorio brasileiro.

### Impacto
- Frontend responsivo; a persistencia e validacao definitiva ainda dependem da API.

### Testes
- Build TypeScript/Vite aprovado.
- Validacao no navegador: 27 UFs; Sao Paulo retornou 645 municipios e a capital foi encontrada; layout mobile 390 x 844 aprovado.

### Pendencias
- Validar UF e codigo IBGE do municipio no backend quando os endpoints forem implementados.

## 2026-07-21 - Fundacao da arquitetura real de producao

### Arquivos alterados
- `services/api/**`
- `compose.dev.yml`, `.env.example`, `.gitignore`
- documentacao de arquitetura, seguranca e operacao na raiz
- notas `00`, `01`, `02`, `07`, `16` e `99` deste vault

### O que foi feito
- Criado backend Java 21/Spring Boot 3.5 com PostgreSQL/Flyway, Redis, OpenAPI, health/metricas, logs estruturados e seguranca restritiva.
- Criadas migrations iniciais de identidade, RBAC, auditoria, perfil e privacidade.
- Consolidado diagnostico, arquitetura alvo, fases, riscos, custos indicativos e checklist de producao.

### Motivo
- Preparar a migracao segura do prototipo sem afirmar prontidao prematura.

### Impacto
- Nova fundacao backend e documentacao; nenhum deploy ou remocao de funcionalidade.

### Testes
- `mvn test`: 2 testes, 0 falhas, `BUILD SUCCESS`.

### Pendencias
- Implementar as fases 2 a 8 e cumprir `PRODUCTION-CHECKLIST.md` antes do go-live.

## 2026-07-21 - Financeiro e publicidade patrocinada

### Arquivos alterados
- `src/FinancialPage.tsx`, `src/App.tsx`, `src/styles.css`
- `scripts/financial-scenarios.mjs`, `package.json`
- `docs/obsidian/15-FINANCEIRO-E-PUBLICIDADE.md` e documentação relacionada

### O que foi feito
- Criados painel financeiro, simulador de comissão/split, campanhas, checkout sandbox e Destaques patrocinados na Visão Geral.
- Documentados ledger, migrations, endpoints, Split 1:1, OAuth, webhook, publicidade, moderação, segurança, riscos jurídicos e tributários.

### Motivo
- Preparar monetização por comissão de operações e publicidade sem misturar valores de terceiros com receita da plataforma.

### Impacto
- Frontend, testes e contratos futuros; sem cobrança, API, banco, Mercado Pago ou deploy.

### Testes
- `npm run test:financial` e `npm run build`.

### Pendências
- Backend, sandbox, elegibilidade comercial, OAuth, checkout, webhook, ledger, conciliação, fiscal, jurídico e contabilidade conforme [[15-FINANCEIRO-E-PUBLICIDADE]].

## 2026-07-21 - Simplificação mobile da Comunidade

### Arquivos alterados
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`, `docs/obsidian/14-COMUNIDADE.md`

### O que foi feito
- Reduzidos hero, barras simultâneas, metadados e ações visíveis no feed móvel.
- Cards passaram a uma coluna e priorizam título, autor, utilidade e comentários; ações secundárias continuam disponíveis no detalhe.

### Motivo
- Melhorar leitura, foco e uso com uma mão em aparelhos móveis.

### Impacto
- Frontend responsivo, sem mudança de dados, regras ou segurança.

### Testes
- Build, cenários da Comunidade e inspeção visual mobile.

### Pendências
- Nenhuma específica deste ajuste visual.

## 2026-07-21 - Comunidade Operador Zero

### Arquivos alterados
- `src/CommunityPage.tsx`, `src/App.tsx`, `src/styles.css`
- `scripts/community-scenarios.mjs`, `package.json`
- `docs/obsidian/14-COMUNIDADE.md` e nove políticas comunitárias conectadas
- documentação geral relacionada

### O que foi feito
- Criado módulo responsivo com feed, categorias, filtros, busca, criação, aceite, pré-análise demonstrativa, comentários, respostas, voto positivo, salvos, denúncia confidencial, regras, atividade e painel de moderação fictício.
- Integrada a Comunidade à navegação e à busca global.
- Especificados modelo, migrations, endpoints, permissões, moderação progressiva/contextual, recursos, retenção, incidentes, privacidade e guia de moderador.

### Motivo
- Oferecer fórum próprio com convivência segura, transparência e separação do ranking esportivo.

### Impacto
- Frontend, testes locais e contratos futuros; sem banco, API, moderação real ou deploy.

### Testes
- `npm run test:community`, demais cenários e `npm run build`.

### Pendências
- Backend, migrations, storage, análise contextual, revisão humana operacional, MFA/RBAC, integração, segurança e revisão jurídica dos termos conforme [[14-COMUNIDADE]].

## 2026-07-21 - Meu Operador e busca global

### Arquivos alterados
- `src/App.tsx`, `src/styles.css`
- `scripts/operator-profile-scenarios.mjs`, `package.json`
- documentação relacionada e `docs/obsidian/13-MEU-OPERADOR.md`

### O que foi feito
- Reconstruída a área Meu Operador com nove abas, identidade, equipamentos, equipe, recrutamento, ranking, conquistas, operações, reputação agregada, privacidade por campo e configurações da conta.
- Adicionadas edição demonstrativa, predefinições, pré-visualização por papel, avisos de segurança e busca global agrupada sem campos privados.
- Documentados entidades, migrations, endpoints, autorização, username, mídia, auditoria e testes necessários no backend.

### Motivo
- Transformar o perfil em ponto central da identidade esportiva e preparar um contrato seguro para persistência e descoberta pública.

### Impacto
- Frontend, testes locais e contratos futuros; sem banco, API, autenticação real ou deploy.

### Testes
- `npm run test:operator`, `npm run test:ranking`, `npm run test:achievements` e `npm run build`.

### Pendências
- Implementar backend, Flyway, RBAC, MFA, uploads, busca indexada, rota pública e testes de integração definidos em [[13-MEU-OPERADOR]].

## 2026-07-21 - Módulo demonstrativo de Conquistas e Medalhas

### Arquivos alterados
- `src/achievement-catalog.ts`, `src/App.tsx`, `src/styles.css`
- `scripts/achievement-scenarios.mjs`, `package.json`
- documentação relacionada e `docs/obsidian/12-CONQUISTAS-E-MEDALHAS.md`

### O que foi feito
- Integrado catálogo de 100 conquistas, coleção responsiva, filtros, progresso, raridades, detalhes, compartilhamento, três destaques no perfil e simulador administrativo.
- Documentados modelo, migrations, endpoints, motor configurável, processamento incremental, segurança e cadastro sem código.

### Motivo
- Reconhecer evolução equilibrada e preparar concessões reproduzíveis e auditáveis.

### Impacto
- Frontend, testes locais e contratos futuros; sem banco, API ou deploy.

### Testes
- `npm run test:achievements`, `npm run test:ranking` e `npm run build`.

### Pendências
- Implementar backend, Flyway, outbox, RBAC/MFA, uploads, notificações e testes de integração descritos em [[12-CONQUISTAS-E-MEDALHAS]].

## 2026-07-21 - Identificação de adversários no desempenho

### Arquivos alterados
- `src/App.tsx`, `src/styles.css`
- `docs/obsidian/11-RANKING.md`, `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionados campos para informar o nick de cada inimigo eliminado e de cada operador que eliminou o usuário.
- Incluídos contadores, remoção de vínculos, sugestões da operação e tratamento responsivo.

### Motivo
- Permitir confirmação individual e rastreável dos eventos de combate.

### Impacto
- Frontend e contrato futuro do Ranking; sem persistência nesta etapa.

### Testes
- Build TypeScript/Vite e teste automatizado do Ranking.

### Pendências
- Resolver nick para UUID de participante no backend e validar elegibilidade na operação.

## 2026-07-21 - Reconstrução demonstrativa do Ranking

### Arquivos alterados
- `src/App.tsx`, `src/styles.css`, `scripts/ranking-scenarios.mjs`, `package.json`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`, `03-DOMINIO-E-REGRAS-DE-NEGOCIO.md`, `04-MODULOS-E-FLUXOS.md`, `06-FRONTEND-WEB.md`, `07-ROADMAP-MVP.md`, `11-RANKING.md`

### O que foi feito
- Conectada área responsiva de ranking com filtros, escopos, desempenho, confirmação, contestação e memória de cálculo.
- Documentados dados, migrações, contratos, fórmula versionada e controles antifraude.
- Adicionados 18 cenários automatizados do motor demonstrativo.

### Motivo
- Transformar o ranking estático em fluxo operacional auditável e preparar a implementação backend-first.

### Impacto
- Frontend, documentação e testes locais; sem banco, API ou deploy.

### Testes
- `npm run test:ranking` e `npm run build`.

### Pendências
- Implementar backend, migrations, autorização, jobs, reputação secreta e testes de integração/carga descritos em [[11-RANKING]].

## 2026-07-21 - Ampliação demonstrativa de Minha Equipe

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/03-DOMINIO-E-REGRAS-DE-NEGOCIO.md`
- `docs/obsidian/04-MODULOS-E-FLUXOS.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/10-MINHA-EQUIPE.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criados estados com e sem equipe, painel Valkyrie Ops, abas, integrantes, convites e histórico.
- Criados assistente de equipe, busca pública, confirmação de convite e decisão protegida de troca.
- Reutilizado o catálogo de modalidades de Operações.
- Documentados migrations, endpoints, invariantes, uploads, limites e permissões futuros.

### Motivo
- Permitir visualizar e validar o fluxo completo de equipes antes do backend.

### Impacto
- Frontend demonstrativo e documentação; sem criação, convite, troca, upload ou permissão real.

### Testes
- Build TypeScript/Vite e inspeção funcional e responsiva.

### Pendências
- Implementar API, migrations, storage, autorização, transações, jobs de expiração, notificações e auditoria.

## 2026-07-21 - Modalidade independente do tipo de jogo

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/09-OPERACOES.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Separados os campos obrigatórios de modalidade e tipo de jogo.
- Incluídas modalidades predefinidas, modalidade personalizada, indicação para iniciantes e sugestões confirmáveis.
- Adicionados modalidade aos cards, detalhes, pesquisa e filtros.
- Documentados catálogo administrável, entidades, endpoints e migration futura.

### Motivo
- Representar corretamente estilo/regulamento e dinâmica/objetivo como dimensões diferentes.

### Impacto
- Frontend demonstrativo e documentação; sem banco, API ou administração persistida.

### Testes
- Build TypeScript/Vite e validação do assistente, cards, detalhes e filtros.

### Pendências
- Criar catálogo persistido, migration aditiva, autorização administrativa e auditoria no backend futuro.

## 2026-07-21 - Ampliação demonstrativa do módulo Operações

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/04-MODULOS-E-FLUXOS.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/09-OPERACOES.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criadas agenda, abas, ordenação cronológica, filtros e cards de operações.
- Criados detalhes com regras, missões, times/esquadrões, cronograma, PDF, participantes e atualizações.
- Criado assistente de organizador em seis etapas com modalidade personalizada e salvamento demonstrativo.
- Documentados modelo, endpoints, migrations, permissões, uploads e auditoria futuros.

### Motivo
- Evoluir a superfície de Operações sem inventar backend inexistente ou romper o protótipo atual.

### Impacto
- Frontend demonstrativo e documentação; nenhuma API, migration, entidade persistida ou deploy.

### Testes
- Build TypeScript/Vite e inspeção funcional e responsiva.

### Pendências
- Implementar integralmente API, PostgreSQL/Flyway, storage, autorização, jobs, notificações, auditoria e testes de segurança.

## 2026-07-21 - Estado e confirmação na nova publicação

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/08-CLASSIFICADOS.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionada seleção obrigatória `Novo` ou `Usado` no fluxo `Quero vender`.
- Criada confirmação obrigatória e contextual sobre estado verdadeiro, propriedade e procedência.
- Acrescentadas declarações específicas para venda ocasional de item novo e descrição transparente de item usado.

### Motivo
- Padronizar a condição exibida e aumentar a clareza e responsabilidade do anunciante.

### Impacto
- Frontend demonstrativo e documentação; sem persistência ou validação server-side.

### Testes
- Build TypeScript/Vite e validação do formulário.

### Pendências
- Repetir validação, versionamento do aceite e auditoria no backend futuro.

## 2026-07-21 - Modalidade Procuro equipamento

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/08-CLASSIFICADOS.md`
- `docs/obsidian/CLASSIFICADOS-PROCURO-EQUIPAMENTO.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Integradas publicações `À venda` e `Procuro` no mesmo mural.
- Criados formulário de procura, compatibilidade, validade, urgência, detalhes, proposta rápida, chat e sugestão de correspondência.
- Mantidas privacidade, restrições de produtos, não intermediação e ausência de transação financeira.

### Motivo
- Permitir que operadores informem equipamentos permitidos que desejam encontrar e recebam contatos estruturados.

### Impacto
- Frontend demonstrativo e documentação; nenhuma entidade, migration, API, persistência ou envio real foi criado porque o projeto atual não possui backend.

### Testes
- Build TypeScript/Vite e inspeção dos fluxos desktop/mobile.

### Pendências
- Backend, migration, autorização por objeto, moderação, auditoria, expiração, notificações e testes de segurança antes de produção.

## 2026-07-21 - Filtro por estado do item nos Classificados

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/08-CLASSIFICADOS.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Adicionado filtro `Todos`, `Novo` e `Usado` combinável com tipo, preço e localização.
- Normalizadas as condições demonstrativas sem alterar os textos exibidos nos anúncios.
- Ajustada a grade responsiva do painel para o quarto filtro.

### Motivo
- Permitir separar rapidamente itens novos e usados nos Classificados.

### Impacto
- Frontend demonstrativo e documentação, sem alteração de backend ou persistência.

### Testes
- Build TypeScript/Vite e validação funcional dos filtros.

### Pendências
- Reproduzir a mesma classificação no contrato e nas consultas do backend futuro.

## 2026-07-21 - Filtros e negociação segura nos Classificados

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/08-CLASSIFICADOS.md`
- `docs/obsidian/CLASSIFICADOS-PRODUTOS-PERMITIDOS.md`
- `docs/obsidian/CLASSIFICADOS-PRODUTOS-PROIBIDOS.md`
- `docs/obsidian/CLASSIFICADOS-RISCOS-JURIDICOS.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criados filtros por tipo, faixa de preço e localização, priorizando a cidade do perfil.
- Criado fluxo especial de AEGs e marcadores somente textual, sem fotos e com declaração de nota fiscal ou prova de origem.
- Criado chat demonstrativo com aviso de não intermediação e recomendações de segurança.

### Motivo
- Facilitar a descoberta local e reduzir riscos na publicação e negociação de equipamentos sensíveis.

### Impacto
- Frontend demonstrativo e documentação; sem backend, persistência, pagamento ou geolocalização do aparelho.

### Testes
- Build TypeScript/Vite e inspeção responsiva do fluxo conectado.

### Pendências
- Parecer jurídico, backend, moderação, denúncias e verificação documental antes de produção.

## 2026-07-21 - Legibilidade das telas conectadas

### Arquivos alterados
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Ampliada a tipografia do dashboard, navegação, Classificados, formulários, avisos e metadados.
- Eliminada a microtipografia de 6–9 px nas informações importantes após o login.
- Ajustados altura de linha, áreas de toque e navegação inferior no mobile.

### Motivo
- Melhorar leitura e uso em computadores e aparelhos móveis sem necessidade de zoom.

### Impacto
- Somente apresentação das superfícies conectadas.

### Testes
- Build TypeScript/Vite e inspeção visual de dashboard e Classificados.

### Pendências
- Realizar auditoria formal de acessibilidade antes da produção.

## 2026-07-21 - Credencial de login demonstrativa

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criada uma credencial fictícia para teste manual do login.
- O formulário passou a rejeitar combinações diferentes e exibir mensagem de erro.
- A conta de teste é exibida no modal para facilitar a validação local.

### Motivo
- Permitir testar manualmente o fluxo de entrada no protótipo.

### Impacto
- Somente frontend demonstrativo; sem conta real, hash, cookie, token ou persistência.

### Testes
- Build TypeScript/Vite e testes manuais de credencial inválida e válida.

### Pendências
- Substituir a verificação local por autenticação real no backend.

## 2026-07-20 - Classificados Operador Zero - revisão e primeira etapa

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/02-ARQUITETURA-E-STACK.md`
- `docs/obsidian/03-DOMINIO-E-REGRAS-DE-NEGOCIO.md`
- `docs/obsidian/04-MODULOS-E-FLUXOS.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/08-CLASSIFICADOS.md`
- `docs/obsidian/CLASSIFICADOS-*.md`

### O que foi feito
- Substituído o conceito principal de Marketplace por mural gratuito de Classificados.
- Removida a noção de transação financeira do modelo planejado.
- Criada experiência responsiva demonstrativa com busca, categorias permitidas, favoritos, avisos e formulário visual.
- Criados documentos de regras, produtos, moderação, privacidade, segurança, riscos, termos e incidentes.

### Motivo
- Reduzir ambiguidades de intermediação e implementar a primeira etapa segura solicitada.

### Impacto
- Frontend e documentação; sem backend, persistência, pagamento, frete ou deploy.

### Testes
- Build TypeScript/Vite e validação visual/funcional responsiva.

### Pendências
- Backend real, verificações, upload seguro, moderação, auditoria e revisão jurídica antes de produção.

## 2026-07-20 - Planejamento e gates do Marketplace

### Arquivos alterados
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/02-ARQUITETURA-E-STACK.md`
- `docs/obsidian/03-DOMINIO-E-REGRAS-DE-NEGOCIO.md`
- `docs/obsidian/04-MODULOS-E-FLUXOS.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/07-ROADMAP-MVP.md`
- `docs/obsidian/08-MARKETPLACE.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Documentados escopo, riscos jurídicos, categorias, fluxos, arquitetura, entidades, permissões, wireframes, moderação, antifraude e integrações.
- Definido gate 18+ e bloqueio inicial de categorias sensíveis.
- Planejado MVP sem pagamento, carteira, frete ou intermediação financeira.

### Motivo
- Cumprir a etapa obrigatória de análise antes de programar o Marketplace.

### Impacto
- Arquitetura e documentação; nenhum código funcional foi implementado.

### Testes
- Revisão de consistência entre arquitetura, segurança, domínio e roadmap.

### Pendências
- Validação por advogado brasileiro e autorização do usuário para iniciar a implementação.

## 2026-07-20 - Nova identidade Operador Zero

### Arquivos alterados
- `public/operador-zero-identity.jpeg`
- `src/App.tsx`
- `src/styles.css`
- `index.html`
- `package.json`
- `package-lock.json`
- `SECURITY.md`
- `docs/obsidian/00-LEIA-ANTES-CODEX.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- A marca visível foi alterada de AirOps para Operador Zero.
- A arte enviada pelo usuário foi aplicada ao hero, login, comunidade e destaques.
- Título, metadados, textos institucionais e identidade interna do pacote foram atualizados.

### Motivo
- Adotar o novo nome e a nova logo oficial do produto.

### Impacto
- Identidade visual e nomenclatura do frontend e da documentação.

### Testes
- Build TypeScript/Vite e inspeção visual em navegador.

### Pendências
- Nenhuma para a troca de identidade no protótipo atual.

## 2026-07-20 - Correção da grade do método na landing

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Título e descrição de cada etapa foram agrupados em uma coluna de conteúdo.
- A grade recebeu larguras estáveis para ícone e numeração, mais uma coluna textual flexível.
- O comportamento mobile e o realce discreto ao passar o cursor foram ajustados.

### Motivo
- Corrigir as quebras de linha excessivas observadas na lista Encontre, Confirme e Evolua.

### Impacto
- Somente frontend público; sem mudança de dados, autenticação ou contratos.

### Testes
- Build TypeScript/Vite e inspeção visual responsiva.

### Pendências
- Nenhuma para este ajuste.

## 2026-07-20 - Dashboard demonstrativo do operador conectado

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criada a experiência pós-login do operador NOMAD em modo demonstração.
- Adicionados navegação autenticada, resumo do perfil, ranking, reputação, operação, pendências, evolução e conquista.
- Criada navegação mobile e saída explícita do modo demonstração.

### Motivo
- Permitir visualizar como a plataforma se comporta após a conexão do usuário.

### Impacto
- Frontend demonstrativo; nenhum token, cookie ou credencial é persistido.

### Testes
- Build TypeScript/Vite e validação visual/funcional em navegador desktop e mobile.

### Pendências
- Substituir dados fictícios por contratos reais após implementação da API e autenticação.

## 2026-07-20 - Fluxo visual de autenticação simples

### Arquivos alterados
- `src/App.tsx`
- `src/styles.css`
- `docs/obsidian/01-ESTADO-ATUAL-DO-PROJETO.md`
- `docs/obsidian/02-ARQUITETURA-E-STACK.md`
- `docs/obsidian/04-MODULOS-E-FLUXOS.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Criadas experiências de login e cadastro por Google ou e-mail/senha.
- Criado fluxo de recuperação exclusivamente por e-mail com mensagem não enumerável.
- Conectados cabeçalho, menu mobile e CTA ao painel de acesso.

### Motivo
- Oferecer cadastro simples e recuperação por e-mail conforme requisito do produto.

### Impacto
- Frontend e contratos de identidade; sem armazenamento de credenciais ou sessão simulada.

### Testes
- Build TypeScript/Vite e validação em navegador.

### Pendências
- Implementar backend, Google OAuth/OIDC, confirmação e recuperação por serviço de e-mail.

## 2026-07-20 - Fundação documental e experiência pública do AirOps

### Arquivos alterados
- `package.json`, configurações Vite/TypeScript, `src/App.tsx`, `src/styles.css`, `public/air-ops-identity.png`
- `docs/obsidian/*`
- documentos de segurança na raiz

### O que foi feito
- Criada a base web responsiva e a landing inicial com dados demonstrativos.
- Definidos arquitetura modular, domínio, fluxos, ranking, roadmap e políticas de segurança.

### Motivo
- Iniciar o projeto pela compreensão do produto, identidade, regras e riscos antes de funcionalidades complexas.

### Impacto
- Frontend e documentação; sem backend, banco ou deploy.

### Testes
- Build TypeScript/Vite e inspeção responsiva previstos após a criação.

### Pendências
- Implementar os marcos do [[07-ROADMAP-MVP]] e validar juridicamente participação de menores.
## 2026-07-27 - Meu Operador, busca e equipes com persistência real

### Arquivos alterados
- `src/api.ts`, `src/App.tsx`, `src/OperatorPage.tsx`, `src/OperatorSearch.tsx`, `src/TeamPage.tsx`, `src/functional-modules.css`
- `services/api/src/main/java/br/com/operadorzero/operator/*`
- `services/api/src/main/java/br/com/operadorzero/team/*`
- `services/api/src/main/java/br/com/operadorzero/shared/audit/AuditEventRepository.java`
- `services/api/src/main/resources/db/migration/V6__operator_profile_functionality.sql`
- `services/api/src/main/resources/db/migration/V7__teams_and_invitations.sql`
- `scripts/stage-two-scenarios.mjs` e testes Java relacionados
- [[13-MEU-OPERADOR]], [[10-MINHA-EQUIPE]], [[06-FRONTEND-WEB]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]]

### O que foi feito
- Implementados perfil, privacidade, equipamentos, busca segura, criação/edição de equipe, integrantes, convites, aceite/recusa, saída e transferência de capitania.
- Removidos do caminho oficial os estados fictícios de Meu Operador e Minha Equipe.
- Adicionados constraints, rate limits, autorização por vínculo/função, transações, versão otimista e auditoria.

### Motivo
- Iniciar a funcionalização dos módulos pós-login usando somente dados reais dos usuários.

### Impacto
- Frontend, backend, banco PostgreSQL e documentação; sem alteração em produção.

### Testes
- `npm run check`: aprovado, incluindo lint, cenários, build, bundle e configuração de deploy.
- `mvn -B clean verify`: aprovado, 45 testes.

### Pendências
- Aplicar migrations somente após backup/preflight no ambiente adequado.
- Criar rota pública de operador, histórico consultável e validação autoritativa município/UF no backend.
- Implementar storage seguro antes de qualquer upload.
- Nenhum deploy, push ou merge foi executado.
## 2026-07-27 - Redução do congelamento no login e cadastro

### Arquivos alterados
- `services/api/pom.xml`
- `services/api/src/main/resources/application.yml`

### O que foi feito
- Substituído o starter JPA/Hibernate pelo starter JDBC, compatível com os repositórios reais baseados em `NamedParameterJdbcTemplate`.
- Desabilitada a procura de repositories Redis, inexistentes no projeto.

### Motivo
- O Render gratuito desligava a API por inatividade e o cold start observado levou 102,3 segundos, fazendo login e cadastro parecerem congelados.

### Impacto
- Backend e tempo de inicialização; schema, Flyway, autenticação, sessões Redis e contratos HTTP permanecem inalterados.

### Testes
- `mvn -B clean verify` e smoke test remoto após deploy.

### Pendências
- O plano gratuito ainda pode adormecer; confirmar o novo tempo de cold start após a publicação.
- Configurar futuramente `api.operadorzero.com.br` para cookies de autenticação no mesmo domínio registrável.

## 2026-07-28 - Correção do login Google e e-mail com sessão first-party

### Arquivos alterados
- `src/api.ts`
- `vercel.json`
- `scripts/auth-flow-scenarios.mjs`
- `scripts/validate-deployment-config.mjs`
- `AUTHENTICATION.md`
- `ENVIRONMENT-VARIABLES.md`
- `DEPLOYMENT.md`
- `docs/obsidian/05-SEGURANCA-E-PRIVACIDADE.md`
- `docs/obsidian/06-FRONTEND-WEB.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Login, cadastro, sessão, CSRF e Google OAuth passaram a usar a origem oficial por proxy da Vercel.
- Adicionadas regras de regressão para impedir que o fallback da SPA capture rotas de autenticação ou que o browser volte a acessar o Render diretamente.
- Corrigido o estado visual que aparentava congelamento durante o cold start e a restauração permanente do estado pendente ao cancelar o Google ou voltar pelo navegador.
- Adicionado reenvio neutro da confirmação e orientação para consultar Lixo Eletrônico; o Resend confirmou que a entrega recente foi aceita pelo servidor destinatário.
- Publicado o TXT `_dmarc.mail` com política inicial `p=none`; Registro.br confirmou a atualização e a resposta foi validada nos servidores autoritativos e no Google Public DNS.

### Motivo
- A separação entre `operadorzero.com.br` e `onrender.com` transformava a sessão em cookie de terceiro, bloqueável pelo navegador e comum aos sintomas dos dois métodos de acesso.

### Impacto
- Frontend, roteamento de deploy e autenticação; sem alteração de schema ou segredos.

### Testes
- `npm run check`, `mvn -B clean verify`, regressão de retorno `pageshow`, probes remotos e navegação Playwright.

### Deploy e verificação remota
- Callback `https://operadorzero.com.br/login/oauth2/code/google` cadastrado no cliente Google, mantendo temporariamente o callback anterior para rollback.
- `VITE_API_URL`, `GOOGLE_REDIRECT_URI` e `AUTH_COOKIE_SAME_SITE` alinhados com o domínio oficial, sem registrar valores secretos.
- Frontend publicado na Vercel pelo commit `8d40638` e backend publicado pelo commit `5c1d31e`; readiness, CSRF, callback Google, persistência após recarga e logout foram verificados pela origem oficial.

### Pendências
- Confirmar um novo cadastro por e-mail e o recebimento da confirmação em uma caixa postal de homologação.
- Acompanhar relatórios e entregabilidade antes de evoluir DMARC de `p=none` para `quarantine` ou `reject`.
- Desativar o segredo Google anterior somente depois da homologação final, preservando rollback até lá.
## 2026-07-28 - Visão Geral com operações, equipes e ranking reais

### Arquivos alterados
- `src/App.tsx`, `src/api.ts` e `src/styles.css`
- `services/api/src/main/java/br/com/operadorzero/team/TeamController.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamService.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamRepository.java`
- `services/api/src/main/java/br/com/operadorzero/team/TeamDtos.java`
- documentação [[06-FRONTEND-WEB]], [[09-OPERACOES]], [[10-MINHA-EQUIPE]], [[11-RANKING]] e [[14-COMUNIDADE]]

### O que foi feito
- Substituídos os placeholders da Visão Geral por operações futuras priorizadas por região/data, total de equipes ativas e os três melhores operadores do ranking.
- Comunidade reduzida a título e subtítulo descritivo.
- Criado endpoint autenticado de resumo para a contagem de equipes ativas.

### Motivo
- Apresentar informações úteis e reais nos cartões principais do painel.

### Impacto
- Frontend web e backend, sem alteração de banco.

### Testes
- `npm run build`: sucesso.
- `mvn test` em `services/api`: 63 testes, zero falhas.

### Pendências
- Nenhuma pendência de código identificada.

## 2026-07-28 - Comunidade funcional e rota pública

### Arquivos alterados
- `src/App.tsx`, `src/CommunityPage.tsx`, `src/api.ts`, `src/main.tsx`
- `src/community-functional.css`
- `services/api/src/main/java/br/com/operadorzero/community/*`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `services/api/src/main/resources/db/migration/V16__community_posts_comments_and_moderation.sql`
- testes, scripts e documentação [[14-COMUNIDADE]]

### O que foi feito
- O card Comunidade foi conectado a `/comunidade` sem receber métricas ou conteúdo fictício.
- O protótipo local foi substituído por feed, detalhe, criação, imagens, comentários/respostas, votos, salvos, compartilhamento, denúncias e perfis reais.
- Criado backend JDBC persistente, leitura pública, interações autenticadas, autorização por objeto, moderação RBAC e auditoria.
- Criada migration aditiva com categorias, publicações, mídia, comentários, votos, salvos e denúncias.

### Motivo
- Tornar a Comunidade utilizável sobre autenticação e banco existentes, sem recriar o projeto.

### Impacto
- Frontend web, backend, PostgreSQL, segurança, moderação e documentação.

### Testes
- `npm run lint`, cenários frontend, build TypeScript/Vite e testes Java.
- Migration ainda requer PostgreSQL isolado/backup antes de aplicação remota.

### Pendências
- Executar preflight/backup e aplicar `V16` de forma controlada antes de deploy.
- Validar E2E autenticado em ambiente com PostgreSQL e usuários de teste.

## 2026-07-28 - Deploy da Comunidade no domínio oficial

### Arquivos alterados
- `docs/obsidian/14-COMUNIDADE.md`
- `docs/obsidian/99-HISTORICO-DE-ALTERACOES.md`

### O que foi feito
- Publicado o commit `4260599` na branch `deploy/render-vercel`.
- CI aprovada nos jobs frontend, backend com build Docker, configuração de deploy e Gitleaks.
- Backend publicado no Render; Flyway validou 16 migrations e aplicou `V16__community_posts_comments_and_moderation.sql` no PostgreSQL 17.
- Frontend publicado em produção na Vercel e associado a `operadorzero.com.br`.
- Docker Desktop 29.6.2 instalado localmente em modo por usuário; o daemon permanece pendente da ativação administrativa do WSL.

### Motivo
- Disponibilizar a Comunidade funcional para usuários reais no domínio oficial.

### Impacto
- Frontend, backend, PostgreSQL, Render, Vercel, GitHub Actions e ambiente local de desenvolvimento.

### Testes
- `npm run check`: aprovado.
- `mvn -B clean verify`: 69 testes, zero falhas.
- GitHub Actions `30392633595`: quatro jobs aprovados.
- Render `live`, schema na versão 16 e sem erro no período do deploy.
- Vercel `READY`, sem erro ou fatal nos logs do deployment.
- HTTP 200 para readiness, categorias, feed e `/comunidade` pela origem oficial.
- Card autenticado abriu `/comunidade`; formulário autenticado `/comunidade/nova` carregou sem publicar conteúdo de teste.

### Pendências
- Autorizar a ativação do WSL em uma janela administrativa e reiniciar o Windows para habilitar o daemon local do Docker.

## 2026-07-29 - Autenticação exclusiva por e-mail e senha

### Arquivos alterados
- `src/App.tsx`, `src/api.ts`, `src/CommunityPage.tsx`
- `services/api/src/main/java/br/com/operadorzero/identity/*`
- `services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java`
- `services/api/src/main/resources/db/migration/V17__retire_social_authentication.sql`
- `services/api/pom.xml`, `application.yml`, `application-prod.yml`
- `render.yaml`, `vercel.json`, `.env.example`
- testes, scripts de validação e documentação de autenticação/deploy

### O que foi feito
- Removido o login social do frontend, backend, dependências, rotas, configuração e testes.
- Cadastro passou a exigir confirmação da senha; login diferencia conta pendente e conta antiga sem senha.
- Mantidos Argon2id, tokens opacos com hash, cookie `HttpOnly`, CSRF, rate limit Redis e respostas neutras de recuperação/reenvio.
- Removida a espera oculta de até três minutos antes de cada ação de autenticação.
- Adicionados templates HTML/texto para confirmação, recuperação e aviso de senha alterada.
- A migration `V17` preserva usuários/perfis, registra auditoria, revoga sessões antigas e remove tabelas exclusivas do mecanismo aposentado.

### Motivo
- Corrigir o congelamento percebido no cadastro/login e simplificar o acesso oficial para e-mail e senha.

### Impacto
- Frontend, backend, banco, Redis, Resend, Render e Vercel. Usuários antigos sem senha devem usar **Esqueci minha senha**.

### Testes
- Baseline antes da alteração: `npm run check` aprovado; `mvn -B clean verify` aprovado com 69 testes.
- Após a alteração: `mvn -B test` aprovado com 65 testes; validações frontend e browser registradas na entrega.
- CI do commit `9680a6e` aprovado nos jobs frontend, backend, configuração de deploy e varredura de segredos.
- Vercel `dpl_FjqsEdX2oLowgrVgUyf2G6zTghyi` publicada e associada a `operadorzero.com.br`, sem resíduo do login Google no bundle.
- Render publicado no commit `9680a6e`; readiness, liveness e health retornaram `200`.
- Flyway validou 17 migrations e aplicou `V17__retire_social_authentication.sql`, deixando o schema na versão 17.
- Smoke remoto validou CSRF, credencial inválida neutra, confirmação obrigatória de senha, recuperação/reenvio neutros e rejeição de tokens inválidos.
- Browser oficial validado em desktop e viewport móvel `390x844`, sem overflow, sem login Google e sem erros no console.

### Pendências
- As quatro variáveis `GOOGLE_*` foram removidas do Render e o cliente OAuth exclusivo do Operador Zero foi excluído no Google Cloud; a restauração administrativa permanece possível por até 30 dias conforme o provedor.
- Permanece pendente somente o E2E humano de recebimento e abertura dos e-mails em uma caixa postal real, pois a automação não acessou mensagens privadas.
# 2026-07-29 - Estrutura pré-publicação e chat privado por esquadrão

- A criação de operações passou a conduzir o organizador ao rascunho para configurar times e esquadrões antes de publicar.
- O backend bloqueia alterações estruturais após a publicação e exige esquadrão nas inscrições de jogos médios e grandes.
- Chats privados por time foram substituídos por canais privados por esquadrão, com autorização server-side e preservação dos canais antigos como histórico inacessível.
- Migration adicionada: `V20__squad_private_chat_channels.sql`.
- Validações executadas: `npm run check` e `mvn -B clean verify`.

# 2026-07-29 - Chat geral horizontal e compatibilidade de inscrições

- O chat geral passou a ocupar toda a largura da central da operação.
- A migration `V21__backfill_legacy_operation_squads.sql` cria esquadrões compatíveis para operações antigas e vincula participantes legados ao esquadrão do próprio time.
- A seleção informa claramente quando um time ainda não possui esquadrão disponível.
- Corrigida a tipagem explícita do UUID opcional do esquadrão na consulta PostgreSQL de inscrição; a falha impedia tanto usuários quanto organizadores de ingressar.
- Mantida a aprovação automática do organizador quando ele escolhe um time e, quando aplicável, um esquadrão.

# 2026-07-30 - Identidade do usuário acompanhada do time

- Padronizada a apresentação `NOME DO USUÁRIO - NOME DO TIME` na busca, nos inscritos de operações e nos comentários.
- Usuários sem equipe ativa passam a aparecer com o sufixo `SEM TIME`.
- Os contratos de inscritos e mensagens de operações agora retornam a equipe ativa do autor.
