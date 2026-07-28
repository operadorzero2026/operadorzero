// Production uses the Vercel same-origin proxy. Keeping the API on the page
// origin makes authentication cookies first-party; local development can
// still point directly at the API with VITE_API_URL.
const configuredApiUrl = import.meta.env.VITE_API_URL?.trim() || window.location.origin

export type SessionUser = {
  id: string
  email: string
  username: string
  displayName: string
  callsign: string
  roles: string[]
}

export type OperatorEquipment = {
  id: string
  category: string
  name: string
  details?: string | null
  condition: string
  visibility: string
  createdAt: string
}

export type OperatorProfile = {
  id: string
  email: string
  username: string
  displayName: string
  callsign: string
  bio?: string | null
  city?: string | null
  stateCode?: string | null
  preferredPosition?: string | null
  secondaryPositions: string[]
  recruitmentStatus: string
  privacy: Record<string, string>
  equipment: OperatorEquipment[]
  hasPhoto: boolean
  version: number
}

export type OperatorSummary = {
  id: string
  username: string
  displayName: string
  callsign: string
  city?: string | null
  stateCode?: string | null
  recruitmentStatus?: string | null
}

export type TeamMember = {
  operatorId: string
  username: string
  callsign: string
  displayName: string
  role: string
  joinedAt: string
}

export type Team = {
  id: string
  name: string
  acronym: string
  city: string
  stateCode: string
  gameStyle: string
  ownsField: boolean
  description?: string | null
  recruitmentStatus: string
  currentUserRole: string
  members: TeamMember[]
  hasLogo: boolean
  version: number
}

export type TeamInvitation = {
  id: string
  teamId: string
  teamName: string
  teamAcronym: string
  inviterCallsign: string
  proposedRole: string
  message?: string | null
  createdAt: string
  expiresAt: string
}

export type TeamWorkspace = { team: Team | null; receivedInvitations: TeamInvitation[] }

export type VenueField = { id:string; name:string; description?:string|null; city:string; stateCode:string; addressLine?:string|null; addressNumber?:string|null; district?:string|null; region?:string|null; locationUrl?:string|null; openingHours?:string|null; amenities?:string|null; maximumCapacity?:number|null; averagePrice?:number|null; paymentMethods?:string|null; managedByCurrentUser:boolean; version:number }
export type VenueMap = { id:string; fieldId:string; fieldName:string; city:string; stateCode:string; name:string; terrainType:string; description?:string|null; approximateSize?:string|null; capacity?:number|null; managedByCurrentUser:boolean; version:number }
export type Operation = { id:string; name:string; description:string; fieldId:string; fieldName:string; mapId?:string|null; mapName?:string|null; city:string; stateCode:string; operationDate:string; presentationTime:string; startTime:string; endTime:string; modality:string; status:string; participantLimit:number; participantCount:number; registrationPrice:number; participantStatus?:string|null }
export type RankingEntry = { position:number; operatorId:string; username:string; displayName:string; callsign:string; city?:string|null; stateCode?:string|null; teamId?:string|null; teamName?:string|null; teamAcronym?:string|null; gross:number; factor:number; bonus:number; penalties:number; finalScore:number; positionVariation:number; operationsConsidered:number }
export type PerformanceRecord = { id:string; operationId:string; operationName:string; operationDate:string; operatorId:string; operatorCallsign:string; representedTeamId?:string|null; representedTeamName?:string|null; eliminations:number; deaths:number; objectivesCompleted:number; roundWins:number; result:string; positionUsed?:string|null; notes?:string|null; highlightReceived?:string|null; penaltyPoints:number; abandoned:boolean; participationScope:string; status:string; organizerConfirmed:boolean; teamConfirmed:boolean; organizerNotes?:string|null; currentUserRecord:boolean; canReviewAsOrganizer:boolean; canReviewAsTeam:boolean; canContest:boolean; createdAt:string; updatedAt:string; version:number }

type ApiError = { code?: string; message?: string; correlationId?: string; fields?: Array<{ field: string; message: string }> }

const FIELD_LABELS: Record<string, string> = { acronym: 'Sigla' }

export class ApiClientError extends Error {
  code?: string
  correlationId?: string

  constructor(message: string, code?: string, correlationId?: string) {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.correlationId = correlationId
  }
}

let csrfToken: string | null = null
let csrfHeader = 'X-CSRF-TOKEN'
const AUTH_REQUEST_TIMEOUT_MS = 15000
const AUTH_API_WAKE_TIMEOUT_MS = 180000
const AUTH_API_WAKE_ATTEMPT_MS = 30000
const AUTH_API_READY_CACHE_MS = 30000
let apiReadyUntil = 0

class ApiTimeoutError extends Error {
  constructor(message = 'A conexão está demorando para iniciar. Aguarde alguns segundos e tente novamente.') {
    super(message)
    this.name = 'ApiTimeoutError'
  }
}

