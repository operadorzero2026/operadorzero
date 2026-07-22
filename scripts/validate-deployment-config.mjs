import { readFile } from 'node:fs/promises'
import { parseDocument } from 'yaml'

const renderSource = await readFile('render.yaml', 'utf8')
const renderDocument = parseDocument(renderSource)
if (renderDocument.errors.length > 0) {
  throw new Error(`render.yaml invalido: ${renderDocument.errors[0].message}`)
}

const renderConfig = renderDocument.toJS()
if (!renderConfig.services?.some(service => service.type === 'web' && service.healthCheckPath === '/actuator/health')) {
  throw new Error('render.yaml deve declarar a API web com health check.')
}
if (!renderConfig.databases?.length) {
  throw new Error('render.yaml deve declarar o PostgreSQL de staging.')
}

const vercelConfig = JSON.parse(await readFile('vercel.json', 'utf8'))
if (vercelConfig.framework !== 'vite' || vercelConfig.outputDirectory !== 'dist') {
  throw new Error('vercel.json deve gerar a SPA Vite em dist.')
}
if (!vercelConfig.rewrites?.some(rule => rule.destination === '/index.html')) {
  throw new Error('vercel.json deve preservar deep links da SPA.')
}

console.log('Configuracoes de Render e Vercel validadas.')
