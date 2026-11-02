import { CheckCircle2, LoaderCircle, TriangleAlert, X } from 'lucide-react'
import { useEffect, useSyncExternalStore } from 'react'
import { dismissUpload, uploadStore, type UploadTask } from './uploadQueue'

const label = (task: UploadTask) => {
  switch (task.phase) {
    case 'uploading': return `Enviando ${Math.round(task.progress * 100)}%`
    case 'waiting-details': return 'Arquivo enviado'
    case 'publishing': return 'Publicando…'
    case 'processing': return task.kind === 'VIDEO' ? 'Processando vídeo…' : 'Processando foto…'
    case 'done': return task.purpose === 'RECON' ? 'Recon no ar' : 'Post publicado'
    case 'error': return task.message ?? 'Falha no envio'
    default: return ''
  }
}

/** Barra fixa no topo: o envio segue enquanto a pessoa navega pelo resto do app. */
export function UploadStatus() {
  const tasks = useSyncExternalStore(uploadStore.subscribe, uploadStore.getSnapshot)
  useEffect(() => {
    const done = tasks.filter(task => task.phase === 'done')
    if (!done.length) return
    const timer = window.setTimeout(() => done.forEach(task => dismissUpload(task.id)), 4000)
    return () => window.clearTimeout(timer)
  }, [tasks])
  if (!tasks.length) return null
  return <div className="upload-status" role="status" aria-live="polite">
    {tasks.map(task => <div key={task.id} className={`upload-status-item upload-status-item--${task.phase}`}>
      {task.phase === 'done' ? <CheckCircle2/> : task.phase === 'error' ? <TriangleAlert/> : <LoaderCircle className="spin"/>}
      <span>{label(task)}</span>
      {(task.phase === 'done' || task.phase === 'error') && <button type="button" onClick={() => dismissUpload(task.id)} aria-label="Fechar aviso"><X/></button>}
      {task.phase === 'uploading' && <i style={{ width: `${Math.round(task.progress * 100)}%` }} aria-hidden="true"/>}
    </div>)}
  </div>
}
