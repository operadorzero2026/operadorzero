import { CameraOff, ImagePlus, RefreshCcw, Settings, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'

export const MAX_RECON_VIDEO_S = 30
const HOLD_MS = 280

type Captured = { file: File; kind: 'IMAGE' | 'VIDEO'; durationS?: number }

/** Formato de gravacao: MP4 onde existe (Safari, Chrome recente), senao WebM; o servidor converte para H.264. */
function recorderType() {
  if (typeof MediaRecorder === 'undefined') return null
  const options = ['video/mp4;codecs=avc1,mp4a.40.2', 'video/mp4', 'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
  return options.find(type => MediaRecorder.isTypeSupported(type)) ?? ''
}

/**
 * Abertura do Recon: camera ao vivo (traseira primeiro), toque fotografa, segurar grava ate 30 s.
 * Sem permissao ou sem camera, continua possivel escolher da galeria.
 */
export function CameraScreen({ busy, error, onPick, onCapture, onClose, onSettings }: {
  busy: boolean; error: string; onPick: (file: File) => void; onCapture: (captured: Captured) => void; onClose: () => void; onSettings: () => void
}) {
  const video = useRef<HTMLVideoElement>(null)
  const gallery = useRef<HTMLInputElement>(null)
  const stream = useRef<MediaStream | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const holdTimer = useRef<number | undefined>(undefined)
  const pressed = useRef(false)
  const startedAt = useRef(0)
  const [facing, setFacing] = useState<'environment' | 'user'>('environment')
  const [status, setStatus] = useState<'starting' | 'ready' | 'denied' | 'unavailable'>('starting')
  const [recording, setRecording] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [canFlip, setCanFlip] = useState(false)
  const [mode, setMode] = useState<'PHOTO' | 'VIDEO'>('PHOTO')

  const stop = useCallback(() => {
    stream.current?.getTracks().forEach(track => track.stop())
    stream.current = null
  }, [])

  useEffect(() => {
    let cancelled = false
    if (!navigator.mediaDevices?.getUserMedia) { setStatus('unavailable'); return }
    setStatus('starting')
    const video916 = { facingMode: { ideal: facing }, width: { ideal: 1080 }, height: { ideal: 1920 }, aspectRatio: { ideal: 9 / 16 } }
    // Microfone so e necessario para gravar; se negado, a camera continua funcionando para fotos e video sem som.
    navigator.mediaDevices.getUserMedia({ video: video916, audio: true })
      .catch(() => navigator.mediaDevices.getUserMedia({ video: video916, audio: false }))
      .then(media => {
        if (cancelled) { media.getTracks().forEach(track => track.stop()); return }
        stop()
        stream.current = media
        if (video.current) { video.current.srcObject = media; void video.current.play().catch(() => undefined) }
        setStatus('ready')
        navigator.mediaDevices.enumerateDevices().then(devices => setCanFlip(devices.filter(device => device.kind === 'videoinput').length > 1)).catch(() => undefined)
      })
      .catch((error: unknown) => {
        if (cancelled) return
        const name = error instanceof DOMException ? error.name : ''
        setStatus(name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable')
      })
    return () => { cancelled = true }
  }, [facing, stop])
  // Libera a camera ao sair desta tela (luz do aparelho apaga).
  useEffect(() => () => { window.clearTimeout(holdTimer.current); if (recorder.current?.state === 'recording') recorder.current.stop(); stop() }, [stop])

  useEffect(() => {
    if (!recording) return
    const timer = window.setInterval(() => {
      const seconds = (performance.now() - startedAt.current) / 1000
      setElapsed(seconds)
      if (seconds >= MAX_RECON_VIDEO_S) recorder.current?.stop()
    }, 100)
    return () => window.clearInterval(timer)
  }, [recording])

  const takePhoto = () => {
    const element = video.current
    if (!element || !element.videoWidth) return
    const canvas = document.createElement('canvas')
    canvas.width = element.videoWidth; canvas.height = element.videoHeight
    const context = canvas.getContext('2d')!
    // A previa da camera frontal e espelhada; a foto sai como a pessoa viu.
    if (facing === 'user') { context.translate(canvas.width, 0); context.scale(-1, 1) }
    context.drawImage(element, 0, 0)
    canvas.toBlob(blob => { if (blob) onCapture({ file: new File([blob], 'recon.jpg', { type: 'image/jpeg' }), kind: 'IMAGE' }) }, 'image/jpeg', 0.92)
  }

  const startRecording = () => {
    const type = recorderType()
    if (!stream.current || type === null) return
    try {
      const media = new MediaRecorder(stream.current, type ? { mimeType: type, videoBitsPerSecond: 6_000_000 } : undefined)
      chunks.current = []
      media.ondataavailable = event => { if (event.data.size) chunks.current.push(event.data) }
      media.onstop = () => {
        setRecording(false)
        const duration = (performance.now() - startedAt.current) / 1000
        const mime = media.mimeType || type || 'video/webm'
        const blob = new Blob(chunks.current, { type: mime.split(';')[0] })
        if (duration < 0.5 || !blob.size) return
        onCapture({ file: new File([blob], mime.includes('mp4') ? 'recon.mp4' : 'recon.webm', { type: blob.type }), kind: 'VIDEO', durationS: Math.min(duration, MAX_RECON_VIDEO_S) })
      }
      media.start(250)
      recorder.current = media
      startedAt.current = performance.now()
      setElapsed(0)
      setRecording(true)
    } catch {
      setStatus('unavailable')
    }
  }

  const down = () => {
    if (status !== 'ready' || busy) return
    // Modo VIDEO: toque comeca e toque termina (segurar tambem funciona).
    if (mode === 'VIDEO') {
      if (recorder.current?.state === 'recording') recorder.current.stop(); else startRecording()
      return
    }
    pressed.current = true
    holdTimer.current = window.setTimeout(() => { if (pressed.current) startRecording() }, HOLD_MS)
  }
  const up = () => {
    if (!pressed.current) return
    pressed.current = false
    window.clearTimeout(holdTimer.current)
    if (recorder.current?.state === 'recording') recorder.current.stop()
    else if (!recording) takePhoto()
  }

  const progress = Math.min(1, elapsed / MAX_RECON_VIDEO_S)
  return <div className="recon-camera">
    <div className="recon-camera-view">
      <video ref={video} className={facing === 'user' ? 'is-mirrored' : ''} muted playsInline autoPlay aria-label="Visualização da câmera"/>
      {status !== 'ready' && <div className="recon-camera-message" role="status">
        {status === 'starting' ? <span>Abrindo a câmera…</span> : <>
          <CameraOff/>
          <b>{status === 'denied' ? 'Sem acesso à câmera' : 'Câmera indisponível'}</b>
          <span>{status === 'denied' ? 'Libere a câmera nas configurações do navegador ou escolha uma foto ou vídeo da galeria.' : 'Escolha uma foto ou um vídeo da galeria.'}</span>
          <button type="button" className="recon-pill" onClick={() => gallery.current?.click()}><ImagePlus/> Abrir galeria</button>
        </>}
      </div>}
    </div>
    <header className="recon-top">
      <button type="button" className="recon-round" onClick={onClose} aria-label="Fechar"><X/></button>
      {recording && <span className="recon-rec" role="timer" aria-live="off"><i/>{Math.floor(elapsed)}s / {MAX_RECON_VIDEO_S}s</span>}
      <button type="button" className="recon-round" onClick={onSettings} aria-label="Configurações do Recon"><Settings/></button>
    </header>
    {error && <p className="recon-toast recon-toast--error" role="alert">{error}</p>}
    <div className="recon-camera-controls">
      <input ref={gallery} type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif,video/mp4,video/quicktime,video/webm,image/*,video/*" hidden
        onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) onPick(file) }}/>
      <button type="button" className="recon-round recon-round--big" onClick={() => gallery.current?.click()} disabled={busy || recording} aria-label="Escolher da galeria"><ImagePlus/></button>
      <button type="button" className={`recon-shutter ${recording ? 'is-recording' : ''}`} disabled={status !== 'ready' || busy}
        style={{ ['--progress' as string]: `${progress * 360}deg` }}
        onPointerDown={down} onPointerUp={up} onPointerLeave={() => { if (recording) up() }} onPointerCancel={up}
        onKeyDown={event => {
          if (event.key !== ' ' && event.key !== 'Enter') return
          event.preventDefault()
          if (mode === 'PHOTO') takePhoto(); else if (recorder.current?.state === 'recording') recorder.current.stop(); else startRecording()
        }}
        onContextMenu={event => event.preventDefault()}
        aria-label="Toque para fotografar; segure para gravar vídeo"><span/></button>
      <button type="button" className="recon-round recon-round--big" disabled={!canFlip || recording || status !== 'ready'}
        onClick={() => setFacing(current => current === 'environment' ? 'user' : 'environment')} aria-label="Alternar câmera"><RefreshCcw/></button>
      <div className="recon-modes" role="radiogroup" aria-label="Modo da câmera">
        {(['PHOTO', 'VIDEO'] as const).map(value => <button key={value} type="button" role="radio" aria-checked={mode === value} disabled={recording}
          className={mode === value ? 'active' : ''} onClick={() => setMode(value)}>{value === 'PHOTO' ? 'FOTO' : 'VÍDEO'}</button>)}
      </div>
      <p className="recon-camera-hint">{busy ? 'Abrindo…' : recording ? (mode === 'VIDEO' ? 'Toque para terminar' : 'Solte para terminar')
        : mode === 'VIDEO' ? `Toque para gravar (até ${MAX_RECON_VIDEO_S} s)` : 'Toque: foto · Segure: vídeo'}</p>
    </div>
  </div>
}
