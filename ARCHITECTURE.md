# Arquitetura de producao - Operador Zero

Status em 2026-07-21: **fundacao da Fase 1 implementada; produto ainda nao esta pronto para producao**.

## Diagnostico executivo

| Capacidade | Estado atual | Destino | Bloqueio |
|---|---|---|---|
| Web React/Vite/TypeScript | Funcional como prototipo local | SPA servida por CDN | Integrar API real |
| API principal | Esqueleto Spring Boot criado | Monolito modular Java 21 | Implementar casos de uso |
| Dados | Cenarios locais/simulados | PostgreSQL + Flyway | Migrar modulo a modulo |
| Autenticacao e autorizacao | Simuladas no cliente | OIDC/JWT, sessao segura e RBAC no servidor | Critico |
| Cache e controles efemeros | Ausentes | Redis | Pendente |
| Arquivos | Sem pipeline confiavel | S3 compativel, quarentena e antivirus | Critico antes de uploads |
| E-mail | Ausente | Adaptador transacional | Pendente |
| Pagamentos | Simulados | Mercado Pago com webhook idempotente | Critico antes de cobrar |
| Observabilidade | Ausente | logs JSON, metricas, traces e alertas | Parcial na API |
| Entrega | Sem pipeline | CI/CD com gates, rollback e ambientes | Critico |

## Arquitetura alvo

Internet -> CDN/WAF/TLS -> SPA e proxy `/api` -> Spring Boot -> PostgreSQL. Redis atende rate limit, sessoes efemeras, locks e filas leves. Objetos passam por quarentena/validacao/antivirus antes do armazenamento S3. E-mail e Mercado Pago ficam atras de adaptadores. Logs, metricas, traces e auditoria seguem para destinos separados.

O backend sera um **monolito modular**, evitando dois backends principais e microservicos prematuros. Modulos: identidade, usuarios, operadores, equipes, operacoes, ranking, conquistas, classificados, comunidade, conversas, moderacao, denuncias/recursos, financeiro, publicidade, notificacoes, arquivos, pagamentos e auditoria. Cada modulo tera API, aplicacao, dominio e infraestrutura, sem acesso direto a tabelas de outro modulo.

## Tecnologia mantida e adicionada

- Mantida: React, Vite, TypeScript e componentes atuais.
- Adicionada: Java 21, Spring Boot 3.5, Spring Security, JPA, Bean Validation, PostgreSQL, Flyway, Redis, OpenAPI, Actuator/Prometheus e logs JSON.
- Planejada: provedor OIDC, S3 compativel, antivirus, e-mail, Mercado Pago, OpenTelemetry, WAF/CDN e gerenciador de segredos.

## Fases e migracoes

1. Fundacao: API, configuracao, migrations, health, logs e regras de seguranca. **Iniciada**.
2. Identidade: cadastro, Google/e-mail, verificacao, recuperacao, MFA administrativo, sessoes e RBAC.
3. Perfil/equipes/operacoes: trocar armazenamento local por APIs e importar dados validos.
4. Ranking/conquistas: eventos imutaveis, aprovacao e recalculo auditavel.
5. Classificados/comunidade/chat: moderacao, denuncia, recurso, retencao, busca e notificacoes.
6. Arquivos: upload direto assinado, quarentena, MIME real, limites, antivirus e remocao.
7. Financeiro/publicidade/pagamentos: ledger, reconciliacao e webhooks idempotentes.
8. Operacao: CI/CD, restauracao comprovada, SLOs, alertas, testes de carga e seguranca.

Migrations existentes: `V1` identidade/RBAC/auditoria e `V2` perfil/privacidade. As proximas devem ser aditivas, revisadas, testadas em copia anonima e acompanhadas de rollback operacional; nunca usar `ddl-auto=update`.

## Riscos e custos

Riscos principais: confiar no frontend, migracao de dados simulados incompletos, autorizacao por objeto, abuso da comunidade/chat, upload malicioso, duplicacao de webhook e custo crescente de midia/logs. Mitigacoes entram antes de habilitar cada modulo.

Estimativa apenas para planejamento, sem cotacao de fornecedor: piloto pequeno R$ 500-2.000/mes; operacao inicial redundante R$ 2.000-8.000/mes; crescimento depende sobretudo de banco, transferencia/armazenamento de imagens, observabilidade, e-mail e suporte. Taxas de pagamento sao variaveis por meio/prazo. A escolha do provedor exige carga, SLA, regiao, volume e cotacao atual.

## Decisoes obrigatorias antes do go-live

Ver `PRODUCTION-CHECKLIST.md`. Nenhum ambiente deve ser divulgado como producao enquanto autenticacao real, autorizacao no servidor, backups restaurados, gestao de segredos, uploads seguros, monitoramento, termos/privacidade e resposta a incidentes estiverem pendentes.

