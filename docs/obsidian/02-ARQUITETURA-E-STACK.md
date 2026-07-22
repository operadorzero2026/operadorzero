# Arquitetura e stack

## Decisao de producao - 2026-07-21

O backend principal sera um monolito modular Java 21/Spring Boot, sem um segundo backend principal em NestJS. PostgreSQL/Flyway sao a fonte de verdade; Redis atende estado efemero. Arquivos, e-mail e pagamentos entram por adaptadores. A SPA atual sera migrada modulo a modulo. Ver [[16-ARQUITETURA-DE-PRODUCAO]].

## Decisão inicial

Monólito modular com frontend separado. É mais simples de operar em recursos limitados e preserva limites de domínio para futura extração.

```text
Browser/PWA futura -> CDN frontend -> API REST -> módulos de aplicação -> PostgreSQL
                                           |-> fila/jobs
                                           |-> object storage
                                           |-> e-mail
```

## Stack alvo

- Frontend: React, TypeScript, Vite, CSS responsivo; estado remoto por camada de queries na fase de API.
- Backend recomendado: Java 21 + Spring Boot, Spring Security, Bean Validation, Flyway e JPA.
- Dados: PostgreSQL, UUID público e chaves internas, UTC no banco.
- Arquivos: object storage compatível com S3, URLs assinadas e quarentena de upload.
- Observabilidade: logs estruturados sem dados sensíveis, métricas e trilha de auditoria separada.

## Identidade e acesso

O cadastro e o login suportam Google via OAuth 2.0/OIDC com Authorization Code + PKCE e credenciais próprias por e-mail e senha. Contas do mesmo e-mail exigem vinculação autenticada, nunca fusão silenciosa. Recuperação de senha ocorre por e-mail com token opaco, de uso único, armazenado somente como hash, com expiração curta e resposta não enumerável. O frontend não armazena senha nem token de recuperação.

Implementacao de 2026-07-22: a API usa sessao opaca persistida no PostgreSQL e enviada em cookie `HttpOnly`, em vez de JWT no navegador. CSRF usa cookie/header dedicado; Redis aplica limites por IP e sujeito. Google OIDC usa o cliente do Spring Security com PKCE e validacao de issuer/audience/nonce. Confirmacao e recuperacao usam a API HTTPS da Resend com idempotencia; a chave fica somente no backend. Resend e Google continuam configuracoes externas obrigatorias por ambiente. Consulte [[05-SEGURANCA-E-PRIVACIDADE]] e `AUTHENTICATION.md`.

## Limites de módulo

Identity & Access, Operators, Teams, Fields & Maps, Operations, Performance Validation, Rankings, Championships, Achievements, Classifieds, Community, Finance & Advertising, Moderation, Notifications e Administration. Integrações ocorrem por serviços de aplicação/eventos; módulos não acessam tabelas alheias diretamente. O financeiro segue [[15-FINANCEIRO-E-PUBLICIDADE]].

## Ambientes

Desenvolvimento, homologação temporária e produção usam bancos, URLs, buckets e segredos distintos. A preparação atual usa Vercel para a SPA e um Blueprint gratuito do Render somente para staging da API, PostgreSQL e estado efêmero. Produção, custos, backup e object storage ainda dependem de decisão explícita. Veja [[16-ARQUITETURA-DE-PRODUCAO]].

Conecta-se a [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]], [[05-SEGURANCA-E-PRIVACIDADE]] e `DEPLOYMENT-SECURITY.md`.
