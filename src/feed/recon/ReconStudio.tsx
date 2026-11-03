import { ArrowLeft, Clock3, Send, UsersRound } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { getDossiers, type DossierSummary } from '../../api'
import { inspect } from '../CreateFlow'
import { filterCode, paintFilter, type TacticalFilterId } from '../filters/tacticalFilters'
import { sourceRect, type Framing } from '../framing'
import { cancelUpload, retryUpload, startPostUpload, submitPostDetails, uploadStore, type ReconDetails } from '../uploadQueue'
import { CameraScreen } from './CameraScreen'
import { ensureReconFonts, FRAME_ASPECT, type ReconElement } from './composition'
import { SettingsSheet, useBackToClose } from './EditorTools'
import { ElementLayer } from './ElementLayer'
import { MediaFrame, ReconEditor, type Fit, type StudioMedia } from './ReconEditor'

const EXPORT_WIDTH = 1080
const round = (value: number) => Math.round(value * 1000) / 1000

/** Foto final em 9:16 (1080 x 1920), sem EXIF/GPS, com o filtro tatico. "Inteiro" coloca a foto completa sobre um fundo desfocado dela mesma. */
async function exportPhoto(media: StudioMedia, framing: Framing, fit: Fit, filter: TacticalFilterId): Promise<Blob> {
  const bitmap = await createImageBitmap(media.file, { imageOrientation: 'from-image' })
  const width = EXPORT_WIDTH
  const height = Math.round(width / FRAME_ASPECT)
  const canvas = document.createElement('canvas')
  canvas.width = width; canvas.height = height
  const context = canvas.getContext('2d', { willReadFrequently: filter !== 'none' })!
  const cover = sourceRect(media.size, FRAME_ASPECT, fit === 'cover' ? framing : { x: 0.5, y: 0.5, zoom: 1 })
  if (fit === 'contain') {
    context.filter = 'blur(40px) brightness(0.6)'
    context.drawImage(bitmap, cover.x, cover.y, cover.width, cover.height, -40, -40, width + 80, height + 80)
    context.filter = 'none'
    const scale = Math.min(width / media.size.width, height / media.size.height)
    const w = media.size.width * scale
    const h = media.size.height * scale
    context.drawImage(bitmap, (width - w) / 2, (height - h) / 2, w, h)
  } else {
    context.drawImage(bitmap, cover.x, cover.y, cover.width, cover.height, 0, 0, width, height)
  }
  paintFilter(context, width, height, filter)
  bitmap.close()
  return new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Falha ao preparar a foto.')), 'image/jpeg', 0.9))
}

/** Video gravado no navegador: a duracao vem do cronometro (WebM gravado nao informa a duracao). */
function inspectRecorded(file: File, durationS: number): Promise<StudioMedia> {
  const url = URL.createObjectURL(file)
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.onloadedmetadata = () => resolve({ file, kind: 'VIDEO', url, size: { width: video.videoWidth || 1080, height: video.videoHeight || 1920 }, durationS })
    video.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Não foi possível abrir a gravação.')) }
    video.src = url
  })
}

/**
 * Criacao do Recon em tela cheia: camera ou galeria -> editor -> (confirmacao) -> publicacao.
 * O video comeca a subir assim que entra no editor; a publicacao so e dada como feita quando o servidor confirma.
 */
