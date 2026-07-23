# Roadmap MVP

## Plano de migracao para producao - 2026-07-21

1. Fundacao de API, banco, Redis, migrations, health e logs - iniciada.
2. Identidade real, Google OIDC, recuperacao, sessoes, MFA admin e RBAC - identidade base concluida; provedores externos, MFA admin e autorizacoes de negocio pendentes.
3. Operador, equipes e operacoes persistentes.
4. Ranking e conquistas auditaveis.
5. Classificados, comunidade, chat, moderacao, denuncia e recurso.
6. Uploads privados com quarentena e antivirus.
7. Destaques gratuitos da comunidade com curadoria administrativa auditável.
8. CI/CD, carga, seguranca, restore, alertas e go-live controlado.

Os gates estao em [[16-ARQUITETURA-DE-PRODUCAO]].

- [x] Protótipo responsivo de [[13-MEU-OPERADOR]] e busca global com dados fictícios.
- [ ] Backend, migrations, política de visibilidade, autenticação forte, mídia segura e busca indexada de [[13-MEU-OPERADOR]].
- [x] Protótipo responsivo e documentação operacional de [[14-COMUNIDADE]].
- [ ] Backend, migrations, moderação contextual, storage seguro, recursos, retenção e revisão jurídica da Comunidade.
- [x] Protótipo de [[15-DESTAQUES-DA-COMUNIDADE]] sem monetização.
- [ ] Persistir curadoria gratuita com RBAC administrativo e auditoria.

## Ranking

- [x] Protótipo responsivo conectado ao painel.
- [x] Motor demonstrativo com 18 cenários automatizados.
- [ ] Implementar entidades, migrações e endpoints de [[11-RANKING]].
- [ ] Executar testes de integração, concorrência, autorização e antifraude no backend.

## Conquistas e Medalhas

- [x] Catálogo demonstrativo com 100 conquistas e tela responsiva.
- [x] Perfil, filtros, detalhes, destaques, compartilhamento e administração visual.
- [x] Motor demonstrativo com 20 cenários automatizados.
- [ ] Implementar banco, API, consumidor de eventos, auditoria e RBAC de [[12-CONQUISTAS-E-MEDALHAS]].

## Marco 0 — fundação (atual)

Documentação, políticas, identidade, landing e decisões arquiteturais.

## Marco 1 — identidade

Schema, migrations, cadastro adulto, e-mail, login protegido, sessão, recuperação, perfil e privacidade. Testes de autorização e rate limit entram junto.

Estado em 2026-07-23: cadastro/login/verificacao/recuperacao/sessao/logout e Google OIDC foram implementados. A chave Resend e as credenciais Google de staging foram salvas somente no Render. O estado temporario do OIDC passou a ser persistido no Redis; Google, confirmacao e recuperacao por e-mail passaram no E2E externo. Faltam DMARC/bounces, remover o segredo Google anterior, implementar MFA administrativo, central de sessoes, politicas juridicas e infraestrutura sem expiracao antes de concluir o marco.

## Marco 2 — comunidade

Implementar o backend de [[10-MINHA-EQUIPE]] para equipes, convites, histórico, preferências, campos, papéis e aprovação. Upload seguro, autorização e auditoria entram junto.

## Marco 3 — operações

Implementar o backend de [[09-OPERACOES]] para transformar o protótipo atual em agenda, inscrição, espera, regras versionadas, documentos protegidos, missões, times/esquadrões, check-in, encerramento, auditoria e notificações reais.

## Marco 4 — confiança esportiva

Desempenho, confirmações, contestações, reputação, fórmula versionada e ranking incremental.

## Marco 5 — competições

Conquistas e campeonato básico. Publicação somente após checklist de segurança, privacidade e restauração.

## Marco 6 — Classificados controlados

Executar o plano de [[08-CLASSIFICADOS]] somente depois da fundação real de identidade, auditoria, notificações e uploads. O beta gratuito começa com itens usados de categorias não sensíveis; marcadores, réplicas e possíveis PCE permanecem bloqueados até aprovação jurídica e técnica.

Não selecionar hospedagem nem executar deploy sem comparação atual de pelo menos três opções e autorização do usuário.
