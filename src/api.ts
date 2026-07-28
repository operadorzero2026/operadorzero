const configuredApiUrl = import.meta.env.VITE_API_URL?.trim() || window.location.origin

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
const AUTH_REQUEST_TIMEOUT_MS = 15000

class ApiTimeoutError extends Error {
  constructor() {
    super('A conexao esta demorando para iniciar. Aguarde alguns segundos e tente novamente.')
    this.name = 'ApiTimeoutError'
  }
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit = {}, timeoutMs = AUTH_REQUEST_TIMEOUT_MS) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (isAbortError(error)) throw new ApiTimeoutError()
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}

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
  const response = await fetchWithTimeout(`${getApiBaseUrl()}/api/auth/csrf`, {
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
  const response = await fetchWithTimeout(`${getApiBaseUrl()}${path}`, {
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

export async function getCurrentSession(timeoutMs = 7000): Promise<SessionUser | null> {
  try {
    const response = await fetchWithTimeout(`${getApiBaseUrl()}/api/auth/session`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    }, timeoutMs)
    if (response.status === 204 || response.status === 401) return null
    if (!response.ok) throw await readError(response)
    return response.json() as Promise<SessionUser>
  } catch (error) {
    if (error instanceof ApiTimeoutError) return null
    throw error
  }
}

export async function logout() {
  await post<void>('/api/auth/logout', {})
  csrfToken = null
}
