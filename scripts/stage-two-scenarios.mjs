import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [api, app, operatorPage, operatorSearch, operatorLabel, teamPage, operationsPage, operationCommandCenter, security] = await Promise.all([
  readFile(new URL('../src/api.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/App.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/OperatorPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/OperatorSearch.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/operator-label.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/TeamPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/OperationsPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/OperationCommandCenter.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../services/api/src/main/java/br/com/operadorzero/shared/config/SecurityConfig.java', import.meta.url), 'utf8'),
])

for (const route of ['/api/operators/me', '/api/operators/search', '/api/teams/workspace', '/invitations', '/captaincy']) {
  assert.ok(api.includes(route), `Contrato ausente no cliente: ${route}`)
}
assert.ok(app.includes('<OperatorPage') && app.includes('<TeamPage') && app.includes('<OperatorSearch'))
assert.ok(operatorPage.includes('getOperatorProfile') && operatorPage.includes('updateOperatorProfile'))
assert.ok(operatorSearch.includes('searchOperators') && operatorSearch.includes('350'))
assert.ok(operatorLabel.includes("operator.teamAcronym?.trim() || 'SEM TIME'"))
assert.ok(!operatorLabel.includes("operator.teamName?.trim() || 'SEM TIME'"))
assert.ok(teamPage.includes('getTeamWorkspace') && teamPage.includes('acceptTeamInvitation'))
assert.ok(api.includes('uploadTeamLogo') && api.includes('body instanceof FormData'))
assert.ok(teamPage.includes('image/png,image/jpeg') && teamPage.includes('2048 × 2048'))
assert.ok(operationsPage.includes('Editar operação') && operationsPage.includes('operationEditMode'))
assert.ok(operationsPage.includes('selected.managedByCurrentUser && operationEditMode'))
assert.ok(operationCommandCenter.includes('editMode&&structure.managedByCurrentUser'))
assert.ok(!operationCommandCenter.includes('chatOpen') && !operationCommandCenter.includes('Abrir comunicação'))
assert.ok(operationCommandCenter.includes('Boolean(chat?.locked)&&!structure.managedByCurrentUser'))
assert.ok(security.includes('"/api/operators/**", "/api/teams/**"') && security.includes('.anyRequest().denyAll()'))

const functionalSource = [api, operatorPage, operatorSearch, teamPage].join('\n')
assert.ok(!/localStorage|sessionStorage|IndexedDB/.test(functionalSource), 'Módulos funcionais não devem persistir sessão ou perfil no navegador')
assert.ok(!/Promise\.resolve\(|mockOperators|fakeTeam|demoTeam/.test(functionalSource), 'Módulos funcionais não devem usar respostas demonstrativas')

console.log('Cenários da etapa 2 validados: perfil, busca, equipes, convites e contratos protegidos.')
