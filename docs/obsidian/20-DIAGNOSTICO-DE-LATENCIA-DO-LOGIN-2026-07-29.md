# Diagnóstico de latência do login — 2026-07-29

Conecta-se a [[01-ESTADO-ATUAL-DO-PROJETO]], [[02-ARQUITETURA-E-STACK]], [[05-SEGURANCA-E-PRIVACIDADE]], [[06-FRONTEND-WEB]], [[16-ARQUITETURA-DE-PRODUCAO]] e [[99-HISTORICO-DE-ALTERACOES]].

## Fluxo real

```text
abrir formulário
→ GET /api/auth/csrf pelo proxy same-origin
→ preencher e enviar
→ POST /api/auth/login
→ CorrelationIdFilter e Spring Security/CSRF
→ rate limit Redis
→ usuário + papéis no PostgreSQL
→ Argon2id matches
→ validação de status/e-mail
→ sessão e auditoria no PostgreSQL
→ cookie OZ_SESSION HttpOnly
→ resposta com projeção mínima do usuário
→ Dashboard renderizado
→ perfil, operações, total de equipes e ranking em paralelo, sem bloquear a entrada
```

Não existe uma segunda chamada de sessão obrigatória após login bem-sucedido. A restauração por `GET /api/auth/session` ocorre somente no carregamento inicial da SPA.

## Linha de base remota aquecida

Medição segura em 2026-07-29, sem conta real e sem registrar credenciais:

- readiness direta no Render, cinco amostras: 213–288 ms; média aproximada 248 ms.
- readiness pelo domínio oficial/Vercel, cinco amostras: 350–818 ms; média aproximada 567 ms.
- acréscimo observado do proxy/rede: aproximadamente 140–570 ms por amostra.
- CSRF direto: 240–488 ms; login inválido direto, incluindo Argon2 e rate limit: 458–2.070 ms.
- CSRF pelo domínio oficial: 348–617 ms; login inválido pelo proxy: 766–994 ms.
- tentativa aquecida medida no navegador oficial: mensagem final em 676 ms.

O serviço estava aquecido durante esta coleta. O Render Free pode hibernar; registros anteriores mostraram retomada próxima de 115 segundos. Portanto, esta coleta não representa o primeiro acesso após hibernação.

## Causas comprovadas

1. O plano gratuito do Render possui cold start muito superior à latência aquecida e não oferece disponibilidade contínua.
2. No primeiro envio de uma mutação, o frontend fazia CSRF e login de forma serial somente depois do clique.
3. O rate limit executava dois scripts Redis sequenciais para IP e sujeito+IP.
4. O proxy acrescenta latência mensurável, mas não explica sozinho esperas longas.

O índice funcional `uq_app_user_email_ci` já cobre `lower(email)`; não foi criado índice duplicado. O dashboard já carrega quatro consultas independentes em paralelo e não impede sua primeira renderização.

## Correções

- CSRF é preparado ao abrir o formulário, com uma única promessa compartilhada por aberturas/envios concorrentes.
- A preparação tolera o cold start documentado sem chamadas periódicas artificiais e exibe estado visual após 500 ms.
- O rate limit preserva as duas dimensões e seus TTLs, mas as atualiza atomicamente em um único round-trip Redis.
- `performance.mark/measure` registra somente durações locais de CSRF, login e tela utilizável.
- O backend registra `auth_login_duration` com correlação, resultado técnico e tempos de Redis, busca, Argon2, sessão, cookie, auditoria e total. Nenhum dado pessoal ou segredo entra no log.

## Limitações

- Uma medição de sucesso real e do primeiro acesso após hibernação depende de conta de homologação confirmada e janela controlada de inatividade.
- Métricas de região/CPU e `EXPLAIN ANALYZE` no banco remoto dependem de acesso operacional ao Render; nenhuma credencial foi solicitada ou exposta.
- Não reduzir o custo Argon2id: em teste local, `matches` ficou aproximadamente entre 23 e 47 ms; o custo não explica esperas de dezenas de segundos.
