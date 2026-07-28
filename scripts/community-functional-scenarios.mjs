import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [app, api, page, migration, controller, security] = await Promise.all([
  readFile('src/App.tsx', 'utf8'),
  readFile('src/api.ts', 'utf8'),
  readFile('src/CommunityPage.tsx', 'utf8'),
  readFile('services/api/src/main/resources/db/migration/V16__community_posts_comments_and_moderation.sql', 'utf8'),
  readFile('services/api/src/main/java/br/com/operadorzero/community/CommunityController.java', 'utf8'),
  readFile('services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java', 'utf8'),
])

assert.ok(app.includes("navigate('/comunidade')"), 'O card Comunidade deve navegar para /comunidade.')
assert.ok(app.includes('<CommunityPage'), 'A rota deve renderizar a página funcional.')
assert.ok(page.includes('getCommunityPosts') && page.includes('createCommunityPost'))
assert.ok(page.includes('createCommunityComment') && page.includes('parentCommentId'))
assert.ok(page.includes('toggleCommunityVote') && page.includes('toggleCommunityBookmark'))
assert.ok(page.includes('reportCommunityPost') && page.includes('reportCommunityComment'))
assert.ok(page.includes('getCommunityReports') && page.includes('moderateCommunityPost'))
assert.ok(!/const\s+posts\s*[:=]/.test(page), 'A Comunidade funcional não pode usar publicações fictícias.')

for (const contract of [
  '/api/community/categories',
  '/api/community/posts',
  '/comments',
  '/vote',
  '/bookmark',
  '/reports',
  '/api/admin/community',
]) {
  assert.ok(api.includes(contract) || controller.includes(contract), `Contrato ausente: ${contract}`)
}

for (const table of ['community_post', 'community_comment', 'community_vote', 'community_bookmark', 'community_report']) {
  assert.ok(migration.includes(table), `Tabela ausente: ${table}`)
}
assert.ok(migration.includes('PRIMARY KEY(post_id, user_id)'), 'O banco deve impedir votos duplicados.')
assert.ok(migration.includes('uq_community_post_author_idempotency'), 'O banco deve impedir publicação duplicada.')
assert.ok(security.includes('HttpMethod.GET, "/api/community/**"') && security.includes('"/api/community/**").authenticated()'))
assert.ok(security.includes('"/api/admin/community/**").hasAnyRole("ADMIN", "MODERATOR")'))

console.log('Comunidade validada: rota, persistência, interações autenticadas e moderação.')
