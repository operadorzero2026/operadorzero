# Diagnóstico e plano de produção — 2026-07-27

Esta nota registra o diagnóstico obrigatório anterior às correções solicitadas para o domínio oficial. Ela complementa [[02-ARQUITETURA-E-STACK]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]] e [[16-ARQUITETURA-DE-PRODUCAO]].

## Arquitetura encontrada

- Frontend React, Vite e TypeScript publicado pela Vercel em `https://operadorzero.com.br`.
- API Java 21 e Spring Boot publicada no Render.
- PostgreSQL para identidade, sessões opacas e auditoria.
- Redis para rate limiting e para a sessão temporária do handshake OIDC.
- Resend para confirmação de e-mail e recuperação de senha.
- Sessão da aplicação em cookie `HttpOnly`; proteção CSRF por cookie e cabeçalho.
- Módulos de negócio ainda não possuem APIs persistentes. A interface de produção exibe estados vazios e a identidade real da sessão, sem registros fictícios.

## Fluxo atual de autenticação

### E-mail e senha

O browser obtém CSRF, envia a credencial por HTTPS à API, a API valida a senha Argon2, cria uma sessão opaca no PostgreSQL e devolve somente o cookie seguro. A senha e o identificador de sessão não são gravados em Web Storage.

### Google

1. O frontend obtém CSRF e cria uma intenção em `/api/auth/google/intent`.
2. A API grava a intenção em cookie temporário e retorna `/oauth2/authorization/google`.
3. Spring Security inicia Authorization Code com PKCE, `state`, `nonce` e escopos `openid`, `email` e `profile`.
4. O callback real é recebido pela API Render em `/login/oauth2/code/google`.
5. A API valida o ID Token pelo provedor OIDC, vincula ou cria a identidade, cria a sessão interna e redireciona para o domínio oficial com um estado de sucesso ou erro sem token.
6. O frontend consulta `/api/auth/session` e passa a usar a identidade retornada pela API.

## Diagnóstico do ambiente publicado

### Crítico

- A página inteira é substituída por “Validando sessão segura” enquanto a consulta inicial aguarda a API. Em cold start ou indisponibilidade do Render, o usuário percebe o site como travado, embora exista um timeout.
- O primeiro clique em “Continuar com Google” aguarda CSRF e criação da intenção antes do redirecionamento. O texto genérico “Aguarde...” não explica o despertar do serviço nem oferece estado intermediário claro.

### Alto

- A interface de logout apaga o estado local mesmo quando a revogação da sessão falha. Em dispositivo compartilhado, a sessão pode reaparecer após recarregar.
- A configuração local aceita URL PostgreSQL sem exigir TLS. A conexão atual pode estar protegida pelo provedor, mas o código não impede uma URL futura em modo não obrigatório.

### Médio

- Login, cadastro, recuperação e reenvio possuem diferenças mensuráveis de trabalho entre contas existentes e inexistentes. As mensagens são neutras, mas ainda existe risco de enumeração por tempo.
- O limite por e-mail é consumido antes da validação da credencial; tentativas de terceiros podem bloquear temporariamente um endereço conhecido.
- O endpoint Spring `/oauth2/authorization/google` é público e pode ser chamado sem passar pela intenção rate limited, criando sessões temporárias no Redis.
- O domínio oficial entrega `noindex` no HTML e no cabeçalho. `robots.txt`, `sitemap.xml` e `manifest.webmanifest` retornam o fallback HTML da SPA.
- Rotas inexistentes retornam o HTML da SPA com status 200; ainda não há páginas públicas persistidas para eventos, equipes, campos ou perfis.

### Baixo

- O hero móvel consome quase toda a primeira tela em 320 px e deixa a ação principal abaixo da dobra.
- O rodapé possui poucos destinos e ainda não oferece páginas públicas completas de termos, privacidade, segurança e regras.
- A entrega de e-mail após commit não possui fila persistente de retry; é uma lacuna operacional, não uma falha de autorização.

## Causa provável do problema relatado

O redirecionamento inicial ao Google responde no domínio publicado e usa o callback cadastrado da API. O principal travamento reproduzível ocorre antes ou depois desse redirecionamento: a SPA bloqueia toda a página enquanto valida a sessão e o serviço gratuito do Render pode estar adormecido. O callback completo precisa continuar sendo validado com conta de teste, sem credenciais pessoais no repositório.

## Arquivos previstos

- `src/App.tsx`: bootstrap não bloqueante, estados do Google, logout confiável, conteúdo e navegação pública.
- `src/api.ts`: timeouts por finalidade, erro seguro, aquecimento controlado e contrato de logout.
- `src/styles.css`: responsividade, foco, mensagens e layout público.
- `index.html`, `vercel.json` e `public/*`: metadados, indexação, robots, sitemap e manifest.
- `services/api/src/main/java/...`: controles confirmados de autenticação e TLS, acompanhados por testes.
- `scripts/*` e testes Java: regressões do fluxo, bundle, deploy e segurança.
- Notas relacionadas deste vault e [[99-HISTORICO-DE-ALTERACOES]].