export function ReconStudio({ username, callsign, onClose }: { username: string; callsign: string; onClose: () => void }) {
  const [step, setStep] = useState<'camera' | 'editor' | 'confirm'>('camera')
  const [media, setMedia] = useState<StudioMedia | null>(null)
  const [elements, setElements] = useState<ReconElement[]>([])
  const [framing, setFraming] = useState<Framing>({ x: 0.5, y: 0.5, zoom: 1 })
  const [fit, setFit] = useState<Fit>('cover')
  const [filter, setFilter] = useState<TacticalFilterId>('none')
  const [audioMuted, setAudioMuted] = useState(false)
  const [dossierId, setDossierId] = useState('')
  const [dossiers, setDossiers] = useState<DossierSummary[] | null>(null)
  const [settings, setSettings] = useState(false)
  const [discard, setDiscard] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [taskId, setTaskId] = useState<string | null>(null)
  const [publishing, setPublishing] = useState(false)
  const published = useRef(false)
  const tasks = useSyncExternalStore(uploadStore.subscribe, uploadStore.getSnapshot)
  const task = tasks.find(item => item.id === taskId)

  useEffect(() => { ensureReconFonts() }, [])
  useEffect(() => { getDossiers(username).then(setDossiers).catch(() => setDossiers([])) }, [username])
  useEffect(() => () => { if (media) URL.revokeObjectURL(media.url) }, [media])
  // Fechar sem publicar cancela o envio em andamento (o servidor apaga o arquivo abandonado).
  useEffect(() => () => { if (taskId && !published.current) cancelUpload(taskId) }, [taskId])

  // So conta como publicado quando o servidor aceitou o Recon (no ar ou processando o video).
  useEffect(() => {
    if (!publishing || !task) return
    if (task.phase === 'processing' || task.phase === 'done') { published.current = true; onClose() }
    if (task.phase === 'error') setPublishing(false)
  }, [publishing, task, onClose])

  const dirty = Boolean(media)
  const requestClose = useCallback(() => { if (publishing) return; if (dirty) setDiscard(true); else onClose() }, [dirty, onClose, publishing])
  useBackToClose(step === 'confirm', () => setStep('editor'))

  const open = async (load: () => Promise<StudioMedia>) => {
    setError(''); setBusy(true)
    try {
      const next = await load()
      setMedia(next); setElements([]); setFraming({ x: 0.5, y: 0.5, zoom: 1 }); setFit('cover'); setFilter('none'); setAudioMuted(false)
      if (next.kind === 'VIDEO') setTaskId(startPostUpload(next.file, 'VIDEO', null, 'RECON'))
      setStep('editor')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível abrir o arquivo.')
    } finally {
      setBusy(false)
    }
  }

  const backToCamera = () => {
    if (taskId) { cancelUpload(taskId); setTaskId(null) }
    setMedia(null); setDiscard(false); setStep('camera')
  }

  const publish = async () => {
    if (!media || busy || publishing) return
    setError(''); setBusy(true)
    try {
      let id = taskId
      if (media.kind === 'IMAGE' && !id) { id = startPostUpload(await exportPhoto(media, framing, fit, filter), 'IMAGE', null, 'RECON'); setTaskId(id) }
      if (!id) return
      const details: ReconDetails = {
        elements, dossierId: dossierId || null,
        audioMuted: media.kind === 'VIDEO' ? audioMuted : undefined,
        complete: media.kind === 'VIDEO'
          ? { stripAudio: audioMuted, crop: fit === 'cover' ? { x: round(framing.x), y: round(framing.y), zoom: round(framing.zoom) } : null, filterCode: filterCode(filter) ?? '' }
          : undefined,
      }
      // Depois de um erro: os detalhes atualizados seguem junto e o envio continua de onde parou.
      submitPostDetails(id, details)
      retryUpload(id)
      setPublishing(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível preparar o Recon.')
    } finally {
      setBusy(false)
    }
  }

  // Foto ja exportada: mudar algo depois de um erro exige gerar a foto de novo.
  const edited = (apply: () => void) => {
    apply()
    if (media?.kind === 'IMAGE' && taskId && task?.phase === 'error') { cancelUpload(taskId); setTaskId(null) }
  }

  const mentions = elements.filter(element => element.type === 'MENTION').map(element => element.type === 'MENTION' ? `@${element.username}` : '')
  const avatar = (callsign || username).charAt(0).toUpperCase()
  const dossierName = dossiers?.find(dossier => dossier.id === dossierId)?.title

  return <div className="recon-studio" role="dialog" aria-modal="true" aria-label="Novo Recon">
    {step === 'camera' && <CameraScreen busy={busy} error={error} onClose={requestClose} onSettings={() => setSettings(true)}
      onPick={file => void open(() => inspect(file, 'RECON'))}
      onCapture={captured => void open(() => captured.kind === 'VIDEO' && captured.durationS ? inspectRecorded(captured.file, captured.durationS) : inspect(captured.file, 'RECON'))}/>}

    {step === 'editor' && media && <ReconEditor media={media} elements={elements} onElements={next => edited(() => setElements(next))}
      framing={framing} onFraming={next => edited(() => setFraming(next))} fit={fit} onFit={next => edited(() => setFit(next))}
      filter={filter} onFilter={next => edited(() => setFilter(next))}
      audioMuted={audioMuted} onAudioMuted={setAudioMuted} avatar={avatar} busy={busy || publishing}
      onClose={requestClose} onPublish={() => void publish()} onNext={() => setStep('confirm')} onSettings={() => setSettings(true)}/>}

    {step === 'confirm' && media && <div className="recon-confirm">
      <header className="recon-confirm-head">
        <button type="button" className="recon-round" onClick={() => setStep('editor')} disabled={publishing} aria-label="Voltar ao editor"><ArrowLeft/></button>
        <h2>Publicar Recon</h2>
      </header>
      <div className="recon-confirm-body">
        <div className="recon-confirm-preview"><div className="recon-preview-frame">
          <MediaFrame media={media} framing={framing} fit={fit} muted filter={filter}/>
          <ElementLayer elements={elements}/>
        </div></div>
        <ul className="recon-confirm-info">
          <li><UsersRound/><span><b>Seu Recon</b><small>Seus seguidores{mentions.length ? ' e os operadores marcados' : ''} podem ver.</small></span></li>
          <li><Clock3/><span><b>24 horas</b><small>{dossierName ? `Depois fica guardado no Dossiê "${dossierName}".` : 'Depois some do feed.'}</small></span></li>
          {mentions.length > 0 && <li><span className="recon-at">@</span><span><b>Marcados</b><small>{mentions.join(', ')} — cada um recebe um aviso.</small></span></li>}
        </ul>
        <button type="button" className="recon-link" onClick={() => setSettings(true)} disabled={publishing}>Configurações do Recon</button>
      </div>
      <div className="recon-confirm-foot">
        <button type="button" className="recon-next recon-next--wide" disabled={busy || publishing} onClick={() => void publish()}><Send/> Publicar</button>
      </div>
    </div>}

    {(publishing || task?.phase === 'error') && step !== 'camera' && <div className={`recon-publishing ${task?.phase === 'error' ? 'is-error' : ''}`} role="status" aria-live="polite">
      {task?.phase === 'error' ? <>
        <b>Não foi possível publicar</b>
        <span>{task.message ?? 'Verifique a conexão e tente de novo.'} Sua edição foi mantida.</span>
        <div><button type="button" className="recon-pill recon-pill--primary" onClick={() => void publish()}>Tentar novamente</button></div>
      </> : <>
        <b>{task?.phase === 'uploading' ? `Enviando… ${Math.round((task.progress ?? 0) * 100)}%` : 'Publicando…'}</b>
        <i className="recon-publishing-bar"><span style={{ width: `${Math.round((task?.progress ?? 0) * 100)}%` }}/></i>
        <button type="button" className="recon-link" onClick={() => { published.current = true; onClose() }}>Continuar navegando (o envio segue em segundo plano)</button>
      </>}
    </div>}

    {settings && <SettingsSheet dossiers={dossiers} dossierId={dossierId} onDossier={setDossierId} onClose={() => setSettings(false)}/>}

    {discard && <div className="recon-sheet-backdrop" onClick={() => setDiscard(false)}>
      <section className="recon-dialog" role="alertdialog" aria-modal="true" aria-labelledby="recon-discard-title" onClick={event => event.stopPropagation()}>
        <h2 id="recon-discard-title">Descartar Recon?</h2>
        <p>Se sair agora, a foto ou vídeo e as edições serão perdidos.</p>
        <button type="button" className="recon-pill recon-pill--primary" onClick={() => setDiscard(false)}>Continuar editando</button>
        <button type="button" className="recon-pill recon-pill--danger" onClick={() => { if (step === 'camera') onClose(); else { backToCamera(); onClose() } }}>Descartar</button>
      </section>
    </div>}
  </div>
}
