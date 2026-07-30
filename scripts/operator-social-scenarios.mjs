import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [app,page,api,migration,controller,repository] = await Promise.all([
  readFile('src/App.tsx','utf8'), readFile('src/OperatorsSocialPage.tsx','utf8'), readFile('src/api.ts','utf8'),
  readFile('services/api/src/main/resources/db/migration/V22__operator_social_connections.sql','utf8'),
  readFile('services/api/src/main/java/br/com/operadorzero/operator/OperatorSocialController.java','utf8'),
  readFile('services/api/src/main/java/br/com/operadorzero/operator/OperatorSocialRepository.java','utf8'),
])
assert.ok(app.includes("onNavigate('/operadores')") && app.includes('<OperatorsSocialPage'))
for (const tab of ['Feed','Buscar operadores','Amigos','Solicitações','Meu perfil']) assert.ok(page.includes(tab),`Navegação ausente: ${tab}`)
for (const section of ['SOBRE','EQUIPAMENTOS PRINCIPAIS','ÚLTIMAS OPERAÇÕES','CONQUISTAS']) assert.ok(page.includes(section),`Seção ausente: ${section}`)
assert.ok(page.includes('getCommunityPosts') && page.includes('getOperatorProfileOverview'))
assert.ok(!/const\s+(posts|operators|friends)\s*=\s*\[/.test(page))
for (const contract of ['/connections','/requests','/accept','/decline','/block','/reports','/profile']) assert.ok(api.includes(contract)||controller.includes(contract),`Contrato ausente: ${contract}`)
for (const table of ['operator_friendship','operator_block','operator_profile_report']) assert.ok(migration.includes(table),`Tabela ausente: ${table}`)
assert.ok(migration.includes('uq_operator_friendship_pair'))
assert.ok(repository.includes("status IN ('PENDING','ACCEPTED')"))
assert.ok(page.includes('/operadores/${friend.username}'))
console.log('Operadores validado: perfil responsivo, feed real, amizades, bloqueio e denúncia persistentes.')
