import { completeMediaUpload, createFeedPost, getMediaStatus, getMediaUpload, putMediaChunk, startMediaUpload, type MediaCrop } from '../api'

/**
 * Fila de envios fora dos componentes: o envio continua enquanto a pessoa navega pelo app.
 * O arquivo sobe assim que o enquadramento e confirmado; a descricao chega depois e o post e criado quando os dois estiverem prontos.
 */
export type UploadPhase = 'uploading' | 'waiting-details' | 'publishing' | 'processing' | 'done' | 'error' | 'cancelled'
export type UploadTask = { id: string; kind: 'IMAGE' | 'VIDEO'; progress: number; phase: UploadPhase; message?: string; postId?: string }
export type PostDetails = { caption?: string; fieldId?: string | null; teamId?: string | null; taggedUsernames?: string[] }

type Internal = UploadTask & { mediaId?: string; uploaded: boolean; details?: PostDetails; idempotencyKey: string; cancelled: boolean }

const MAX_RETRIES = 5
const POLL_MS = 3000
let tasks: Internal[] = []
let snapshot: UploadTask[] = []
const listeners = new Set<() => void>()

const publish = () => {
  snapshot = tasks.filter(task => task.phase !== 'cancelled').map(({ id, kind, progress, phase, message, postId }) => ({ id, kind, progress, phase, message, postId }))
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

export function startPostUpload(file: Blob, kind: 'IMAGE' | 'VIDEO', crop: MediaCrop | null): string {
  const id = crypto.randomUUID()
  tasks = [...tasks, { id, kind, progress: 0, phase: 'uploading', uploaded: false, idempotencyKey: crypto.randomUUID(), cancelled: false }]
  publish()
  void run(id, file, kind, crop)
  return id
}

export function submitPostDetails(id: string, details: PostDetails) {
  const task = find(id)
  if (!task || task.cancelled) return
  update(id, { details, phase: task.uploaded ? 'publishing' : task.phase })
  if (task.uploaded) void createPost(id)
}

export function cancelUpload(id: string) {
  update(id, { cancelled: true, phase: 'cancelled' })
}

export function dismissUpload(id: string) {
  tasks = tasks.filter(task => task.id !== id)
  publish()
}

async function run(id: string, file: Blob, kind: 'IMAGE' | 'VIDEO', crop: MediaCrop | null) {
  try {
    const upload = await startMediaUpload({ purpose: 'POST', kind, contentType: file.type || 'application/octet-stream', totalBytes: file.size, crop })
    let offset = upload.receivedBytes
    let failures = 0
    while (offset < file.size) {
      if (find(id)?.cancelled) return
      try {
        const result = await putMediaChunk(upload.uploadId, offset, file.slice(offset, offset + upload.chunkBytes))
        offset = result.receivedBytes
        failures = 0
        update(id, { progress: offset / file.size })
      } catch (error) {
        // Rede instavel (4G): espera, pergunta ao servidor ate onde chegou e continua dali.
        if (++failures > MAX_RETRIES) throw error
        await sleep(1000 * 2 ** failures)
        offset = (await getMediaUpload(upload.uploadId).catch(() => ({ receivedBytes: offset }))).receivedBytes
      }
    }
    await completeMediaUpload(upload.uploadId)
    const task = find(id)
    if (!task || task.cancelled) return
    update(id, { mediaId: upload.mediaId, uploaded: true, progress: 1, phase: task.details ? 'publishing' : 'waiting-details' })
    if (task.details) await createPost(id)
  } catch (error) {
    update(id, { phase: 'error', message: errorMessage(error, 'Não foi possível enviar o arquivo.') })
  }
}

async function createPost(id: string) {
  const task = find(id)
  if (!task?.mediaId || !task.details || task.phase === 'processing' || task.phase === 'done') return
  try {
    const post = await createFeedPost({ mediaId: task.mediaId, idempotencyKey: task.idempotencyKey, ...task.details })
    if (post.status === 'PUBLISHED') { update(id, { phase: 'done', postId: post.id }); return }
    update(id, { phase: 'processing', postId: post.id })
    for (;;) {
      await sleep(POLL_MS)
      const media = await getMediaStatus(task.mediaId).catch(() => null)
      if (media?.status === 'READY') { update(id, { phase: 'done' }); return }
      if (media?.status === 'FAILED') { update(id, { phase: 'error', message: 'Não foi possível processar o arquivo. Tente outro.' }); return }
    }
  } catch (error) {
    update(id, { phase: 'error', message: errorMessage(error, 'Não foi possível publicar o post.') })
  }
}
