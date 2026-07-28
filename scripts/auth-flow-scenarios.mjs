import { readFile } from 'node:fs/promises'

const api = await readFile('src/api.ts', 'utf8')
const app = await readFile('src/App.tsx', 'utf8')

if (!api.includes("import.meta.env.VITE_API_URL?.trim() || window.location.origin")) {
  throw new Error('Producao deve usar a origem da pagina para manter cookies de autenticacao first-party.')
}

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
  'A conexão está demorando para iniciar.',
]) {
  if (!api.includes(required)) throw new Error(`Contrato de autenticacao ausente: ${required}`)
}

for (const forbidden of ['localStorage', 'sessionStorage', 'DEMO_PASSWORD', 'OperadorZero@2026']) {
  if (api.includes(forbidden) || app.includes(forbidden)) throw new Error(`Persistencia/credencial proibida no frontend: ${forbidden}`)
}

if (!app.includes('getCurrentSession()') || !app.includes("mode === 'reset'")) {
  throw new Error('Restauracao de sessao ou recuperacao de senha nao esta conectada a interface.')
}

if (app.includes('if (authChecking) return') || app.includes('Validando sessão segura...')) {
  throw new Error('Bootstrap de sessao nao deve bloquear a pagina publica.')
}

if (!app.includes('sessionRestoring') || !app.includes('Verificando acesso...')) {
  throw new Error('Restauracao nao bloqueante da sessao deve informar seu estado.')
}

for (const coldStartContract of [
  'AUTH_API_WAKE_TIMEOUT_MS = 180000',
  'AUTH_API_WAKE_ATTEMPT_MS = 30000',
  '/actuator/health/readiness',
  'waitForAuthenticationApi()',
  'O acesso está demorando mais que o esperado',
]) {
  if (!api.includes(coldStartContract)) throw new Error(`Cold start nao tratado: ${coldStartContract}`)
}

if (!app.includes('Conectando ao Google...')) {
  throw new Error('Inicio Google deve informar progresso.')
}

if (app.includes('logout().finally(() => setCurrentUser(null))')) {
  throw new Error('Interface nao pode confirmar logout quando a revogacao falha.')
}

if (!api.includes('if (error instanceof ApiTimeoutError) return null')) {
  throw new Error('Timeout do bootstrap deve continuar silencioso e sem simular sessao.')
}

if (!app.includes("oauthCode === 'TERMS_REQUIRED'") || !app.includes('Selecione Criar conta')) {
  throw new Error('Conta Google nova nao orienta o usuario a concluir cadastro e aceite.')
}

for (const googleRecoveryContract of [
  "pendingAction === 'google'",
  'Conectando ao Google...',
  "window.addEventListener('pageshow', resumeAfterGoogle)",
  'event.persisted',
  'O acesso com Google foi cancelado.',
]) {
  if (!app.includes(googleRecoveryContract)) throw new Error(`Retorno do Google pode congelar a tela: ${googleRecoveryContract}`)
}

for (const emailConfirmationContract of [
  'resendVerification,',
  'setVerificationEmail(email)',
  'Reenviar confirmação',
  'Confira também o Lixo Eletrônico',
]) {
  if (!app.includes(emailConfirmationContract)) throw new Error(`Reenvio de confirmacao ausente: ${emailConfirmationContract}`)
}

console.log('Fluxos frontend de sessao, CSRF, verificacao e recuperacao validados.')
