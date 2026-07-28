import { readFile } from 'node:fs/promises'
import { parseDocument } from 'yaml'

const fullGitSha = /^[0-9a-f]{40}$/i
const fullImageDigest = /@sha256:[0-9a-f]{64}(?:\s|$)/i

const renderSource = await readFile('render.yaml', 'utf8')
const renderDocument = parseDocument(renderSource)
if (renderDocument.errors.length > 0) {
  throw new Error(`render.yaml invalido: ${renderDocument.errors[0].message}`)
}

const renderConfig = renderDocument.toJS()
if (!renderConfig.services?.some(service => service.type === 'web' && service.healthCheckPath === '/actuator/health/readiness')) {
  throw new Error('render.yaml deve declarar a API web com readiness de banco e Redis.')
}
if (!renderConfig.databases?.length) {
  throw new Error('render.yaml deve declarar o PostgreSQL de staging.')
}
const apiService = renderConfig.services.find(service => service.type === 'web')
const apiEnvironmentKeys = new Set(apiService?.envVars?.map(variable => variable.key) ?? [])
for (const required of ['MAIL_ENABLED', 'MAIL_FROM', 'RESEND_API_KEY']) {
  if (!apiEnvironmentKeys.has(required)) {
    throw new Error(`render.yaml deve declarar ${required} somente no backend.`)
  }
}
for (const obsolete of ['MAIL_HOST', 'MAIL_PORT', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_SMTP_AUTH', 'MAIL_STARTTLS']) {
  if (apiEnvironmentKeys.has(obsolete)) {
    throw new Error(`render.yaml ainda declara a variavel SMTP obsoleta ${obsolete}.`)
  }
}

const vercelConfig = JSON.parse(await readFile('vercel.json', 'utf8'))
if (vercelConfig.framework !== 'vite' || vercelConfig.outputDirectory !== 'dist') {
  throw new Error('vercel.json deve gerar a SPA Vite em dist.')
}
if (!vercelConfig.rewrites?.some(rule => rule.destination === '/index.html')) {
  throw new Error('vercel.json deve preservar deep links da SPA.')
}
const spaFallbackIndex = vercelConfig.rewrites.findIndex(rule => rule.destination === '/index.html')
for (const source of ['/api/:path*', '/actuator/:path*', '/oauth2/:path*', '/login/oauth2/:path*']) {
  const proxyIndex = vercelConfig.rewrites.findIndex(rule => rule.source === source
    && rule.destination?.startsWith('https://operadorzero-api-staging.onrender.com/'))
  if (proxyIndex < 0 || proxyIndex > spaFallbackIndex) {
    throw new Error(`Proxy same-origin ausente ou posterior ao fallback da SPA: ${source}.`)
  }
}
const globalHeaders = vercelConfig.headers?.find(rule => rule.source === '/(.*)')?.headers ?? []
const csp = globalHeaders.find(header => header.key === 'Content-Security-Policy')?.value ?? ''
if (!csp.includes("connect-src 'self'") || csp.includes('connect-src \'self\' https://operadorzero-api-staging.onrender.com')) {
  throw new Error('CSP deve obrigar o browser a acessar autenticacao pelo proxy same-origin.')
}

const workflowSource = await readFile('.github/workflows/ci.yml', 'utf8')
for (const [index, line] of workflowSource.split(/\r?\n/).entries()) {
  const action = line.match(/^\s*-\s+uses:\s+([^\s#]+)/)?.[1]
  if (action && !action.startsWith('./')) {
    const revision = action.slice(action.lastIndexOf('@') + 1)
    if (!fullGitSha.test(revision)) {
      throw new Error(`Action sem SHA imutavel em .github/workflows/ci.yml:${index + 1}.`)
    }
  }

  if (/\bdocker\s+run\b/.test(line) && !fullImageDigest.test(line)) {
    throw new Error(`Imagem executavel sem digest em .github/workflows/ci.yml:${index + 1}.`)
  }
}

const dockerfileSource = await readFile('services/api/Dockerfile', 'utf8')
for (const [index, line] of dockerfileSource.split(/\r?\n/).entries()) {
  if (/^\s*FROM\s+/i.test(line) && !/^\s*FROM\s+scratch(?:\s|$)/i.test(line) && !fullImageDigest.test(line)) {
    throw new Error(`Imagem base sem digest em services/api/Dockerfile:${index + 1}.`)
  }
}

console.log('Configuracoes de Render, Vercel e referencias imutaveis validadas.')
