import { completeMediaUpload, createFeedPost, createRecon, getMediaStatus, getMediaUpload, putMediaChunk, startMediaUpload, type MediaCrop } from '../api'
import { toPayload, type ReconElement } from './recon/composition'

/**
 * Fila de envios fora dos componentes: o envio continua enquanto a pessoa navega pelo app.
 * O arquivo sobe assim que o enquadramento e confirmado; a descricao chega depois e o post e criado quando os dois estiverem prontos.
 * No Recon o envio termina (complete) so na publicacao, porque silenciar o audio e o enquadramento final do video
 * so sao conhecidos ali.
 */
export type UploadPhase = 'uploading' | 'waiting-details' | 'publishing' | 'processing' | 'done' | 'error' | 'cancelled'
export type UploadPurpose = 'POST' | 'RECON'
export type UploadTask = { id: string; purpose: UploadPurpose; kind: 'IMAGE' | 'VIDEO'; progress: number; phase: UploadPhase; message?: string; postId?: string }
export type PostDetails = { caption?: string; fieldId?: string | null; teamId?: string | null; taggedUsernames?: string[]; operationId?: string | null }
export type ReconDetails = {
  overlayText?: string | null; elements?: ReconElement[]; audioMuted?: boolean; dossierId?: string | null
  complete?: { stripAudio?: boolean; crop?: MediaCrop | null; filterCode?: string | null }
}

type Internal = UploadTask & {
  file: Blob; crop: MediaCrop | null; filterCode: string | null; uploadId?: string; mediaId?: string; chunksDone: boolean; uploaded: boolean
  details?: PostDetails | ReconDetails; idempotencyKey: string; cancelled: boolean; running: boolean
}

const MAX_RETRIES = 5
const POLL_MS = 3000
let tasks: Internal[] = []
let snapshot: UploadTask[] = []
const listeners = new Set<() => void>()

const publish = () => {
  snapshot = tasks.filter(task => task.phase !== 'cancelled').map(({ id, purpose, kind, progress, phase, message, postId }) => ({ id, purpose, kind, progress, phase, message, postId }))
  listeners.forEach(listener => listener())
}
const update = (id: string, patch: Partial<Internal>) => {
  tasks = tasks.map(task => task.id === id ? { ...task, ...patch } : task)
  publish()
}
const find = (id: string) => tasks.find(task => task.id === id)
const sleep = (ms: number) => new Promise(resolve => window.setTimeout(resolve, ms))
const errorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback

export const uploadStore = {
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
  getSnapshot: () => snapshot,
}

/** filterCode: filtro tatico que o servidor aplica no video (fotos ja chegam filtradas). */
export function startPostUpload(file: Blob, kind: 'IMAGE' | 'VIDEO', crop: MediaCrop | null, purpose: UploadPurpose = 'POST', filterCode: string | null = null): string {
  const id = crypto.randomUUID()
  tasks = [...tasks, { id, purpose, kind, progress: 0, phase: 'uploading', file, crop, filterCode: kind === 'VIDEO' ? filterCode : null, chunksDone: false, uploaded: false,
    idempotencyKey: crypto.randomUUID(), cancelled: false, running: false }]
  publish()
  void run(id)
  return id
}

export function submitPostDetails(id: string, details: PostDetails | ReconDetails) {
  const task = find(id)
  if (!task || task.cancelled) return
  update(id, { details, phase: task.chunksDone ? 'publishing' : task.phase })
  void finish(id)
}

export function cancelUpload(id: string) {
  update(id, { cancelled: true, phase: 'cancelled' })
}

export function dismissUpload(id: string) {
  tasks = tasks.filter(task => task.id !== id)
  publish()
}

/** Tentar de novo depois de um erro: continua de onde parou (envio, conclusao ou publicacao). */
export function retryUpload(id: string) {
  const task = find(id)
  if (!task || task.cancelled || task.phase !== 'error') return
  update(id, { phase: task.chunksDone ? 'publishing' : 'uploading', message: undefined })
  if (!task.chunksDone) void run(id); else void finish(id)
}

