import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const app = await readFile('src/App.tsx', 'utf8')
const highlights = await readFile('src/CommunityHighlights.tsx', 'utf8')
const packageJson = await readFile('package.json', 'utf8')

const blockedOrganizerInfo = /\b(?:pix|pagamento|pagar|cobrança|cobrar|boleto|cartão|transferência|depósito|banco|agência|conta bancária|chave|checkout|reembolso|mercado pago|infinitepay|asaas|pagar\.me|pagbank|stripe|paypal)\b|(?:https?:\/\/|www\.)|(?:r\$|brl)\s*\d|qr\s*code/i
const freeStatuses = ['Solicitada','Aguardando aprovação','Aprovada','Lista de espera','Confirmada','Check-in realizado','Participou','Ausente','Cancelada','Removida']
const cases = [
  ['operações não possuem propriedade price', () => assert.doesNotMatch(app, /type OperationItem[^\n]+price/)],
  ['cards informam inscrição gratuita', () => assert.match(app, /Inscrição gratuita pela plataforma/)],
  ['assistente não oferece participação paga', () => assert.doesNotMatch(app, /<option>Paga<\/option>/)],
  ['informação logística é aceita', () => assert.equal(blockedOrganizerInfo.test('Levar água e chegar 30 minutos antes.'), false)],
  ['Pix é bloqueado nas informações', () => assert.equal(blockedOrganizerInfo.test('Minha chave Pix é exemplo'), true)],
  ['link é bloqueado nas informações', () => assert.equal(blockedOrganizerInfo.test('Acesse https://exemplo.test'), true)],
  ['valor é bloqueado nas informações', () => assert.equal(blockedOrganizerInfo.test('Custa R$ 90'), true)],
  ['status gratuitos previstos são únicos', () => assert.equal(new Set(freeStatuses).size, 10)],
  ['destaques usam seleção gratuita', () => assert.match(highlights, /SELEÇÃO GRATUITA/)],
  ['ordenação usa posição e relevância', () => assert.match(highlights, /a\.position - b\.position.*b\.relevance - a\.relevance/)],
  ['gestão registra responsável e motivo', () => { assert.match(highlights, /selectedBy/); assert.match(highlights, /reason/) }],
  ['destaque pode ser pausado e encerrado', () => { assert.match(highlights, /Pausado/); assert.match(highlights, /Encerrado/) }],
  ['classificados preservam preço informativo', () => assert.match(app, /type ClassifiedListing[\s\S]*?price: number/)],
  ['nenhum menu financeiro permanece', () => assert.doesNotMatch(app, /label: 'Financeiro'/)],
  ['nenhum teste financeiro permanece', () => assert.doesNotMatch(packageJson, /test:financial/)],
]

for (const [name, test] of cases) { test(); console.log(`✓ ${name}`) }
console.log(`\n${cases.length} cenários da plataforma gratuita validados.`)
