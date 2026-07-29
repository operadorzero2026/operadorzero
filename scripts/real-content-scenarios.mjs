import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [app, operations] = await Promise.all([
  readFile('src/App.tsx', 'utf8'),
  readFile('src/OperationsPage.tsx', 'utf8'),
])

const heroLogin = app.indexOf("onClick={() => setAuthMode('login')}>Entrar <ArrowRight")
const heroSignup = app.indexOf("className=\"text-button link-button\" onClick={() => setAuthMode('signup')}>Criar perfil gratuito")
if (heroLogin < 0 || heroSignup < 0 || heroLogin > heroSignup) {
  throw new Error('A primeira dobra deve priorizar Entrar e manter Criar perfil gratuito como segunda opção.')
}

const fictionalContent = [
  'Operação Linha de Frente',
  'Cerco ao Distrito 7',
  'Missão Vale Sombrio',
  'NOMAD',
  'RAVEN',
  'ATLAS',
  'GHOST',
  'Valkyrie Ops',
  'Echo Squad',
  'Sentinelas PR',
  'Campo Bravo Zero',
]

for (const content of fictionalContent) {
  assert.equal(app.includes(content), false, `Conteúdo fictício ainda presente na aplicação oficial: ${content}`)
}

for (const required of [
  'Nenhuma operação publicada ainda',
  'Ainda não existem publicações nesta categoria',
  'Você ainda não participa de uma equipe',
  'Nenhum item encontrado',
]) {
  assert.equal(app.includes(required), true, `Estado vazio ausente: ${required}`)
}

for (const technicalNotice of [
  'CONTEÚDO REAL',
  'Exibindo somente dados reais',
  'Este painel não utiliza dados fictícios',
  'Nenhum dado demonstrativo',
  'Autenticada pela API',
  'persistidos e vinculados',
  'persistência, autorização e auditoria no backend',
  'VITE_API_URL',
]) {
  assert.equal(app.includes(technicalNotice), false, `Mensagem técnica exposta na interface: ${technicalNotice}`)
}

assert.equal(/const\s+(operations|ranking|classifiedListings|operationCatalog|teamMembers|rankingOperators)\s*=/.test(app), false)
assert.equal(app.includes('localStorage'), false)
assert.equal(app.includes('sessionStorage'), false)
assert.equal(operations.includes('Promise.allSettled'), true, 'O detalhe da operação deve tolerar falha parcial das consultas.')
assert.equal(operations.includes('Tentar novamente'), true, 'Falhas no detalhe da operação devem permitir nova tentativa.')

console.log('Aplicação oficial validada sem registros fictícios ou persistência simulada.')
