import { Archive, ChevronDown, Flag, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { addReconToDossier, createDossier, deleteRecon, getAuthorRecons, getDossier, getDossiers, markReconViewed, mediaUrl, removeReconFromDossier, reportRecon,
  type DossierSummary, type ReconItem } from '../api'
import { timeAgo } from './format'
import { useVideoSource } from './useVideoSource'
import { ReportSheet } from './FeedSheets'

const IMAGE_MS = 5000
const HOLD_MS = 220
const CLOSE_DRAG_PX = 110

type Header = { title: string; username: string; own: boolean }

/** Toca uma sequencia de Recons: barras de progresso, tocar a direita/esquerda, segurar pausa, arrastar para baixo fecha. */
function ReconPlayer({ header, items, startIndex, onView, onEnd, onPrevGroup, onClose, actions }: {
  header: Header; items: ReconItem[]; startIndex: number; onView?: (item: ReconItem) => Promise<boolean>
  onEnd: () => void; onPrevGroup: () => void; onClose: () => void; actions?: (item: ReconItem) => React.ReactNode
}) {
  const [index, setIndex] = useState(startIndex)
  const [progress, setProgress] = useState(0)
  const [paused, setPaused] = useState(false)
  const [ended, setEnded] = useState<Set<string>>(new Set())
  const [drag, setDrag] = useState(0)
  const video = useRef<HTMLVideoElement>(null)
  const pointer = useRef<{ x: number; y: number; at: number; held: boolean } | null>(null)
  const holdTimer = useRef<number | undefined>(undefined)
  const item = items[index]
  const isVideo = item?.media.kind === 'VIDEO'
  useVideoSource(video, mediaUrl(item?.media.hlsUrl), mediaUrl(item?.media.mp4Url), item && isVideo ? 'active' : 'idle')

  const next = useCallback(() => { setProgress(0); if (index + 1 < items.length) setIndex(index + 1); else onEnd() }, [index, items.length, onEnd])
  const prev = () => { setProgress(0); if (index > 0) setIndex(index - 1); else onPrevGroup() }

  useEffect(() => {
    if (!item || !onView) return
    let active = true
    onView(item).then(ok => { if (!ok && active) setEnded(previous => new Set(previous).add(item.id)) })
    return () => { active = false }
  }, [item, onView])

  // Foto: 5 s; video: acompanha o tempo do proprio video. Recon encerrado mostra o aviso e segue.
  useEffect(() => {
    if (!item || paused) return
    if (ended.has(item.id)) { const timer = window.setTimeout(next, 1500); return () => window.clearTimeout(timer) }
    if (isVideo) {
      const element = video.current
      if (!element) return
      element.dataset.active = 'true'
      element.play().catch(() => undefined)
      const tick = () => setProgress(element.duration ? element.currentTime / element.duration : 0)
      element.addEventListener('timeupdate', tick)
      element.addEventListener('ended', next)
      return () => { element.pause(); element.removeEventListener('timeupdate', tick); element.removeEventListener('ended', next) }
    }
    let frame = 0
    const started = performance.now() - progress * IMAGE_MS
    const step = (time: number) => {
      const value = (time - started) / IMAGE_MS
      if (value >= 1) { next(); return }
      setProgress(value)
      frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
    // progress entra so na retomada apos pausa; nao deve reiniciar o efeito a cada quadro.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item, paused, isVideo, ended, next])

  useEffect(() => {
    const keys = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'ArrowRight') next()
      if (event.key === 'ArrowLeft') prev()
    }
    window.addEventListener('keydown', keys)
    return () => window.removeEventListener('keydown', keys)
  })

  const down = (event: ReactPointerEvent<HTMLDivElement>) => {
    try { event.currentTarget.setPointerCapture(event.pointerId) } catch { /* ponteiro ja liberado: o gesto segue sem captura */ }
    pointer.current = { x: event.clientX, y: event.clientY, at: Date.now(), held: false }
    holdTimer.current = window.setTimeout(() => { if (pointer.current) { pointer.current.held = true; setPaused(true) } }, HOLD_MS)
  }
  const move = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!pointer.current) return
    const dy = event.clientY - pointer.current.y
    if (dy > 10) { window.clearTimeout(holdTimer.current); setPaused(true); setDrag(dy) }
  }
  const up = (event: ReactPointerEvent<HTMLDivElement>) => {
    window.clearTimeout(holdTimer.current)
    const start = pointer.current
    pointer.current = null
    if (!start) return
    if (drag > CLOSE_DRAG_PX) { onClose(); return }
    setDrag(0)
    if (start.held || drag > 10) { setPaused(false); return }
    const bounds = event.currentTarget.getBoundingClientRect()
    if (event.clientX - bounds.left < bounds.width * 0.3) prev(); else next()
  }

  if (!item) return null
  const media = item.media
  return <div className="recon-viewer" role="dialog" aria-modal="true" aria-label={`Recon de @${header.username}`}
    style={{ transform: drag ? `translateY(${drag}px)` : undefined, opacity: drag ? Math.max(0.4, 1 - drag / 400) : undefined }}>
    <div className="recon-stage">
      {ended.has(item.id) ? <div className="recon-ended" role="status"><b>Recon encerrado</b><span>Este Recon já saiu do ar.</span></div>
        : isVideo ? <video ref={video} className="recon-media" poster={mediaUrl(media.posterUrl)} playsInline muted={false} aria-label={`Vídeo de @${header.username}`}/>
        : <img className="recon-media" src={mediaUrl(media.images.at(-1)?.url)} alt={`Foto do Recon de @${header.username}${item.overlayText ? `: ${item.overlayText}` : ''}`}/>}
      {item.overlayText && !ended.has(item.id) && <p className="recon-overlay">{item.overlayText}</p>}
      <div className="recon-touch" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={() => { pointer.current = null; setPaused(false); setDrag(0) }}
        aria-label="Toque à direita para avançar, à esquerda para voltar; segure para pausar"/>
    </div>
    <header className="recon-header">
      <div className="recon-progress" aria-hidden="true">{items.map((entry, position) => <span key={entry.id}>
        <i style={{ width: `${position < index ? 100 : position === index ? Math.round(progress * 100) : 0}%` }}/></span>)}</div>
      <div className="recon-meta">
        <span className="recon-avatar">{header.username.charAt(0).toUpperCase()}</span>
        <p><b>{header.title}</b>{item.publishedAt && <> · {timeAgo(item.publishedAt)}</>}</p>
        {paused && !drag && <span className="recon-paused">Pausado</span>}
        <button type="button" className="feed-icon-button" onClick={onClose} aria-label="Fechar Recon"><X/></button>
      </div>
    </header>
    {actions && <footer className="recon-actions" onPointerDown={() => setPaused(true)}>{actions(item)}</footer>}
  </div>
}

