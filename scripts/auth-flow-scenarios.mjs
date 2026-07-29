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
  'AUTH_BOOT_TIMEOUT_MS = 120000',
  'AUTH_MUTATION_TIMEOUT_MS = 45000',
  'CSRF_FRESHNESS_MS = 5 * 60 * 1000',
  'Date.now() - csrfPreparedAt < CSRF_FRESHNESS_MS',
  'csrfRequest: Promise<void> | null',
  'prepareAuthentication',
  'markAuthenticatedAreaUsable',
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

if (!app.includes('Preparando conexão segura...') || !app.includes('Conectando com segurança...')) {
  throw new Error('Cold start deve ter estado visual sem congelar o formulario.')
}

for (const removedSocialAuth of ['prepareGoogleLogin', 'Continuar com Google', 'Conectando ao Google', '/api/auth/google/intent']) {
  if (api.includes(removedSocialAuth) || app.includes(removedSocialAuth)) {
    throw new Error(`Autenticacao social ainda presente: ${removedSocialAuth}`)
  }
}

for (const passwordConfirmationContract of ['passwordConfirmation', 'Confirmar senha', 'As senhas devem ser iguais.']) {
  if (!api.includes(passwordConfirmationContract) && !app.includes(passwordConfirmationContract)) {
    throw new Error(`Confirmacao de senha ausente: ${passwordConfirmationContract}`)
  }
}

if (app.includes('logout().finally(() => setCurrentUser(null))')) {
  throw new Error('Interface nao pode confirmar logout quando a revogacao falha.')
}

if (!api.includes('if (error instanceof ApiTimeoutError) return null')) {
  throw new Error('Timeout do bootstrap deve continuar silencioso e sem simular sessao.')
}

for (const csrfRecoveryContract of [
  'response.status === 403',
  "error.code !== 'ACCESS_DENIED'",
  'csrfToken = null',
  'response = await send()',
]) {
  if (!api.includes(csrfRecoveryContract)) throw new Error(`Recuperacao de CSRF ausente: ${csrfRecoveryContract}`)
}

for (const emailConfirmationContract of [
  'resendVerification,',
  'setVerificationEmail(email)',
  'Reenviar e-mail de confirmação',
  'Confira também o Lixo Eletrônico',
]) {
  if (!app.includes(emailConfirmationContract)) throw new Error(`Reenvio de confirmacao ausente: ${emailConfirmationContract}`)
}

console.log('Fluxos frontend de sessao, CSRF, verificacao e recuperacao validados.')
