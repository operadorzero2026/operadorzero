import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { clamp, sourceRect, type Box, type Framing } from './framing'

/** Arrastar para posicionar e controle de zoom; a midia sempre cobre o quadro inteiro. */
export function FrameEditor({ src, kind, media, aspect, framing, onChange }: { src: string; kind: 'IMAGE' | 'VIDEO'; media: Box; aspect: number; framing: Framing; onChange: (framing: Framing) => void }) {
  const frame = useRef<HTMLDivElement>(null)
  const [box, setBox] = useState<Box>({ width: 0, height: 0 })
  const drag = useRef<{ x: number; y: number; start: Framing } | null>(null)
  useLayoutEffect(() => {
    const element = frame.current
    if (!element) return
    const measure = () => setBox({ width: element.clientWidth, height: element.clientHeight })
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [aspect])
  const scale = box.width ? Math.max(box.width / media.width, box.height / media.height) * framing.zoom : 0
  const rect = sourceRect(media, aspect, framing)
  const left = -rect.x * scale
  const top = -rect.y * scale
  // Mantem o centro dentro dos limites quando o zoom ou a proporcao mudam.
  useEffect(() => {
    const centered = { ...framing, x: (rect.x + rect.width / 2) / media.width, y: (rect.y + rect.height / 2) / media.height }
    if (Math.abs(centered.x - framing.x) > 0.001 || Math.abs(centered.y - framing.y) > 0.001) onChange(centered)
  }, [framing, media, onChange, rect.height, rect.width, rect.x, rect.y])
  const down = (event: ReactPointerEvent) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY, start: framing }
  }
  const move = (event: ReactPointerEvent) => {
    if (!drag.current || !scale) return
    const dx = (event.clientX - drag.current.x) / scale / media.width
    const dy = (event.clientY - drag.current.y) / scale / media.height
    onChange({ ...drag.current.start, x: clamp(drag.current.start.x - dx, 0, 1), y: clamp(drag.current.start.y - dy, 0, 1) })
  }
  const style = { width: media.width * scale, height: media.height * scale, transform: `translate(${left}px, ${top}px)` }
  return <div className="frame-editor">
    <div ref={frame} className="frame-editor-window" style={{ aspectRatio: String(aspect) }} onPointerDown={down} onPointerMove={move}
      onPointerUp={() => { drag.current = null }} onPointerCancel={() => { drag.current = null }} role="application" aria-label="Arraste para enquadrar">
      {kind === 'IMAGE' ? <img src={src} alt="" draggable={false} style={style}/> : <video src={src} muted loop autoPlay playsInline style={style}/>}
      <span className="frame-editor-grid" aria-hidden="true"/>
    </div>
    <label className="frame-editor-zoom"><span>Zoom</span>
      <input type="range" min={1} max={3} step={0.01} value={framing.zoom} onChange={event => onChange({ ...framing, zoom: Number(event.target.value) })} aria-label="Zoom do enquadramento"/>
    </label>
  </div>
}
