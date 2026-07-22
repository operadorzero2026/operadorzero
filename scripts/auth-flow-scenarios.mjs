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
]) {
  if (!api.includes(required)) throw new Error(`Contrato de autenticacao ausente: ${required}`)
}

for (const forbidden of ['localStorage', 'sessionStorage', 'DEMO_PASSWORD', 'OperadorZero@2026']) {
  if (api.includes(forbidden) || app.includes(forbidden)) throw new Error(`Persistencia/credencial proibida no frontend: ${forbidden}`)
}

if (!app.includes('getCurrentSession()') || !app.includes("mode === 'reset'")) {
  throw new Error('Restauracao de sessao ou recuperacao de senha nao esta conectada a interface.')
}

console.log('Fluxos frontend de sessao, CSRF, verificacao e recuperacao validados.')
