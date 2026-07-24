import { readFile } from 'node:fs/promises'

const api = await readFile('src/api.ts', 'utf8')
const app = await readFile('src/App.tsx', 'utf8')

for (const required of [
  "credentials: 'include'",
  '/api/auth/csrf',
  '/api/auth/session',
  '/api/auth/logout',
  '/api/auth/verify-email',
  '/api/auth/password-reset',
  'new AbortController()',
  'controller.abort()',
  "error.name === 'AbortError'",
  'AUTH_REQUEST_TIMEOUT_MS = 15000',
  'fetchWithTimeout',
  'ApiTimeoutError',
  'A conexao esta demorando para iniciar.',
]) {
  if (!api.includes(required)) throw new Error(`Contrato de autenticacao ausente: ${required}`)
}

for (const forbidden of ['localStorage', 'sessionStorage', 'DEMO_PASSWORD', 'OperadorZero@2026']) {
  if (api.includes(forbidden) || app.includes(forbidden)) throw new Error(`Persistencia/credencial proibida no frontend: ${forbidden}`)
}

if (!app.includes('getCurrentSession()') || !app.includes("mode === 'reset'")) {
  throw new Error('Restauracao de sessao ou recuperacao de senha nao esta conectada a interface.')
}

if (!app.includes('Continuar no site')) {
  throw new Error('Bootstrap de sessao nao oferece saida manual quando a API demora.')
}

if (!api.includes('if (error instanceof ApiTimeoutError) return null')) {
  throw new Error('Timeout do bootstrap deve continuar silencioso e sem simular sessao.')
}

if (!app.includes("oauthCode === 'TERMS_REQUIRED'") || !app.includes('Selecione Criar conta')) {
  throw new Error('Conta Google nova nao orienta o usuario a concluir cadastro e aceite.')
}

console.log('Fluxos frontend de sessao, CSRF, verificacao e recuperacao validados.')
