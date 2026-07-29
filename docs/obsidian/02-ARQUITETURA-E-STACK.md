# Arquitetura e stack

## Decisao de producao - 2026-07-21

O backend principal sera um monolito modular Java 21/Spring Boot, sem um segundo backend principal em NestJS. PostgreSQL/Flyway sao a fonte de verdade; Redis atende estado efemero. Arquivos e e-mail entram por adaptadores. A SPA atual sera migrada modulo a modulo. Ver [[16-ARQUITETURA-DE-PRODUCAO]].

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

O cadastro e o login usam exclusivamente e-mail e senha. A confirmação do endereço é obrigatória antes da primeira sessão. Recuperação de senha ocorre por e-mail com token opaco, de uso único, armazenado somente como hash, com expiração curta e resposta não enumerável. O frontend não armazena senha nem token de recuperação.

Implementação atual: a API usa sessão opaca persistida no PostgreSQL e enviada em cookie `HttpOnly`, em vez de JWT no navegador. CSRF usa cookie/header dedicado e Redis aplica limites por IP e sujeito. Confirmação e recuperação usam a API HTTPS da Resend com idempotência; a chave fica somente no backend. Consulte [[05-SEGURANCA-E-PRIVACIDADE]] e `AUTHENTICATION.md`.

## Limites de módulo

Identity & Access, Operators, Teams, Fields & Maps, Operations, Performance Validation, Rankings, Championships, Achievements, Classifieds, Community, Community Highlights, Moderation, Notifications e Administration. Integrações ocorrem por serviços de aplicação/eventos; módulos não acessam tabelas alheias diretamente. A curadoria gratuita segue [[15-DESTAQUES-DA-COMUNIDADE]].

## Ambientes

Desenvolvimento, homologação temporária e produção usam bancos, URLs, buckets e segredos distintos. A preparação atual usa Vercel para a SPA e um Blueprint gratuito do Render somente para staging da API, PostgreSQL e estado efêmero. Produção, custos, backup e object storage ainda dependem de decisão explícita. Veja [[16-ARQUITETURA-DE-PRODUCAO]].

Conecta-se a [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]], [[05-SEGURANCA-E-PRIVACIDADE]] e `DEPLOYMENT-SECURITY.md`.

## Identidade endurecida em 2026-07-27

Tokens, sessões e pseudônimos técnicos usam HMAC-SHA-256 com `AUTH_HASH_KEY` exclusiva por ambiente. Rate limit combina IP e sujeito associado ao IP; o Redis usa `noeviction` para falhar fechado sem descartar silenciosamente estado vivo. A migration `V17` aposenta a autenticação social sem apagar usuários. Veja [[05-SEGURANCA-E-PRIVACIDADE]].
