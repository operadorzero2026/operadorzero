const configuredApiUrl = import.meta.env.VITE_API_URL?.trim()

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

async function request(path: string, body: Record<string, unknown>) {
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    throw new Error(response.status === 401 ? 'E-mail ou senha incorretos.' : 'Nao foi possivel concluir a solicitacao.')
  }
}

export function login(email: string, password: string) {
  return request('/api/auth/login', { email, password })
}

export function register(displayName: string, email: string, password: string) {
  return request('/api/auth/register', { displayName, email, password })
}

export function requestPasswordRecovery(email: string) {
  return request('/api/auth/password-recovery', { email })
}

export function getGoogleLoginUrl() {
  return `${getApiBaseUrl()}/oauth2/authorization/google`
}
