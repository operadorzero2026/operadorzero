# Histórico de alterações

## 2026-07-23 - Persistencia do handshake Google OAuth no Redis

### Arquivos alterados
- `services/api/pom.xml`
- `services/api/src/main/resources/application-prod.yml`
- `services/api/src/main/java/br/com/operadorzero/identity/GoogleAuthSuccessHandler.java`
- `services/api/src/test/java/br/com/operadorzero/FoundationRulesTest.java`
- `services/api/src/test/java/br/com/operadorzero/identity/GoogleAuthSuccessHandlerTest.java`
- `services/api/src/test/java/br/com/operadorzero/shared/config/RedisOAuthSessionConfigurationTest.java`
- `src/App.tsx` e `scripts/auth-flow-scenarios.mjs`
- `AUTHENTICATION.md` e notas relacionadas de arquitetura, seguranca, roadmap e producao

### O que foi feito
- Persistida no Redis a `HttpSession` temporaria usada pelo Spring Security durante Google OIDC, com namespace isolado, cookie dedicado e expiracao de 10 minutos.
- Preservados `state`, `nonce` e verificador PKCE entre reinicios ou troca de instancia do Render, sem alterar a sessao opaca de usuario persistida no PostgreSQL.
- Adicionado log sanitizado de falha Google contendo somente codigo tecnico validado e classe da excecao.
- Adicionados testes da configuracao Redis, serializacao completa do pedido OAuth e ausencia de detalhes sensiveis no log.
- Adicionada orientacao especifica quando uma conta Google nova tenta usar o fluxo Entrar sem aceite dos Termos.

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
