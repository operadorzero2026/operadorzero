# Checklist de producao

- [ ] Dominio, DNS, TLS, CDN/WAF e headers revisados.
- [ ] Autenticacao real, verificacao, recuperacao, MFA admin e revogacao testadas.
- [ ] RBAC e autorizacao por objeto com testes negativos.
- [ ] PostgreSQL gerenciado, pool, migrations e indices revisados.
- [ ] Backup automatico e restauracao completa cronometrada.
- [ ] Redis com autenticacao/rede privada e politica de indisponibilidade.
- [ ] Upload privado, quarentena, antivirus, quota e remocao.
- [ ] E-mail com SPF, DKIM, DMARC, bounce e templates.
- [ ] Mercado Pago homologado, webhook idempotente e reconciliacao.
- [ ] Moderacao, denuncia, recurso, bloqueio e retencao operacionais.
- [ ] Politica de privacidade, termos, consentimentos e canal LGPD aprovados.
- [ ] Logs/metricas/traces, alertas, on-call e runbooks ativos.
- [ ] Rate limits, CSP/CORS/CSRF, secrets scan e dependency scan validados.
- [ ] Testes unitarios, integracao, E2E, carga e seguranca aprovados.
- [ ] Staging equivalente, CI/CD, rollback e resposta a incidentes exercitados.
- [ ] Sem dados/testes/credenciais ficticias privilegiadas no ambiente final.

