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

O cadastro e o login suportarão Google via OAuth 2.0/OIDC com Authorization Code + PKCE e credenciais próprias por e-mail e senha. Contas do mesmo e-mail exigem vinculação autenticada, nunca fusão silenciosa. Recuperação de senha ocorre por e-mail com token opaco, de uso único, armazenado somente como hash, com expiração curta e resposta não enumerável. O frontend não armazena senha nem token de recuperação.

## Limites de módulo

Identity & Access, Operators, Teams, Fields & Maps, Operations, Performance Validation, Rankings, Championships, Achievements, Classifieds, Community, Finance & Advertising, Moderation, Notifications e Administration. Integrações ocorrem por serviços de aplicação/eventos; módulos não acessam tabelas alheias diretamente. O financeiro segue [[15-FINANCEIRO-E-PUBLICIDADE]].

## Ambientes

Desenvolvimento, homologação temporária e produção usam bancos, URLs, buckets e segredos distintos. A escolha de hospedagem será comparada no momento do deploy; nenhum fornecedor foi selecionado nesta fase.

Conecta-se a [[03-DOMINIO-E-REGRAS-DE-NEGOCIO]], [[05-SEGURANCA-E-PRIVACIDADE]] e `DEPLOYMENT-SECURITY.md`.
