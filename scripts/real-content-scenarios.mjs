import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const app = await readFile('src/App.tsx', 'utf8')

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
  'Exibindo somente dados reais',
  'Nenhum dado demonstrativo ou gerado automaticamente é exibido.',
  'Esta tela usa somente informações retornadas pela sessão autenticada.',
]) {
  assert.equal(app.includes(required), true, `Estado real ausente: ${required}`)
}

assert.equal(/const\s+(operations|ranking|classifiedListings|operationCatalog|teamMembers|rankingOperators)\s*=/.test(app), false)
assert.equal(app.includes('localStorage'), false)
assert.equal(app.includes('sessionStorage'), false)

console.log('Aplicação oficial validada sem registros fictícios ou persistência simulada.')
