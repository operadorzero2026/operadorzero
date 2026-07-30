# Comunidade

## Identificação de autores — 2026-07-30

Autores de publicações e comentários aparecem como `CALLSIGN - SIGLA` da equipe ativa ou `CALLSIGN - SEM TIME`.

Fórum funcional do Operador Zero para conteúdo exclusivamente relacionado ao airsoft. Conecta-se a [[13-MEU-OPERADOR]], [[10-MINHA-EQUIPE]], [[09-OPERACOES]], [[08-CLASSIFICADOS]], [[05-SEGURANCA-E-PRIVACIDADE]] e [[06-FRONTEND-WEB]].

## Estado funcional em 2026-07-28

- A rota pública `/comunidade` lista somente publicações persistidas no PostgreSQL; não há fixtures ou conteúdo fictício.
- Visitantes podem consultar feed, categorias, busca, ordenação, publicação individual, comentários e perfil comunitário mínimo do autor.
- Sessão real é obrigatória para criar publicação, enviar imagem, comentar, responder, votar, salvar, denunciar ou excluir conteúdo próprio.
- O frontend encaminha tentativas de interação sem sessão para o login já existente.
- Publicações possuem autor, avatar textual de fallback, título, descrição, categoria, data, votos, comentários, imagens opcionais, salvar, compartilhar e denunciar.
- A criação usa título, categoria, descrição, até quatro imagens e chave idempotente; o botão fica bloqueado durante o envio.
- Comentários são retornados em árvore por `parent_comment_id`, rejeitam texto vazio e respeitam o bloqueio da conversa.
- Voto e item salvo possuem unicidade `(post_id, user_id)` no banco.
- Autor e administradores/moderadores podem excluir conteúdo conforme autorização por objeto no backend.
- `ADMIN` e `MODERATOR` podem consultar denúncias, suspender/excluir publicações e comentários, bloquear comentários e resolver a fila.

## Banco e migration

`V16__community_posts_comments_and_moderation.sql` é aditiva e cria `community_category`, `community_post`, `community_post_media`, `community_comment`, `community_vote`, `community_bookmark` e `community_report`.

As categorias iniciais ficam no banco. Constraints cobrem título, descrição, status, alvo de denúncia, motivo, voto único, salvo único e idempotência de publicação/comentário. Nenhuma tabela ou identidade anterior é removida.

## API

Leitura pública:

- `GET /api/community/categories`
- `GET /api/community/posts`
- `GET /api/community/posts/{postId}`
- `GET /api/community/posts/{postId}/comments`
- `GET /api/community/authors/{username}`
- `GET /api/community/images/{imageId}`

Interações autenticadas:

- `POST /api/community/posts`
- `POST /api/community/posts/{postId}/images`
- `POST /api/community/posts/{postId}/comments`
- `POST /api/community/posts/{postId}/vote`
- `POST /api/community/posts/{postId}/bookmark`
- `POST /api/community/posts/{postId}/reports`
- `POST /api/community/comments/{commentId}/reports`
- `DELETE /api/community/posts/{postId}`
- `DELETE /api/community/comments/{commentId}`

Moderação:

- `GET /api/admin/community/reports`
- `PATCH /api/admin/community/posts/{postId}/status`
- `PATCH /api/admin/community/comments/{commentId}/status`
- `PATCH /api/admin/community/posts/{postId}/comments-lock`
- `PATCH /api/admin/community/reports/{reportId}`

## Segurança e privacidade

- Mutação usa sessão HttpOnly, CSRF, validação Bean Validation, SQL parametrizado e auditoria.
- Ordenação SQL é escolhida somente entre expressões fixas no service.
- O backend não confia no autor, papel ou proprietário enviados pelo navegador.
- Imagens aceitam apenas PNG/JPEG reais de até 2 MB, são decodificadas/reencodificadas e persistidas sem nome original.
- Publicações suportam até quatro imagens. Conteúdo é renderizado como texto, sem HTML arbitrário.
- Feed e busca excluem conteúdo suspenso/excluído e contas inativas.
- Denunciante não aparece no feed nem para o autor.
- O avatar comunitário usa a inicial do callsign/nome enquanto não existir consentimento específico para tornar a foto de perfil pública.

## Verificação e implantação

Testes cobrem serviço, migration, contratos frontend, rota, ausência de fixtures, voto único, idempotência e RBAC de moderação.

Em 2026-07-28, a CI aprovou frontend, backend, build Docker, configuração de deploy e varredura de segredos. O Render validou as 16 migrations e aplicou a V16 no PostgreSQL 17; o serviço retornou ao estado `live`. A Vercel publicou o mesmo commit em produção e associou `operadorzero.com.br`. Readiness, categorias, feed vazio real, rota pública, card autenticado e formulário de criação autenticado foram verificados pela origem oficial.

Veja [[99-HISTORICO-DE-ALTERACOES]].