## Riscos

- Cookies entre Vercel e Render exigem CORS explícito, `credentials: include`, `Secure` e política SameSite compatível.
- Alterar o callback ou a origem oficial sem sincronizar Google Cloud e Render interrompe o OIDC.
- Uma regra de indexação incorreta pode publicar rotas privadas ou páginas vazias.
- Exigir TLS no PostgreSQL precisa preservar parâmetros válidos da URL fornecida pelo Render.
- Correções de timing e rate limiting podem aumentar custo computacional ou bloquear usuários se os limites forem agressivos.

## Estratégia de rollback

1. Manter alterações em commits pequenos e reversíveis.
2. Não executar migration destrutiva.
3. Preservar as variáveis e o callback atuais durante a correção.
4. Publicar primeiro a API compatível com o frontend anterior quando houver mudança de contrato.
5. Verificar readiness, CORS, CSRF, login e callback antes de promover o frontend.
6. Em regressão, reverter o deployment da Vercel e o serviço Render para os artefatos anteriores; sessões e usuários existentes permanecem intactos.

## Testes planejados

- `npm run check` e `mvn -B clean verify`.
- Testes de bootstrap lento/indisponível, clique duplo Google, erro recuperável e logout sem revogação.
- Testes de callback, conta nova, conta vinculada, conta inativa e termos no backend.
- Navegador real em desktop e larguras 320, 360, 375, 390 e 414 px.
- Verificação de console, rede, CORS, CSRF, cookies e atualização após login.
- Verificação de `robots.txt`, sitemap, manifest, canonical, Open Graph e JSON-LD.
- Busca por segredos e nova auditoria de segurança após as mudanças.
- Smoke test progressivo no domínio oficial somente depois da aprovação de build e testes.

## Ações manuais do proprietário

- Manter no Google Cloud apenas o callback exato da API e as origens oficiais necessárias; o segredo fica exclusivamente no Render.
- Confirmar a aplicação OAuth como externa e publicada, sem escopos além de `openid`, `email` e `profile`.
- Revogar o segredo Google anterior depois de confirmar que não existe consumidor remanescente.
- Migrar os recursos gratuitos e efêmeros para infraestrutura permanente antes de anunciar disponibilidade contínua a usuários reais.

## Correção de segurança concluída localmente

- Cadastro existente, recuperação e reenvio agora executam trabalho Argon2 equivalente antes da resposta neutra.
- O limite por sujeito foi associado ao IP de origem; um terceiro remoto não consome uma chave global da conta.
- `/oauth2/authorization/google` exige liberação de uso único criada por `POST /api/auth/google/intent`; acesso direto retorna `404` e não aloca pedido OIDC.
- Intenções Google consumidas ou expiradas são removidas, com índice aditivo na migration `V5`.
- Hashes deterministas foram substituídos por HMAC-SHA-256 com `AUTH_HASH_KEY` obrigatória no backend.
- O Blueprint Redis usa `noeviction`, evitando expulsão silenciosa de sessões temporárias e chaves de rate limit.
- `mvn -B clean verify` passou com 40 testes e `npm run check` passou integralmente.

Estas mudanças ainda não foram publicadas. Antes de deploy, o Render precisa receber uma `AUTH_HASH_KEY` aleatória e estável; a primeira ativação invalida sessões e links criados com o esquema de hash anterior. A migration `V5` foi validada estaticamente, mas não executada em PostgreSQL isolado porque Docker não está instalado nesta máquina.

## Correção do cold start publicada

Os logs do Render mostraram inicialização completa em aproximadamente 115 segundos, enquanto o frontend encerrava a preparação Google em 45 segundos. Não houve registro de falha OIDC no backend: o pedido era abandonado antes do redirecionamento.

O frontend passou a consultar `/actuator/health/readiness` com tentativas de 30 segundos dentro de uma janela total de três minutos. Somente depois da readiness ele obtém CSRF, cria o intent e redireciona para o Google. O deployment Vercel `dpl_HoRvhgGQnpD1mMauGiPLRwVK3Df1` ficou `READY` e atualizou `operadorzero.com.br`, `www.operadorzero.com.br` e o alias Vercel. O bundle oficial contém a sonda de readiness; domínio e API responderam `200`.

Esse deploy alterou somente o frontend. O pacote backend com `AUTH_HASH_KEY`, migration `V5` e demais correções de segurança continua local e ainda não foi aplicado ao Render.
