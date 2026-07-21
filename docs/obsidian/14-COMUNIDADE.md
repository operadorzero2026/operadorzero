# Comunidade

Fórum comunitário do Operador Zero, conectado a [[13-MEU-OPERADOR]], [[10-MINHA-EQUIPE]], [[09-OPERACOES]], [[08-CLASSIFICADOS]] e [[12-CONQUISTAS-E-MEDALHAS]]. O protótipo atual é somente frontend e usa dados fictícios.

## Interface entregue

- Menu Comunidade, busca, 19 categorias iniciais, 13 filtros e feed responsivo.
- Publicações oficiais, comuns e sensíveis; votos apenas positivos; salvar, comentar, responder e denunciar.
- Aceite específico antes da primeira publicação, editor de Markdown representativo, prévia e análise demonstrativa de dados pessoais/linguagem inadequada.
- Regras da Comunidade, Minha atividade e painel de moderação marcado como demonstração RBAC/MFA.
- Busca global passa a incluir publicações da Comunidade.
- No mobile, categorias e metadados secundários deixam o feed principal; os cards exibem somente contexto essencial e ações secundárias ficam no detalhe.

## Modelo e migrations propostos

Entidades: `CommunityCategory`, `CommunityPost`, `CommunityPostRevision`, `CommunityComment`, `CommunityCommentRevision`, `CommunityVote`, `CommunityBookmark`, `CommunityFollow`, `CommunityMention`, `CommunityReport`, `CommunityModerationCase`, `CommunityModerationDecision`, `CommunityModerationAppeal`, `CommunityWordFilter`, `CommunityFilterVariation`, `CommunityAutomatedAnalysis`, `CommunityUserRestriction`, `CommunityUserWarning`, `CommunityUserBlock`, `CommunityUserMute`, `CommunityMedia`, `CommunityLinkAnalysis`, `CommunityTermsAcceptance` e `CommunityAuditLog`.

Migrations planejadas:

1. `V025__community_categories_posts_comments.sql`
2. `V026__community_votes_follows_mentions.sql`
3. `V027__community_reports_moderation_appeals.sql`
4. `V028__community_filters_automated_analysis.sql`
5. `V029__community_restrictions_blocks_mutes.sql`
6. `V030__community_media_links_terms_audit.sql`

Categorias e limites são administráveis no banco; não ficam exclusivamente no frontend. Constraints impedem voto duplicado e preservam revisão/decisão original. Auditoria é append-only.

## Endpoints propostos

- `GET|POST /api/v1/community/posts` e `GET|PATCH|DELETE /api/v1/community/posts/{id}`
- `POST /api/v1/community/posts/{id}/comments`, `/votes`, `/bookmarks`, `/reports`
- `PATCH|DELETE /api/v1/community/comments/{id}` e endpoints de resposta/voto/denúncia
- `GET /api/v1/community/categories`, `/feed`, `/search`, `/activity/me`
- `POST|DELETE /api/v1/community/users/{id}/block` e `/mute`
- `GET|POST /api/v1/community/appeals`
- `/api/v1/admin/community/*` para fila, decisão, filtros, restrições, evidências e métricas.

Todos exigem autenticação adequada, autorização por objeto, paginação, limites, idempotência, validação e auditoria. O frontend nunca define permissão ou selo oficial.

## Moderação

Pipeline: normalização segura -> detecção de dados/links/arquivos -> regras administráveis -> análise contextual -> decisão preventiva -> revisão humana -> decisão motivada -> recurso. Resultado automático é indício, não prova definitiva. Casos graves ficam bloqueados e priorizados; casos ambíguos permitem edição e revisão.

Escala: orientação, advertência, restrição temporária, suspensão e banimento. Ocorrências leves decaem; decisões anuladas não geram efeito. Pontuação de risco é privada e não altera ranking esportivo ou reputação pública.

## Segurança e privacidade

- Denunciante permanece confidencial; acesso administrativo é restrito e auditado.
- Conteúdo em análise/removido e usuários suspensos não entram no feed ou busca.
- Markdown sanitizado; sem HTML arbitrário, script, iframe ou conteúdo oculto.
- Upload somente JPEG/PNG/WebP validado, com limite, remoção EXIF, quarentena e varredura; vídeo apenas por domínios permitidos.
- Links normalizados e analisados; domínio é mostrado antes da saída.
- Bloqueio impede interação direta; silenciamento afeta somente quem silenciou.
- Limites iniciais: 5 posts/h, 30 comentários/h, 10 menções/comentário, 5 links/post, 10 imagens/post e 5 denúncias/h, todos configuráveis e adaptáveis ao risco.
- Proteção de menores permanece bloqueador jurídico antes da abertura para esse público.

## Separação esportiva

Votos, advertências e participação comunitária não alteram K/D, ranking, resultado de operação ou reputação esportiva. Conquistas comunitárias futuras não concedem pontos esportivos.

## Documentos conectados

- [[COMMUNITY-GUIDELINES]]
- [[COMMUNITY-MODERATION-POLICY]]
- [[COMMUNITY-APPEALS]]
- [[COMMUNITY-PROHIBITED-CONTENT]]
- [[COMMUNITY-AUTOMATED-MODERATION]]
- [[COMMUNITY-DATA-RETENTION]]
- [[COMMUNITY-INCIDENT-RESPONSE]]
- [[COMMUNITY-PRIVACY]]
- [[COMMUNITY-MODERATOR-GUIDE]]

## Pendências antes de produção

- Criar backend, migrations, storage, filas e testes de integração/segurança.
- Contratar/revisar ferramenta contextual e de mídia sem enviar dados além da finalidade.
- Fazer revisão jurídica brasileira, LGPD, Marco Civil, direitos autorais, proteção de menores e procedimento com autoridades.
- Revisar os termos com jurídico; os textos atuais são rascunhos, não parecer legal.
