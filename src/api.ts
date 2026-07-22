const configuredApiUrl = import.meta.env.VITE_API_URL?.trim()

export type SessionUser = {
  id: string
  email: string
  username: string
  displayName: string
  callsign: string
  roles: string[]
}

type ApiError = {
  code?: string
  message?: string
}

let csrfToken: string | null = null
let csrfHeader = 'X-CSRF-TOKEN'

function getApiBaseUrl() {
  if (!configuredApiUrl) {
    throw new Error('API indisponivel: configure VITE_API_URL.')
  }

  const url = new URL(configuredApiUrl)
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('VITE_API_URL deve usar HTTP ou HTTPS.')
  }

  return url.toString().replace(/\/$/, '')
}

async function readError(response: Response) {
  const fallback = response.status === 401 ? 'E-mail ou senha incorretos.' : 'Nao foi possivel concluir a solicitacao.'
  try {
    const error = await response.json() as ApiError
    return new Error(error.message || fallback)
  } catch {
    return new Error(fallback)
  }
}

async function ensureCsrf() {
  if (csrfToken) return
  const response = await fetch(`${getApiBaseUrl()}/api/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await readError(response)
  const result = await response.json() as { token: string; headerName: string }
  csrfToken = result.token
  csrfHeader = result.headerName
}

async function post<T>(path: string, body: Record<string, unknown>): Promise<T> {
  await ensureCsrf()
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [csrfHeader]: csrfToken!,
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) throw await readError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function login(email: string, password: string) {
  const session = await post<SessionUser>('/api/auth/login', { email, password })
  // Spring Security rotates the CSRF token after authentication. Force the
  // next state-changing request (for example, logout) to fetch the new token.
  csrfToken = null
  return session
}

export function register(displayName: string, email: string, password: string, termsAccepted: boolean) {
  return post<{ message: string }>('/api/auth/register', { displayName, email, password, termsAccepted })
}

export function requestPasswordRecovery(email: string) {
  return post<{ message: string }>('/api/auth/password-recovery', { email })
}

export function resendVerification(email: string) {
  return post<{ message: string }>('/api/auth/resend-verification', { email })
}

export function verifyEmail(token: string) {
  return post<{ message: string }>('/api/auth/verify-email', { token })
}

export function resetPassword(token: string, password: string) {
  return post<{ message: string }>('/api/auth/password-reset', { token, password })
}

export async function prepareGoogleLogin(termsAccepted: boolean) {
  const result = await post<{ authorizationPath: string }>('/api/auth/google/intent', { termsAccepted })
  return `${getApiBaseUrl()}${result.authorizationPath}`
}

export async function getCurrentSession(): Promise<SessionUser | null> {
  const response = await fetch(`${getApiBaseUrl()}/api/auth/session`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (response.status === 204 || response.status === 401) return null
  if (!response.ok) throw await readError(response)
  return response.json() as Promise<SessionUser>
}

export async function logout() {
  await post<void>('/api/auth/logout', {})
  csrfToken = null
}
