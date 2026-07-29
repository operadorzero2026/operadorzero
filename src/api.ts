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
  airsoftStartedAt?: string | null
  airsoftExperience?: string | null
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
  teamName?: string | null
  airsoftStartedAt?: string | null
  airsoftExperience?: string | null
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
export type Operation = { id:string; name:string; description:string; fieldId:string; fieldName:string; mapId?:string|null; mapName?:string|null; city:string; stateCode:string; operationDate:string; presentationTime:string; startTime:string; endTime:string; modality:string; status:string; gameSize:'SMALL'|'MEDIUM'|'LARGE'; participantLimit:number|null; participantCount:number; registrationPrice:number; participantStatus?:string|null; managedByCurrentUser:boolean; hasCover:boolean; coverVersion:number; briefing?:string|null; version?:number }
export type OperationTeam = { id:string; name:string; acronym?:string|null; color:string; description?:string|null; capacity:number; participantCount:number; status:string }
export type OperationParticipant = { operatorId:string; callsign:string; displayName:string; status:string; operationTeamId?:string|null; operationTeamName?:string|null }
export type OperationRoster = { teams:OperationTeam[]; participants:OperationParticipant[]; currentUserTeamId?:string|null }
export type OperationSquad = { id:string;teamId:string;name:string;acronym?:string|null;description?:string|null;capacity:number;participantCount:number;sortOrder:number;status:string;commanderId?:string|null;commanderCallsign?:string|null;radioId?:string|null;radioCallsign?:string|null }
export type OperationStructureTeam = OperationTeam & {sortOrder:number;commanderId?:string|null;commanderCallsign?:string|null;radioId?:string|null;radioCallsign?:string|null;squads:OperationSquad[]}
export type OperationStructure = {operationId:string;gameSize:'SMALL'|'MEDIUM'|'LARGE';participantLimit:number|null;participantCount:number;commandRolesEnabled:boolean;allowRoleAccumulation:boolean;managedByCurrentUser:boolean;teams:OperationStructureTeam[];roles:Array<{id:string;operatorId:string;callsign:string;role:string;teamId?:string|null;squadId?:string|null}>}
export type OperationChatMessage={id:string;channelId:string;author:{id:string;callsign:string;displayName:string};parentMessageId?:string|null;body:string;status:string;official:boolean;roleLabel?:string|null;createdAt:string;updatedAt:string;editableByCurrentUser:boolean}
export type OperationChatPage={channelId:string;channelType:string;locked:boolean;items:OperationChatMessage[];hasMore:boolean}
export type RankingEntry = { position:number; operatorId:string; username:string; displayName:string; callsign:string; city?:string|null; stateCode?:string|null; teamId?:string|null; teamName?:string|null; teamAcronym?:string|null; gross:number; factor:number; bonus:number; penalties:number; finalScore:number; positionVariation:number; operationsConsidered:number }
export type PerformanceRecord = { id:string; operationId:string; operationName:string; operationDate:string; operatorId:string; operatorCallsign:string; representedTeamId?:string|null; representedTeamName?:string|null; eliminations:number; deaths:number; objectivesCompleted:number; roundWins:number; result:string; positionUsed?:string|null; notes?:string|null; highlightReceived?:string|null; penaltyPoints:number; abandoned:boolean; participationScope:string; status:string; organizerConfirmed:boolean; teamConfirmed:boolean; organizerNotes?:string|null; currentUserRecord:boolean; canReviewAsOrganizer:boolean; canReviewAsTeam:boolean; canContest:boolean; createdAt:string; updatedAt:string; version:number }
export type CommunityCategory = { id:string; slug:string; name:string }
export type CommunityAuthor = { id:string; username:string; displayName:string; callsign:string; city?:string|null; stateCode?:string|null; teamName?:string|null }
export type CommunityPostSummary = {
  id:string
  author:CommunityAuthor
  category:CommunityCategory
  title:string
  description:string
  createdAt:string
  voteCount:number
  commentCount:number
  votedByCurrentUser:boolean
  savedByCurrentUser:boolean
  canManage:boolean
  commentsLocked:boolean
  coverImageId?:string|null
}
export type CommunityPost = CommunityPostSummary & { updatedAt:string; imageIds:string[] }
export type CommunityComment = {
  id:string
  postId:string
  parentCommentId?:string|null
  author:CommunityAuthor
  description:string
  createdAt:string
  canManage:boolean
}
export type CommunityReport = {
  id:string
  targetType:'POST'|'COMMENT'
  targetId:string
  targetTitle:string
  reporterUsername:string
  reason:string
  details?:string|null
  status:string
  createdAt:string
}

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
let csrfRequest: Promise<void> | null = null
let csrfPreparedAt = 0
const AUTH_REQUEST_TIMEOUT_MS = 15000
const AUTH_BOOT_TIMEOUT_MS = 120000
const AUTH_MUTATION_TIMEOUT_MS = 45000
const CSRF_FRESHNESS_MS = 5 * 60 * 1000

