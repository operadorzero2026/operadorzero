# Estado atual do projeto

## 2026-07-27

- Concluída a auditoria inicial de todas as superfícies em [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]]. Somente identidade possui integração real; os nove destinos autenticados continuam sem rotas e sem contratos de negócio.
- A Etapa 1 removeu do bundle oficial mensagens sobre dados reais/fictícios, demonstração, backend, API, persistência, produção e segurança interna. Estados vazios agora usam linguagem curta e orientada ao usuário.
- Os protótipos desconectados de Comunidade, Destaques e Conquistas permanecem fora do bundle e não foram reativados. Nenhum deploy, push ou merge foi realizado nesta etapa.
- O cadastro Google no domínio oficial recebeu uma espera ativa de readiness por até três minutos depois que os logs demonstraram cold start de aproximadamente 115 segundos no Render Free. O frontend corrigido foi publicado diretamente na Vercel em produção e os aliases oficiais foram atualizados sem erro.
- O bundle oficial foi verificado com a rota `/actuator/health/readiness`; domínio e API responderam `200`. Não houve erro Google nos logs do backend durante a reprodução.
- O pacote local de endurecimento da autenticacao corrigiu enumeracao temporal, bloqueio global por e-mail, acesso direto ao inicio Google, limpeza de intencoes OAuth, hash deterministico e eviccao silenciosa do Redis.
- Em 2026-07-28, o dominio oficial passou a encaminhar API e OAuth pela mesma origem; callback Google, persistencia apos recarga e logout foram validados E2E com cookies `Secure`/`SameSite=Lax`.
- `AUTH_HASH_KEY` passou a ser obrigatoria no backend quando `AUTH_ENABLED=true`; deve ser configurada no Render antes de qualquer deploy e invalida sessoes/links antigos na primeira ativacao.
- `mvn -B clean verify` passou com 40 testes e `npm run check` passou integralmente. Nenhum deploy remoto foi executado; a migration `V5` ainda requer validacao em PostgreSQL isolado porque Docker nao esta instalado nesta maquina.
- Os modulos de negocio continuam demonstrativos e a infraestrutura gratuita continua inadequada para cadastros permanentes de usuarios reais. Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-23

- O dominio oficial `https://operadorzero.com.br` foi conectado ao frontend de staging na Vercel com DNS e HTTPS validos; `https://www.operadorzero.com.br` redireciona permanentemente para o dominio raiz.
- O Render passou a aceitar somente as origens HTTPS explicitas do dominio oficial e do endereco legado da Vercel durante a transicao; `FRONTEND_BASE_URL` aponta para o dominio raiz.
- O cliente Google OAuth manteve o callback exato no backend Render e recebeu as origens JavaScript do dominio oficial, sem mover o client secret para o frontend.
- O publico Google OAuth passou de `Testando`, sem usuarios de teste, para `Em producao` e `Externo`; o fluxo Criar conta -> aceite -> Google -> callback -> sessao foi validado no dominio oficial com a conta de homologacao existente.
- As credenciais Google OIDC de staging foram configuradas somente no backend do Render e `GOOGLE_AUTH_ENABLED` foi ativado.
- O fluxo remoto foi validado de forma nao destrutiva: readiness `200`, emissao de intent com CSRF, redirecionamento `302` para `accounts.google.com` e CORS com credenciais limitado a origens HTTPS explicitas.
- Um teste de regressao garante que `/oauth2/authorization/google` mantenha OIDC, PKCE e `nonce` quando o provedor estiver habilitado.
- Cadastro Google, callback e sessao autenticada foram validados E2E com uma conta de teste; resta desativar o segredo Google anterior.
- O primeiro callback real revelou que o Render Free pode reiniciar a API entre a autorizacao e o retorno. O estado temporario do OAuth foi movido da memoria da instancia para Redis, com expiracao de 10 minutos e namespace isolado; o E2E remoto passou depois do deploy.
- Falhas Google agora registram apenas classificacao tecnica sanitizada, permitindo distinguir estado perdido de falha na troca do token sem expor dados do provedor.
- O dominio `mail.operadorzero.com.br` esta verificado no Resend; confirmacao e recuperacao foram entregues em enderecos de homologacao, sem expor tokens. DMARC, webhook de bounce e rotina operacional de falhas continuam pendentes.
- O PostgreSQL gratuito do Render expira em 20 de agosto de 2026 e sera excluido se nao houver upgrade. A API gratuita hiberna e o Redis gratuito nao possui persistencia; esse conjunto continua sendo staging e nao deve receber cadastros reais permanentes.
- Os modulos de negocio apos o login continuam demonstrativos; o sistema ainda nao esta liberado para usuarios reais. Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-22