async function run(id: string) {
  const task = find(id)
  if (!task || task.running) return
  update(id, { running: true })
  try {
    let uploadId = task.uploadId
    let chunkBytes = 0
    let offset = 0
    if (!uploadId) {
      const upload = await startMediaUpload({ purpose: task.purpose, kind: task.kind, contentType: task.file.type || 'application/octet-stream', totalBytes: task.file.size, crop: task.crop, filterCode: task.filterCode })
      uploadId = upload.uploadId; chunkBytes = upload.chunkBytes; offset = upload.receivedBytes
      update(id, { uploadId, mediaId: upload.mediaId })
    } else {
      const upload = await getMediaUpload(uploadId)
      chunkBytes = upload.chunkBytes; offset = upload.receivedBytes
    }
    const file = task.file
    let failures = 0
    while (offset < file.size) {
      if (find(id)?.cancelled) return
      try {
        const result = await putMediaChunk(uploadId, offset, file.slice(offset, offset + chunkBytes))
        offset = result.receivedBytes
        failures = 0
        update(id, { progress: offset / file.size })
      } catch (error) {
        // Rede instavel (4G): espera, pergunta ao servidor ate onde chegou e continua dali.
        if (++failures > MAX_RETRIES) throw error
        await sleep(1000 * 2 ** failures)
        offset = (await getMediaUpload(uploadId).catch(() => ({ receivedBytes: offset }))).receivedBytes
      }
    }
    const current = find(id)
    if (!current || current.cancelled) return
    update(id, { chunksDone: true, progress: 1, phase: current.details ? 'publishing' : 'waiting-details' })
    await finish(id)
  } catch (error) {
    update(id, { phase: 'error', message: errorMessage(error, 'Não foi possível enviar o arquivo.') })
  } finally {
    update(id, { running: false })
  }
}

/** Conclui o envio (no Recon, com as opcoes da publicacao) e cria o post/Recon quando os detalhes chegaram. */
async function finish(id: string) {
  const task = find(id)
  if (!task || task.cancelled || !task.chunksDone || !task.uploadId) return
  const recon = task.purpose === 'RECON'
  if (!task.uploaded) {
    // Post conclui logo; Recon espera a publicacao para saber se tira o audio e qual o enquadramento.
    if (recon && !task.details) return
    try {
      const options = recon ? (task.details as ReconDetails).complete : undefined
      await completeMediaUpload(task.uploadId, options)
      update(id, { uploaded: true })
    } catch (error) {
      update(id, { phase: 'error', message: errorMessage(error, 'Não foi possível concluir o envio.') })
      return
    }
  }
  if (find(id)?.details) await createPost(id)
}

async function createPost(id: string) {
  const task = find(id)
  if (!task?.mediaId || !task.details || task.phase === 'processing' || task.phase === 'done') return
  update(id, { phase: 'publishing' })
  try {
    let post: { id: string; status: string }
    if (task.purpose === 'RECON') {
      const details = task.details as ReconDetails
      post = await createRecon({ mediaId: task.mediaId, overlayText: details.overlayText ?? null, elements: details.elements ? toPayload(details.elements) : undefined,
        audioMuted: details.audioMuted, dossierId: details.dossierId ?? null })
    } else {
      post = await createFeedPost({ mediaId: task.mediaId, idempotencyKey: task.idempotencyKey, ...(task.details as PostDetails) })
    }
    if (post.status === 'PUBLISHED' || post.status === 'ACTIVE') { update(id, { phase: 'done', postId: post.id }); return }
    update(id, { phase: 'processing', postId: post.id })
    for (;;) {
      await sleep(POLL_MS)
      const media = await getMediaStatus(task.mediaId).catch(() => null)
      if (media?.status === 'READY') { update(id, { phase: 'done' }); return }
      if (media?.status === 'FAILED') { update(id, { phase: 'error', message: 'Não foi possível processar o arquivo. Tente outro.' }); return }
    }
  } catch (error) {
    update(id, { phase: 'error', message: errorMessage(error, task.purpose === 'RECON' ? 'Não foi possível publicar o Recon.' : 'Não foi possível publicar o post.') })
  }
}