/** Recons dos autores da barra, em sequencia: ao terminar um autor, passa para o proximo. */
export function ReconViewer({ usernames, start, onClose, onChanged }: { usernames: string[]; start: number; onClose: () => void; onChanged: () => void }) {
  const [authorIndex, setAuthorIndex] = useState(start)
  const [data, setData] = useState<{ username: string; own: boolean; items: ReconItem[] } | null>(null)
  const [saving, setSaving] = useState<ReconItem | null>(null)
  const [reporting, setReporting] = useState<ReconItem | null>(null)
  const username = usernames[authorIndex]
  useEffect(() => {
    let active = true
    setData(null)
    getAuthorRecons(username).then(result => { if (active) setData({ username: result.username, own: result.own, items: result.items }) })
      .catch(() => { if (active) setData({ username, own: false, items: [] }) })
    return () => { active = false }
  }, [username])
  const nextAuthor = useCallback(() => { if (authorIndex + 1 < usernames.length) setAuthorIndex(authorIndex + 1); else onClose() }, [authorIndex, usernames.length, onClose])
  const onView = useCallback((item: ReconItem) => markReconViewed(item.id).then(() => true).catch(() => false), [])
  // Ao fechar, a barra recarrega (vistos/nao vistos); o ref evita disparar a cada nova funcao do pai.
  const changed = useRef(onChanged)
  useEffect(() => { changed.current = onChanged }, [onChanged])
  useEffect(() => () => changed.current(), [])
  useEffect(() => {
    if (data && data.items.length === 0) { const timer = window.setTimeout(nextAuthor, 1500); return () => window.clearTimeout(timer) }
  }, [data, nextAuthor])
  if (!data) return <div className="recon-viewer" role="dialog" aria-modal="true" aria-busy="true"><div className="recon-ended"><span>Carregando…</span></div></div>
  if (!data.items.length) return <div className="recon-viewer" role="dialog" aria-modal="true"><div className="recon-ended" role="status"><b>Recon encerrado</b><span>@{data.username} não tem Recons no ar.</span></div></div>
  const firstUnseen = Math.max(0, data.items.findIndex(item => !item.seen))
  return <>
    <ReconPlayer key={data.username} header={{ title: `Recon de @${data.username}`, username: data.username, own: data.own }} items={data.items}
      startIndex={data.own ? 0 : firstUnseen} onView={onView} onEnd={nextAuthor}
      onPrevGroup={() => setAuthorIndex(Math.max(0, authorIndex - 1))} onClose={onClose}
      actions={data.own ? item => <>
        <button type="button" onClick={() => setSaving(item)}><Archive/> Salvar no Dossiê</button>
        <button type="button" onClick={() => { if (window.confirm('Apagar este Recon?')) void deleteRecon(item.id).then(() => { onChanged(); onClose() }) }}><Trash2/> Apagar</button>
      </> : item => <button type="button" onClick={() => setReporting(item)}><Flag/> Denunciar</button>}/>
    {saving && <SaveToDossier username={data.username} recon={saving} onClose={() => setSaving(null)}/>}
    {reporting && <ReportSheet title="Denunciar Recon" onClose={() => setReporting(null)} onSubmit={(reason, details) => reportRecon(reporting.id, reason, details)}/>}
  </>
}