function wait(timeoutMs: number) {
  return new Promise<void>(resolve => window.setTimeout(resolve, timeoutMs))
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
  if (!configuredApiUrl) throw new Error('Não foi possível conectar ao serviço neste momento.')
  const url = new URL(configuredApiUrl)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Não foi possível conectar ao serviço neste momento.')
  return url.toString().replace(/\/$/, '')
}

async function waitForAuthenticationApi() {
  if (Date.now() < apiReadyUntil) return
  const deadline = Date.now() + AUTH_API_WAKE_TIMEOUT_MS
  while (Date.now() < deadline) {
    const remaining = deadline - Date.now()
    try {
      const response = await fetchWithTimeout(`${getApiBaseUrl()}/actuator/health/readiness`, {
        method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
      }, Math.min(AUTH_API_WAKE_ATTEMPT_MS, remaining))
      if (response.ok) {
        apiReadyUntil = Date.now() + AUTH_API_READY_CACHE_MS
        return
      }
    } catch (error) {
      if (!(error instanceof ApiTimeoutError) && !(error instanceof TypeError)) throw error
    }
    if (Date.now() < deadline) await wait(1500)
  }
  throw new ApiTimeoutError('O acesso está demorando mais que o esperado. Tente novamente em instantes.')
}

async function readError(response: Response) {
  const fallback = response.status === 401
    ? 'Sua sessão expirou. Entre novamente.'
    : 'Não foi possível concluir a solicitação.'
  try {
    const error = await response.json() as ApiError
    const fieldError = error.fields?.[0]
    const message = fieldError
      ? `${FIELD_LABELS[fieldError.field] || 'Campo'}: ${fieldError.message}`
      : error.message || fallback
    return new ApiClientError(message, error.code, error.correlationId)
  } catch {
    return new ApiClientError(fallback)
  }
}