function authMark(name: string) {
  if (typeof performance !== 'undefined') performance.mark(`oz-auth:${name}`)
}

function authMeasure(name: string, start: string, end: string) {
  if (typeof performance === 'undefined') return
  try { performance.measure(`oz-auth:${name}`, `oz-auth:${start}`, `oz-auth:${end}`) } catch { /* mark not present */ }
}

class ApiTimeoutError extends Error {
  constructor(message = 'A conexão está demorando para iniciar. Aguarde alguns segundos e tente novamente.') {
    super(message)
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
  if (!configuredApiUrl) throw new Error('Não foi possível conectar ao serviço neste momento.')
  const url = new URL(configuredApiUrl)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Não foi possível conectar ao serviço neste momento.')
  return url.toString().replace(/\/$/, '')
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
  if (csrfToken && Date.now() - csrfPreparedAt < CSRF_FRESHNESS_MS) return
  csrfToken = null
  if (!csrfRequest) {
    csrfRequest = (async () => {
      authMark('csrf-start')
      const response = await fetchWithTimeout(`${getApiBaseUrl()}/api/auth/csrf`, {
        method: 'GET', credentials: 'include', headers: { Accept: 'application/json' },
      }, AUTH_BOOT_TIMEOUT_MS)
      if (!response.ok) throw await readError(response)
      const result = await response.json() as { token: string; headerName: string }
      csrfToken = result.token
      csrfHeader = result.headerName
      csrfPreparedAt = Date.now()
      authMark('csrf-end')
      authMeasure('csrf', 'csrf-start', 'csrf-end')
    })().finally(() => { csrfRequest = null })
  }
  await csrfRequest
}

/** Starts the safe, idempotent CSRF/bootstrap request while the user fills the form. */
export const prepareAuthentication = () => ensureCsrf()

async function apiRequest<T>(path: string, init: RequestInit = {}, timeoutMs = AUTH_REQUEST_TIMEOUT_MS): Promise<T> {
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
  authMark('login-start')
  const session = await apiRequest<SessionUser>('/api/auth/login', json('POST', { email, password }), AUTH_MUTATION_TIMEOUT_MS)
  authMark('login-response')
  authMeasure('login-request', 'login-start', 'login-response')
  csrfToken = null
  csrfPreparedAt = 0
  return session
}

export function markAuthenticatedAreaUsable() {
  authMark('usable')
  authMeasure('login-to-usable', 'login-start', 'usable')
}

export const register = (displayName: string, email: string, password: string, passwordConfirmation: string, termsAccepted: boolean) =>
  apiRequest<{ message: string }>('/api/auth/register', json('POST', { displayName, email, password, passwordConfirmation, termsAccepted }), AUTH_MUTATION_TIMEOUT_MS)
export const requestPasswordRecovery = (email: string) => apiRequest<{ message: string }>('/api/auth/password-recovery', json('POST', { email }), AUTH_MUTATION_TIMEOUT_MS)
export const resendVerification = (email: string) => apiRequest<{ message: string }>('/api/auth/resend-verification', json('POST', { email }), AUTH_MUTATION_TIMEOUT_MS)
export const verifyEmail = (token: string) => apiRequest<{ message: string }>('/api/auth/verify-email', json('POST', { token }), AUTH_MUTATION_TIMEOUT_MS)
export const resetPassword = (token: string, password: string, passwordConfirmation: string) =>
  apiRequest<{ message: string }>('/api/auth/password-reset', json('POST', { token, password, passwordConfirmation }), AUTH_MUTATION_TIMEOUT_MS)

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
  csrfPreparedAt = 0
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
export const getTeamSummary = () => apiRequest<{ total: number }>('/api/teams/summary')
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
export const getOperation = (id:string) => apiRequest<Operation>(`/api/operations/${encodeURIComponent(id)}`)
export const getOperationRoster = (id:string) => apiRequest<OperationRoster>(`/api/operations/${encodeURIComponent(id)}/roster`)
export const createOperation = (operation:Record<string,unknown>) => apiRequest<Operation>('/api/operations',json('POST',operation))
export const updateOperation = (id:string,operation:Record<string,unknown>) => apiRequest<Operation>(`/api/operations/${encodeURIComponent(id)}`,json('PATCH',operation))
export const deleteOperation = (id:string,reason:string) => apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}`,json('DELETE',{reason}))
export async function uploadOperationCover(id:string,file:File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<Operation>(`/api/operations/${encodeURIComponent(id)}/cover`, { method:'POST', body })
}
export const operationCoverUrl = (id:string,version:number) => `${getApiBaseUrl()}/api/operations/${encodeURIComponent(id)}/cover?v=${version}`
export const publishOperation = (id:string) => apiRequest<Operation>(`/api/operations/${encodeURIComponent(id)}/publish`,json('POST',{}))
export const requestOperationParticipation = (id:string,operationTeamId:string,operationSquadId?:string|null) => apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/participation`,json('POST',{operationTeamId,operationSquadId:operationSquadId||null}))
export const cancelOperationParticipation = (id:string) => apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/participation/cancel`,json('POST',{}))
export const removeOperationCover=(id:string)=>apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/cover`,json('DELETE'))
export const getOperationStructure=(id:string)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/structure`)
export const createOperationTeam=(id:string,team:Record<string,unknown>)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/teams`,json('POST',team))
export const updateOperationTeam=(id:string,teamId:string,team:Record<string,unknown>)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/teams/${encodeURIComponent(teamId)}`,json('PATCH',team))
export const deleteOperationTeam=(id:string,teamId:string)=>apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/teams/${encodeURIComponent(teamId)}`,json('DELETE'))
export const createOperationSquad=(id:string,teamId:string,squad:Record<string,unknown>)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/teams/${encodeURIComponent(teamId)}/squads`,json('POST',squad))
export const updateOperationSquad=(id:string,squadId:string,squad:Record<string,unknown>)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/squads/${encodeURIComponent(squadId)}`,json('PATCH',squad))
export const deleteOperationSquad=(id:string,squadId:string)=>apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/squads/${encodeURIComponent(squadId)}`,json('DELETE'))
export const moveOperationParticipant=(id:string,move:{operatorId:string;teamId:string;squadId?:string|null})=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/participants/move`,json('PATCH',move))
export const assignOperationRole=(id:string,role:Record<string,unknown>)=>apiRequest<OperationStructure>(`/api/operations/${encodeURIComponent(id)}/roles`,json('POST',role))
export const removeOperationRole=(id:string,roleId:string)=>apiRequest<{message:string}>(`/api/operations/${encodeURIComponent(id)}/roles/${encodeURIComponent(roleId)}`,json('DELETE'))
export const getOperationChat=(id:string,teamId?:string|null)=>apiRequest<OperationChatPage>(teamId?`/api/operations/${encodeURIComponent(id)}/teams/${encodeURIComponent(teamId)}/chat`:`/api/operations/${encodeURIComponent(id)}/chat/general`)
export const sendOperationChat=(id:string,body:string,teamId?:string|null,parentMessageId?:string|null)=>apiRequest<{message:string}>(teamId?`/api/operations/${encodeURIComponent(id)}/teams/${encodeURIComponent(teamId)}/chat/messages`:`/api/operations/${encodeURIComponent(id)}/chat/general/messages`,json('POST',{body,parentMessageId:parentMessageId||null,idempotencyKey:crypto.randomUUID(),official:false}))
export const getOperatorRanking = (city='',stateCode='') => apiRequest<{ruleVersion:string;items:RankingEntry[]}>(`/api/rankings/operators?city=${encodeURIComponent(city)}&stateCode=${encodeURIComponent(stateCode)}&limit=100`)
export const getMyPerformance = () => apiRequest<{items:PerformanceRecord[]}>('/api/performance/me')
export const getReviewablePerformance = () => apiRequest<{items:PerformanceRecord[]}>('/api/performance/reviewable')
export const savePerformance = (operationId:string,record:Record<string,unknown>) => apiRequest<PerformanceRecord>(`/api/performance/operations/${encodeURIComponent(operationId)}`,json('POST',record))
export const reviewPerformance = (id:string,review:Record<string,unknown>) => apiRequest<PerformanceRecord>(`/api/performance/${encodeURIComponent(id)}/review`,json('PATCH',review))
export const contestPerformance = (id:string,reason:string) => apiRequest<{message:string}>(`/api/performance/${encodeURIComponent(id)}/contests`,json('POST',{reason}))

