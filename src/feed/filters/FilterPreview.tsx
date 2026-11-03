import { useEffect, useLayoutEffect, useRef } from 'react'
import { paintFilter, TACTICAL_FILTERS, type TacticalFilterId } from './tacticalFilters'
import './filters.css'

export type SourceRegion = { x: number; y: number; width: number; height: number }

/** Lado maior da previa: processar pixels em tela cheia a cada quadro pesaria no celular. */
const PREVIEW_MAX = 540

/**
 * Previa ao vivo do filtro tatico. Desenha a midia (recorte "cover" ou inteira em "contain") num canvas e aplica o filtro
 * a cada quadro do video. videoElement reaproveita um video que ja esta tocando (mesmo som e mesmo tempo).
 */
export function FilterPreview({ kind, url, videoElement, region, fit = 'cover', filter, className }: {
  kind: 'IMAGE' | 'VIDEO'; url: string; videoElement?: HTMLVideoElement | null; region?: SourceRegion | null; fit?: 'cover' | 'contain'
  filter: TacticalFilterId; className?: string
}) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const own = useRef<HTMLVideoElement>(null)
  const image = useRef<HTMLImageElement | null>(null)
  const clock = useRef(new Date())
  const redraw = useRef<(() => void) | null>(null)
  const settings = useRef({ region, fit, filter })
  useLayoutEffect(() => { settings.current = { region, fit, filter } }, [region, fit, filter])

  useEffect(() => {
    const element = canvas.current
    if (!element) return
    const ctx = element.getContext('2d', { willReadFrequently: true })
    if (!ctx) return
    let frame = 0
    let stopped = false
    const source = (): CanvasImageSource | null => kind === 'IMAGE' ? image.current : videoElement ?? own.current
    const draw = () => {
      const media = source()
      const box = element.getBoundingClientRect()
      const scale = Math.min(PREVIEW_MAX / Math.max(box.width, box.height, 1), Math.min(window.devicePixelRatio || 1, 2))
      const w = Math.max(2, Math.round(box.width * scale))
      const h = Math.max(2, Math.round(box.height * scale))
      if (element.width !== w || element.height !== h) { element.width = w; element.height = h }
      const sw = media instanceof HTMLVideoElement ? media.videoWidth : media instanceof HTMLImageElement ? media.naturalWidth : 0
      const sh = media instanceof HTMLVideoElement ? media.videoHeight : media instanceof HTMLImageElement ? media.naturalHeight : 0
      ctx.fillStyle = '#000'
      ctx.fillRect(0, 0, w, h)
      if (media && sw && sh) {
        const { region: r, fit: mode, filter: id } = settings.current
        if (mode === 'contain') {
          const fitScale = Math.min(w / sw, h / sh)
          ctx.drawImage(media, (w - sw * fitScale) / 2, (h - sh * fitScale) / 2, sw * fitScale, sh * fitScale)
        } else {
          const rect = r ?? { x: 0, y: 0, width: sw, height: sh }
          ctx.drawImage(media, rect.x, rect.y, rect.width, rect.height, 0, 0, w, h)
        }
        paintFilter(ctx, w, h, id, media instanceof HTMLVideoElement ? media.currentTime : 0, clock.current)
      }
      if (!stopped && kind === 'VIDEO') frame = requestAnimationFrame(draw)
    }
    redraw.current = draw
    if (kind === 'IMAGE') {
      const img = new Image()
      img.onload = () => { image.current = img; draw() }
      img.src = url
    } else {
      frame = requestAnimationFrame(draw)
    }
    const observer = new ResizeObserver(() => { if (kind === 'IMAGE') draw() })
    observer.observe(element)
    return () => { stopped = true; redraw.current = null; cancelAnimationFrame(frame); observer.disconnect() }
  }, [kind, url, videoElement])

  // Foto: redesenha quando o filtro ou o recorte mudam (o video ja redesenha a cada quadro).
  useEffect(() => {
    if (kind === 'IMAGE' && image.current) redraw.current?.()
  }, [kind, filter, region, fit])

  return <div className={`tactical-preview ${className ?? ''}`}>
    <canvas ref={canvas} aria-label="Prévia com filtro"/>
    {kind === 'VIDEO' && !videoElement && <video ref={own} src={url} muted loop autoPlay playsInline aria-hidden="true"/>}
  </div>
}

/** Fileira de filtros para escolher com um toque. */
export function FilterPicker({ value, onChange }: { value: TacticalFilterId; onChange: (id: TacticalFilterId) => void }) {
  return <div className="tactical-picker" role="radiogroup" aria-label="Filtros táticos">
    {TACTICAL_FILTERS.map(item => <button key={item.id} type="button" role="radio" aria-checked={value === item.id}
      className={`tactical-chip tactical-chip--${item.id} ${value === item.id ? 'active' : ''}`} onClick={() => onChange(item.id)}>
      <i aria-hidden="true"/>{item.label}
    </button>)}
  </div>
}