function SaveToDossier({ username, recon, onClose }: { username: string; recon: ReconItem; onClose: () => void }) {
  const [dossiers, setDossiers] = useState<DossierSummary[] | null>(null)
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  useEffect(() => { getDossiers(username).then(setDossiers).catch(() => setDossiers([])) }, [username])
  const save = async (dossierId: string) => {
    try { await addReconToDossier(dossierId, recon.id); setMessage('Recon guardado no Dossiê.'); window.setTimeout(onClose, 900) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível guardar.') }
  }
  const create = async () => {
    if (!title.trim()) return
    try { const dossier = await createDossier(title.trim()); await save(dossier.id) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível criar o Dossiê.') }
  }
  return <div className="create-sheet-backdrop recon-sheet" onClick={onClose}>
    <section className="create-sheet" role="dialog" aria-modal="true" aria-labelledby="save-dossier-title" onClick={event => event.stopPropagation()}>
      <header><h2 id="save-dossier-title">Salvar no Dossiê</h2><button type="button" className="feed-icon-button" onClick={onClose} aria-label="Fechar"><ChevronDown/></button></header>
      {dossiers?.map(dossier => <button type="button" key={dossier.id} className="create-choice" onClick={() => void save(dossier.id)}>
        <Archive/><span><b>{dossier.title}</b><small>{dossier.count} {dossier.count === 1 ? 'Recon' : 'Recons'}</small></span></button>)}
      <label className="create-caption"><span>Novo Dossiê</span>
        <input value={title} maxLength={40} onChange={event => setTitle(event.target.value)} placeholder="Ex.: Operação Inverno"/></label>
      <button type="button" className="button button--small" disabled={!title.trim()} onClick={() => void create()}>Criar e salvar</button>
      {message && <p className="create-notice" role="status">{message}</p>}
    </section>
  </div>
}

/** Dossie do perfil: Recons guardados, tocados em sequencia (nao contam como visualizacao). */
export function DossierViewer({ dossierId, onClose, onChanged }: { dossierId: string; onClose: () => void; onChanged: () => void }) {
  const [data, setData] = useState<Awaited<ReturnType<typeof getDossier>> | null>(null)
  useEffect(() => { getDossier(dossierId).then(setData).catch(() => onClose()) }, [dossierId, onClose])
  if (!data) return <div className="recon-viewer" role="dialog" aria-modal="true" aria-busy="true"><div className="recon-ended"><span>Carregando…</span></div></div>
  if (!data.items.length) return <div className="recon-viewer" role="dialog" aria-modal="true"><div className="recon-ended" role="status"><b>Dossiê vazio</b>
    <button type="button" className="button button--small" onClick={onClose}>Fechar</button></div></div>
  return <ReconPlayer header={{ title: `Dossiê ${data.title}`, username: data.ownerUsername, own: data.own }} items={data.items} startIndex={0}
    onEnd={onClose} onPrevGroup={() => undefined} onClose={onClose}
    actions={data.own ? item => <button type="button" onClick={() => void removeReconFromDossier(data.id, item.id).then(() => { onChanged(); onClose() })}><Trash2/> Remover do Dossiê</button> : undefined}/>
}