export const getCommunityCategories = () => apiRequest<CommunityCategory[]>('/api/community/categories')
export const getCommunityPosts = (filters:{query?:string;category?:string;sort?:string;page?:number;size?:number}={}) => {
  const params = new URLSearchParams({
    q: filters.query || '',
    category: filters.category || '',
    sort: filters.sort || 'RECENT',
    page: String(filters.page || 0),
    size: String(filters.size || 20),
  })
  return apiRequest<{items:CommunityPostSummary[];page:number;size:number;total:number}>(`/api/community/posts?${params}`)
}
export const getCommunityPost = (id:string) => apiRequest<CommunityPost>(`/api/community/posts/${encodeURIComponent(id)}`)
export const getCommunityComments = (id:string) => apiRequest<{items:CommunityComment[]}>(`/api/community/posts/${encodeURIComponent(id)}/comments`)
export const getCommunityAuthor = (username:string) => apiRequest<CommunityAuthor>(`/api/community/authors/${encodeURIComponent(username)}`)
export const communityImageUrl = (id:string) => `${getApiBaseUrl()}/api/community/images/${encodeURIComponent(id)}`
export const createCommunityPost = (post:{categorySlug:string;title:string;description:string;idempotencyKey:string}) =>
  apiRequest<CommunityPost>('/api/community/posts',json('POST',post),30000)