- Cadastro por e-mail e senha, confirmacao de e-mail, login, recuperacao de senha, sessao e logout foram conectados entre SPA e API.
- Senhas usam Argon2id; tokens de verificacao, recuperacao e sessao sao opacos e persistidos somente como hash.
- A sessao real usa cookie `HttpOnly`; mutacoes exigem CSRF; cadastro/login/recuperacao usam rate limit no Redis.
- Google OIDC com Authorization Code, PKCE, `state`, `nonce` e validacao do provedor esta implementado, mas depende das credenciais externas do ambiente.
- A migration `V3__functional_identity.sql` adiciona tokens, sessoes, identidades OIDC e papeis iniciais.
- O frontend restaura a sessao da API e nao usa `localStorage` ou `sessionStorage` para credenciais.
- O envio de confirmacao e recuperacao foi migrado do SMTP para a API HTTPS da Resend, com chave exclusiva do backend e idempotencia. A chave, o dominio e o E2E externo ainda precisam ser configurados.
- Modulos de negocio exibidos apos o login continuam demonstrativos e nao persistem dados. O marco atual permite homologar identidade, nao receber usuarios reais em todos os modulos.
- Antes de usuarios reais ainda faltam DNS/homologacao da Resend, termos e privacidade aprovados, backup restaurado, monitoramento e os gates de [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-21

- Fundacao compilavel do backend Java 21/Spring Boot 3.5 criada.
- PostgreSQL/Flyway, Redis, health/metricas, logs estruturados, OpenAPI e deny-by-default preparados.
- Migrations iniciais cobrem identidade, RBAC, auditoria, perfil e privacidade.
- O frontend continua demonstrativo e ainda nao consome casos de uso reais.
- **O produto nao esta pronto para producao.** Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## 2026-07-20

Fase zero iniciada: arquitetura, regras, segurança e primeira experiência pública.

- Implementado: base React + TypeScript + Vite, landing responsiva, experiência visual de autenticação, dashboard demonstrativo e primeira fatia frontend dos Classificados Operador Zero.
- Documentado: módulos, entidades, fluxos, ranking, privacidade e políticas de segurança.
- Não implementado: API, banco, autenticação efetiva, Google OAuth/OIDC, envio de e-mail, uploads, moderação real, Classificados persistentes, operações reais e deploy.
- Dados da interface atual: somente demonstrativos, identificados visualmente como fictícios.

Próximo marco: fundação do backend modular, schema PostgreSQL, autenticação segura e primeira fatia vertical de operador + operação.

Veja [[07-ROADMAP-MVP]] e [[99-HISTORICO-DE-ALTERACOES]].
## Atualização funcional de 2026-07-27

Meu Operador, busca de operadores e Minha Equipe possuem agora contratos reais no backend e telas conectadas sem conteúdo fictício. As migrations aditivas `V6` e `V7` cobrem perfil/equipamentos/privacidade e equipes/convites/histórico. A entrega está somente no worktree local: não foi aplicada no Render nem publicada na Vercel.

Validação local: `npm run check` aprovado e `mvn -B clean verify` aprovado com 45 testes. Antes de qualquer publicação desta etapa ainda são necessários backup/preflight do PostgreSQL, aplicação controlada das migrations, smoke test autenticado e decisão explícita de deploy. Detalhes em [[18-AUDITORIA-DE-TELAS-E-INTEGRACAO-2026-07-27]], [[13-MEU-OPERADOR]] e [[10-MINHA-EQUIPE]].

## Comunidade funcional em 2026-07-28

[[14-COMUNIDADE]] deixou de usar o protótipo fictício e passou a possuir rota pública `/comunidade`, feed real, publicação individual, criação, comentários encadeados, votos, salvos, compartilhamento, denúncias, perfil do autor e moderação. A migration aditiva `V16` cria o domínio persistente sem remover dados anteriores. A entrega permanece local e exige backup/preflight antes de aplicar a migration remotamente.
