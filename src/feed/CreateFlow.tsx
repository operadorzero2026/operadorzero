import { ArrowLeft, Camera, ChevronDown, ImagePlus, Send, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { getFields, getTeamWorkspace, type VenueField } from '../api'
import { FrameEditor } from './FrameEditor'
import { sourceRect, type Box, type Framing } from './framing'
import { cancelUpload, startPostUpload, submitPostDetails } from './uploadQueue'

const MB = 1024 * 1024
const LIMITS = { IMAGE: 20 * MB, VIDEO: 300 * MB }
const MAX_VIDEO_S = { POST: 90, RECON: 30 }
const PHOTO_ASPECTS = [{ label: '4:5', value: 4 / 5 }, { label: '1:1', value: 1 }, { label: '9:16', value: 9 / 16 }]
const VIDEO_ASPECT = 9 / 16
const CAPTION_MAX = 2200
const OVERLAY_MAX = 140

export type CreateMode = 'POST' | 'RECON'

type Picked = { file: File; kind: 'IMAGE' | 'VIDEO'; url: string; size: Box; durationS?: number }

async function inspect(file: File, mode: CreateMode): Promise<Picked> {
  const kind = file.type.startsWith('video/') ? 'VIDEO' : file.type.startsWith('image/') || /\.(heic|heif)$/i.test(file.name) ? 'IMAGE' : null
  if (!kind) throw new Error('Escolha uma foto ou um vídeo.')
  if (file.size > LIMITS[kind]) throw new Error(kind === 'VIDEO' ? 'O vídeo pode ter no máximo 300 MB.' : 'A foto pode ter no máximo 20 MB.')
  if (kind === 'IMAGE') {
    // Decodifica respeitando a orientacao do EXIF; HEIC so abre onde o navegador suporta (Safari).
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' }).catch(() => {
      throw new Error('Este navegador não abre esse formato. Envie a foto em JPG ou PNG.')
    })
    const size = { width: bitmap.width, height: bitmap.height }
    bitmap.close()
    return { file, kind, url: URL.createObjectURL(file), size }
  }
  const url = URL.createObjectURL(file)
  const meta = await new Promise<{ size: Box; durationS: number }>((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => resolve({ size: { width: video.videoWidth, height: video.videoHeight }, durationS: video.duration })
    video.onerror = () => reject(new Error('Não foi possível abrir este vídeo.'))
    video.src = url
  })
  if (meta.durationS > MAX_VIDEO_S[mode] + 0.5) {
    URL.revokeObjectURL(url)
    throw new Error(mode === 'RECON' ? 'O vídeo do Recon pode ter no máximo 30 segundos.' : 'O vídeo do post pode ter no máximo 90 segundos.')
  }
  return { file, kind, url, size: meta.size, durationS: meta.durationS }
}

/** Recorta e reencoda a foto no aparelho (1080 px de largura): sai sem EXIF/GPS e na proporcao escolhida. */
async function exportPhoto(picked: Picked, aspect: number, framing: Framing): Promise<Blob> {
  const bitmap = await createImageBitmap(picked.file, { imageOrientation: 'from-image' })
  const rect = sourceRect(picked.size, aspect, framing)
  const width = Math.min(1080, Math.round(rect.width))
  const height = Math.round(width / aspect)
  const canvas = document.createElement('canvas')
  canvas.width = width; canvas.height = height
  canvas.getContext('2d')!.drawImage(bitmap, rect.x, rect.y, rect.width, rect.height, 0, 0, width, height)
  bitmap.close()
  return new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Falha ao preparar a foto.')), 'image/jpeg', 0.9))
}