export const uploadCommunityImage = (postId:string,file:File) => {
  const body = new FormData()
  body.append('file',file)
  return apiRequest<{message:string}>(`/api/community/posts/${encodeURIComponent(postId)}/images`,{method:'POST',body},30000)
}
export const createCommunityComment = (postId:string,comment:{parentCommentId?:string|null;description:string;idempotencyKey:string}) =>
  apiRequest<{message:string}>(`/api/community/posts/${encodeURIComponent(postId)}/comments`,json('POST',comment))
export const toggleCommunityVote = (postId:string) =>
  apiRequest<{active:boolean;count:number}>(`/api/community/posts/${encodeURIComponent(postId)}/vote`,json('POST',{}))
export const toggleCommunityBookmark = (postId:string) =>
  apiRequest<{active:boolean;count:number}>(`/api/community/posts/${encodeURIComponent(postId)}/bookmark`,json('POST',{}))
export const reportCommunityPost = (postId:string,report:{reason:string;details:string}) =>
  apiRequest<{message:string}>(`/api/community/posts/${encodeURIComponent(postId)}/reports`,json('POST',report))
export const reportCommunityComment = (commentId:string,report:{reason:string;details:string}) =>
  apiRequest<{message:string}>(`/api/community/comments/${encodeURIComponent(commentId)}/reports`,json('POST',report))
export const deleteCommunityPost = (postId:string) =>
  apiRequest<{message:string}>(`/api/community/posts/${encodeURIComponent(postId)}`,json('DELETE'))
export const deleteCommunityComment = (commentId:string) =>
  apiRequest<{message:string}>(`/api/community/comments/${encodeURIComponent(commentId)}`,json('DELETE'))
export const getCommunityReports = () => apiRequest<{items:CommunityReport[]}>('/api/admin/community/reports')
export const moderateCommunityPost = (postId:string,status:'PUBLISHED'|'SUSPENDED'|'DELETED',reason:string) =>
  apiRequest<{message:string}>(`/api/admin/community/posts/${encodeURIComponent(postId)}/status`,json('PATCH',{status,reason}))
export const moderateCommunityComment = (commentId:string,status:'PUBLISHED'|'SUSPENDED'|'DELETED',reason:string) =>
  apiRequest<{message:string}>(`/api/admin/community/comments/${encodeURIComponent(commentId)}/status`,json('PATCH',{status,reason}))
export const setCommunityCommentsLocked = (postId:string,locked:boolean,reason:string) =>
  apiRequest<{message:string}>(`/api/admin/community/posts/${encodeURIComponent(postId)}/comments-lock`,json('PATCH',{locked,reason}))
export const resolveCommunityReport = (reportId:string,status:'REVIEWED'|'DISMISSED'|'ACTIONED',resolution:string) =>
  apiRequest<{message:string}>(`/api/admin/community/reports/${encodeURIComponent(reportId)}`,json('PATCH',{status,resolution}))