async function ensureCsrf() {
  if (csrfToken) return
  const response = await fetchWithTimeout(`${getApiBaseUrl()}/api/auth/csrf`, {
    method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await readError(response)
  const result = await response.json() as { token: string; headerName: string }
  csrfToken = result.token
  csrfHeader = result.headerName
}

async function apiRequest<T>(path: string, init: RequestInit = {}, timeoutMs = AUTH_REQUEST_TIMEOUT_MS): Promise<T> {
  await waitForAuthenticationApi()
  const method = (init.method || 'GET').toUpperCase()
  const mutating = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  if (mutating) await ensureCsrf()
  const send = () => fetchWithTimeout(`${getApiBaseUrl()}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(init.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...(mutating ? { [csrfHeader]: csrfToken! } : {}),
        ...init.headers,
      },
    }, timeoutMs)
  let response = await send()
  if (mutating && response.status === 403) {
    const error = await readError(response)
    if (error.code !== 'ACCESS_DENIED') throw error
    csrfToken = null
    await ensureCsrf()
    response = await send()
  }
  if (!response.ok) throw await readError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function json(method: string, body?: unknown): RequestInit {
  return { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }
}

export async function login(email: string, password: string) {
  const session = await apiRequest<SessionUser>('/api/auth/login', json('POST', { email, password }))
  csrfToken = null
  return session
}

export const register = (displayName: string, email: string, password: string, termsAccepted: boolean) =>
  apiRequest<{ message: string }>('/api/auth/register', json('POST', { displayName, email, password, termsAccepted }))
export const requestPasswordRecovery = (email: string) => apiRequest<{ message: string }>('/api/auth/password-recovery', json('POST', { email }))
export const resendVerification = (email: string) => apiRequest<{ message: string }>('/api/auth/resend-verification', json('POST', { email }))
export const verifyEmail = (token: string) => apiRequest<{ message: string }>('/api/auth/verify-email', json('POST', { token }))
export const resetPassword = (token: string, password: string) => apiRequest<{ message: string }>('/api/auth/password-reset', json('POST', { token, password }))

export async function prepareGoogleLogin(termsAccepted: boolean) {
  const result = await apiRequest<{ authorizationPath: string }>('/api/auth/google/intent', json('POST', { termsAccepted }))
  return `${getApiBaseUrl()}${result.authorizationPath}`
}

export async function getCurrentSession(timeoutMs = 7000): Promise<SessionUser | null> {
  try {
    const response = await fetchWithTimeout(`${getApiBaseUrl()}/api/auth/session`, {
      method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
    }, timeoutMs)
    if ([204, 401].includes(response.status)) return null
    if (!response.ok) throw await readError(response)
    return response.json() as Promise<SessionUser>
  } catch (error) {
    if (error instanceof ApiTimeoutError) return null
    throw error
  }
}

export async function logout() {
  await apiRequest<void>('/api/auth/logout', json('POST', {}))
  csrfToken = null
}

export const getOperatorProfile = () => apiRequest<OperatorProfile>('/api/operators/me')
export const updateOperatorProfile = (profile: Omit<OperatorProfile, 'id' | 'email' | 'privacy' | 'equipment' | 'hasPhoto'>) =>
  apiRequest<OperatorProfile>('/api/operators/me', json('PATCH', profile))
export const uploadOperatorPhoto = (file: File) => {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<OperatorProfile>('/api/operators/me/photo', { method: 'POST', body })
}
export const operatorPhotoUrl = (version: number) => `${getApiBaseUrl()}/api/operators/me/photo?v=${version}`
export const updateOperatorPrivacy = (fields: Record<string, string>) =>
  apiRequest<OperatorProfile>('/api/operators/me/privacy', json('PUT', { fields }))
export const addOperatorEquipment = (equipment: { category: string; name: string; details: string; condition: string; visibility: string }) =>
  apiRequest<OperatorProfile>('/api/operators/me/equipment', json('POST', equipment))
export const removeOperatorEquipment = (equipmentId: string) =>
  apiRequest<void>(`/api/operators/me/equipment/${encodeURIComponent(equipmentId)}`, json('DELETE'))
export const searchOperators = (query: string) =>
  apiRequest<{ items: OperatorSummary[] }>(`/api/operators/search?q=${encodeURIComponent(query)}&limit=10`)
export const getOperatorPublicProfile = (username: string) =>
  apiRequest<OperatorSummary>(`/api/operators/${encodeURIComponent(username)}`)

export const getTeamWorkspace = () => apiRequest<TeamWorkspace>('/api/teams/workspace')
export const createTeam = (team: Record<string, unknown>) => apiRequest<Team>('/api/teams', json('POST', team))
export const uploadTeamLogo = (teamId: string, file: File) => {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<void>(`/api/teams/${encodeURIComponent(teamId)}/logo`, { method: 'POST', body })
}
export const teamLogoUrl = (teamId: string, version: number) => `${getApiBaseUrl()}/api/teams/${encodeURIComponent(teamId)}/logo?v=${version}`
export const updateTeam = (teamId: string, team: Record<string, unknown>) =>
  apiRequest<Team>(`/api/teams/${encodeURIComponent(teamId)}`, json('PATCH', team))
export const inviteOperator = (teamId: string, invitation: { operatorId: string; proposedRole: string; message: string }) =>
  apiRequest<{ message: string }>(`/api/teams/${encodeURIComponent(teamId)}/invitations`, json('POST', invitation))
export const acceptTeamInvitation = (invitationId: string) =>
  apiRequest<Team>(`/api/teams/invitations/${encodeURIComponent(invitationId)}/accept`, json('POST', {}))
export const declineTeamInvitation = (invitationId: string) =>
  apiRequest<{ message: string }>(`/api/teams/invitations/${encodeURIComponent(invitationId)}/decline`, json('POST', {}))
export const leaveTeam = () => apiRequest<{ message: string }>('/api/teams/leave', json('POST', {}))
export const transferCaptaincy = (teamId: string, operatorId: string, reason: string) =>
  apiRequest<Team>(`/api/teams/${encodeURIComponent(teamId)}/captaincy`, json('POST', { operatorId, reason }))

export const getFields = (query = '') => apiRequest<{items:VenueField[]}>(`/api/fields?q=${encodeURIComponent(query)}&limit=100`)
export const createField = (field:Record<string,unknown>) => apiRequest<VenueField>('/api/fields',json('POST',field))
export const getMaps = (fieldId = '') => apiRequest<{items:VenueMap[]}>(`/api/maps?limit=100${fieldId?`&fieldId=${encodeURIComponent(fieldId)}`:''}`)
export const createVenueMap = (fieldId:string,map:Record<string,unknown>) => apiRequest<VenueMap>(`/api/fields/${encodeURIComponent(fieldId)}/maps`,json('POST',map))
export const getOperations = (query = '') => apiRequest<{items:Operation[]}>(`/api/operations?q=${encodeURIComponent(query)}&limit=100`)
export const createOperation = (operation:Record<string,unknown>) => apiRequest<Operation>('/api/operations',json('POST',operation))
export const requestOperationParticipation = (id:string) => apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/participation`,json('POST',{}))
export const cancelOperationParticipation = (id:string) => apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/participation/cancel`,json('POST',{}))
export const getOperatorRanking = (city='',stateCode='') => apiRequest<{ruleVersion:string;items:RankingEntry[]}>(`/api/rankings/operators?city=${encodeURIComponent(city)}&stateCode=${encodeURIComponent(stateCode)}&limit=100`)
export const getMyPerformance = () => apiRequest<{items:PerformanceRecord[]}>('/api/performance/me')
export const getReviewablePerformance = () => apiRequest<{items:PerformanceRecord[]}>('/api/performance/reviewable')
export const savePerformance = (operationId:string,record:Record<string,unknown>) => apiRequest<PerformanceRecord>(`/api/performance/operations/${encodeURIComponent(operationId)}`,json('POST',record))
export const reviewPerformance = (id:string,review:Record<string,unknown>) => apiRequest<PerformanceRecord>(`/api/performance/${encodeURIComponent(id)}/review`,json('PATCH',review))
export const contestPerformance = (id:string,reason:string) => apiRequest<{message:string}>(`/api/performance/${encodeURIComponent(id)}/contests`,json('POST',{reason}))