export function CreateFlow({ mode = 'POST', onClose }: { mode?: CreateMode; onClose: () => void }) {
  const recon = mode === 'RECON'
  const [step, setStep] = useState<'pick' | 'frame' | 'details'>('pick')
  const [picked, setPicked] = useState<Picked | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [aspect, setAspect] = useState(recon ? VIDEO_ASPECT : PHOTO_ASPECTS[0].value)
  const [overlay, setOverlay] = useState('')
  const [reframe, setReframe] = useState(false)
  const [framing, setFraming] = useState<Framing>({ x: 0.5, y: 0.5, zoom: 1 })
  const [taskId, setTaskId] = useState<string | null>(null)
  const [caption, setCaption] = useState('')
  const [fields, setFields] = useState<VenueField[] | null>(null)
  const [fieldId, setFieldId] = useState('')
  const [team, setTeam] = useState<{ id: string; name: string } | null>(null)
  const [tagTeam, setTagTeam] = useState(false)
  const [tagged, setTagged] = useState('')
  const gallery = useRef<HTMLInputElement>(null)
  const camera = useRef<HTMLInputElement>(null)
  const published = useRef(false)

  useEffect(() => () => { if (picked) URL.revokeObjectURL(picked.url) }, [picked])
  // Fechar sem publicar descarta o envio em andamento (o servidor apaga o arquivo abandonado).
  useEffect(() => () => { if (taskId && !published.current) cancelUpload(taskId) }, [taskId])

  const choose = async (file?: File) => {
    if (!file) return
    setError(''); setBusy(true)
    try {
      const result = await inspect(file, mode)
      setPicked(result)
      setFraming({ x: 0.5, y: 0.5, zoom: 1 })
      setReframe(false)
      setStep('frame')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível abrir o arquivo.')
    } finally {
      setBusy(false)
    }
  }

  const confirmFrame = async () => {
    if (!picked) return
    setError(''); setBusy(true)
    try {
      const id = picked.kind === 'IMAGE'
        ? startPostUpload(await exportPhoto(picked, aspect, framing), 'IMAGE', null, mode)
        : startPostUpload(picked.file, 'VIDEO', reframe ? { x: round(framing.x), y: round(framing.y), zoom: round(framing.zoom) } : null, mode)
      setTaskId(id)
      setStep('details')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível preparar o arquivo.')
    } finally {
      setBusy(false)
    }
  }

  const loadOptions = useCallback(() => {
    if (fields) return
    getFields().then(result => setFields(result.items)).catch(() => setFields([]))
    getTeamWorkspace().then(result => setTeam(result.team ? { id: result.team.id, name: result.team.name } : null)).catch(() => setTeam(null))
  }, [fields])

  const publishPost = () => {
    if (!taskId) return
    published.current = true
    if (recon) { submitPostDetails(taskId, { overlayText: overlay.trim() || null }); onClose(); return }
    submitPostDetails(taskId, {
      caption: caption.trim() || undefined,
      fieldId: fieldId || null,
      teamId: tagTeam && team ? team.id : null,
      taggedUsernames: tagged.split(/[\s,]+/).map(name => name.replace(/^@/, '')).filter(Boolean).slice(0, 10),
    })
    onClose()
  }

  const back = () => {
    if (step === 'details' && taskId) { cancelUpload(taskId); setTaskId(null) }
    setStep(step === 'details' ? 'frame' : 'pick')
  }

  return <div className="create-flow" role="dialog" aria-modal="true" aria-labelledby="create-flow-title">
    <header>
      {step === 'pick' ? <button type="button" className="feed-icon-button" onClick={onClose} aria-label="Fechar"><X/></button>
        : <button type="button" className="feed-icon-button" onClick={back} aria-label="Voltar"><ArrowLeft/></button>}
      <h2 id="create-flow-title">{step === 'pick' ? (recon ? 'Novo Recon' : 'Novo post') : step === 'frame' ? 'Enquadrar' : recon ? 'Texto do Recon' : 'Descrição'}</h2>
      {step === 'frame' && <button type="button" className="button button--small" disabled={busy} onClick={() => void confirmFrame()}>Avançar</button>}
      {step === 'details' && <button type="button" className="button button--small" onClick={publishPost}><Send/> Publicar</button>}
    </header>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}

    {step === 'pick' && <div className="create-pick">
      <input ref={gallery} type="file" accept="image/*,video/*" hidden onChange={event => void choose(event.target.files?.[0])}/>
      <input ref={camera} type="file" accept="image/*,video/*" capture="environment" hidden onChange={event => void choose(event.target.files?.[0])}/>
      <button type="button" className="create-choice" disabled={busy} onClick={() => gallery.current?.click()}><ImagePlus/><span><b>GALERIA</b><small>{recon ? 'Foto ou vídeo de até 30 s. Some em 24 horas.' : 'Foto (até 20 MB) ou vídeo (até 90 s e 300 MB).'}</small></span></button>
      <button type="button" className="create-choice" disabled={busy} onClick={() => camera.current?.click()}><Camera/><span><b>CÂMERA</b><small>Grave ou fotografe agora.</small></span></button>
      {busy && <p className="create-notice" role="status">Abrindo arquivo…</p>}
    </div>}

    {step === 'frame' && picked && <div className="create-frame">
      {picked.kind === 'IMAGE' && recon ? <p className="create-notice">Recons usam a tela inteira (9:16).</p>
        : picked.kind === 'IMAGE'
        ? <div className="create-segmented" role="radiogroup" aria-label="Proporção">{PHOTO_ASPECTS.map(option => <button type="button" key={option.label} role="radio"
            aria-checked={aspect === option.value} className={aspect === option.value ? 'active' : ''} onClick={() => setAspect(option.value)}>{option.label}</button>)}</div>
        : <div className="create-segmented" role="radiogroup" aria-label="Enquadramento do vídeo">
            <button type="button" role="radio" aria-checked={!reframe} className={!reframe ? 'active' : ''} onClick={() => setReframe(false)}>Original</button>
            <button type="button" role="radio" aria-checked={reframe} className={reframe ? 'active' : ''} onClick={() => setReframe(true)}>Enquadrar 9:16</button>
          </div>}
      {picked.kind === 'VIDEO' && !reframe
        ? <div className="create-original"><video src={picked.url} muted loop autoPlay playsInline/><p>O vídeo aparece inteiro; o que não for 9:16 fica com fundo desfocado.</p></div>
        : <FrameEditor src={picked.url} kind={picked.kind} media={picked.size} aspect={picked.kind === 'IMAGE' ? aspect : VIDEO_ASPECT} framing={framing} onChange={setFraming}/>}
    </div>}

    {step === 'details' && recon && <div className="create-details">
      <label className="create-caption"><span>Texto sobre o Recon (opcional)</span>
        <input value={overlay} maxLength={OVERLAY_MAX} onChange={event => setOverlay(event.target.value)} placeholder="Ex.: Saindo para a patrulha"/>
        <small>{overlay.length}/{OVERLAY_MAX}</small>
      </label>
      <p className="create-notice">O Recon fica visível por 24 horas para quem segue você.</p>
    </div>}

    {step === 'details' && !recon && <div className="create-details">
      <label className="create-caption"><span className="sr-only">Descrição</span>
        <textarea value={caption} maxLength={CAPTION_MAX} rows={5} placeholder="Conte o que rolou. Use #hashtags e @menções." onChange={event => setCaption(event.target.value)}/>
        <small>{caption.length}/{CAPTION_MAX}</small>
      </label>
      <details className="create-more" onToggle={event => { if ((event.target as HTMLDetailsElement).open) loadOptions() }}>
        <summary>Mais opções <ChevronDown/></summary>
        <label><span>Campo</span>
          <select value={fieldId} onChange={event => setFieldId(event.target.value)}>
            <option value="">Nenhum</option>
            {fields?.map(field => <option key={field.id} value={field.id}>{field.name} · {field.city}/{field.stateCode}</option>)}
          </select>
        </label>
        {team && <label className="create-check"><input type="checkbox" checked={tagTeam} onChange={event => setTagTeam(event.target.checked)}/> Marcar minha equipe ({team.name})</label>}
        <label><span>Operadores</span><input value={tagged} onChange={event => setTagged(event.target.value)} placeholder="@usuario, @outro" autoCapitalize="none"/></label>
      </details>
      <p className="create-notice">O arquivo continua subindo enquanto você escreve e depois que publicar.</p>
    </div>}
  </div>
}

const round = (value: number) => Math.round(value * 1000) / 1000
