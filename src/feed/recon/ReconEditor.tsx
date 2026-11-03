import { ArrowRight, Copy, Maximize2, Minimize2, MoreHorizontal, Pencil, Settings, Smile, Trash2, Type, Volume2, VolumeX, X } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import type { OperatorSummary } from '../../api'
import { clamp, sourceRect, type Box, type Framing } from '../framing'
import { FRAME_ASPECT, MAX_ELEMENTS, MAX_MENTIONS, MENTION_STYLES, newId, nextZ, type DrawingElement, type ReconElement, type Stroke, type TextElement } from './composition'
import { ElementLayer } from './ElementLayer'
import { BottomSheet, DrawPad, StickerSheet, TextEditor, type TextDraft } from './EditorTools'

export type StudioMedia = { file: File; kind: 'IMAGE' | 'VIDEO'; url: string; size: Box; durationS?: number }
export type Fit = 'cover' | 'contain'

const SNAP = 0.015
const TAP_MS = 280
const TRASH_RADIUS = 56
const DEFAULT_TEXT: TextDraft = { text: '', font: 'MODERN', color: '#FFFFFF', align: 'CENTER', background: 'NONE', size: 0.07 }

type Point = { x: number; y: number }
type Gesture = {
  target: 'element' | 'media'; id?: string; pointers: Map<number, Point>; start: Map<number, Point>
  element?: ReconElement; framing: Framing; moved: boolean; at: number
}

/** Posicao e tamanho da midia dentro do quadro para um enquadramento (a midia sempre cobre o quadro em "cover"). */
export function mediaPlacement(media: Box, box: Box, framing: Framing) {
  const scale = box.width ? Math.max(box.width / media.width, box.height / media.height) * framing.zoom : 0
  const rect = sourceRect(media, FRAME_ASPECT, framing)
  return { scale, style: { width: media.width * scale, height: media.height * scale, transform: `translate(${-rect.x * scale}px, ${-rect.y * scale}px)` } }
}

export function MediaFrame({ media, framing, fit, muted, videoRef }: { media: StudioMedia; framing: Framing; fit: Fit; muted: boolean; videoRef?: React.RefObject<HTMLVideoElement | null> }) {
  const frame = useRef<HTMLDivElement>(null)
  const [box, setBox] = useState<Box>({ width: 0, height: 0 })
  useLayoutEffect(() => {
    const element = frame.current
    if (!element) return
    const measure = () => setBox({ width: element.clientWidth, height: element.clientHeight })
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const placement = mediaPlacement(media.size, box, framing)
  const cover = fit === 'cover'
  return <div ref={frame} className={`recon-media-frame ${cover ? '' : 'is-contain'}`}>
    {!cover && media.kind === 'IMAGE' && <img className="recon-media-backdrop" src={media.url} alt="" aria-hidden="true"/>}
    {media.kind === 'IMAGE'
      ? <img src={media.url} alt="" draggable={false} style={cover ? placement.style : undefined}/>
      : <video ref={videoRef} src={media.url} loop playsInline autoPlay muted={muted} style={cover ? placement.style : undefined}/>}
  </div>
}

/**
 * Editor do Recon: a midia ocupa o quadro 9:16 e os elementos ficam por cima.
 * Gestos: arrastar move, pinca muda o tamanho, girar dois dedos gira, arrastar ate a lixeira apaga,
 * dois toques no texto editam e um toque na mencao troca o estilo. Sem elemento, os gestos enquadram a midia.
 */
export function ReconEditor({ media, elements, onElements, framing, onFraming, fit, onFit, audioMuted, onAudioMuted, avatar, busy, onClose, onPublish, onNext, onSettings }: {
  media: StudioMedia; elements: ReconElement[]; onElements: (next: ReconElement[]) => void
  framing: Framing; onFraming: (framing: Framing) => void; fit: Fit; onFit: (fit: Fit) => void
  audioMuted: boolean; onAudioMuted: (muted: boolean) => void; avatar: string; busy: boolean
  onClose: () => void; onPublish: () => void; onNext: () => void; onSettings: () => void
}) {
  const stage = useRef<HTMLDivElement>(null)
  const trash = useRef<HTMLDivElement>(null)
  const video = useRef<HTMLVideoElement>(null)
  const gesture = useRef<Gesture | null>(null)
  const lastTap = useRef<{ id: string; at: number } | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [dragging, setDragging] = useState<string | null>(null)
  const [overTrash, setOverTrash] = useState(false)
  const [guides, setGuides] = useState({ v: false, h: false })
  const [tool, setTool] = useState<null | 'text' | 'draw' | 'add' | 'more'>(null)
  const [editingText, setEditingText] = useState<TextElement | null>(null)
  const [notice, setNotice] = useState('')
  const latest = useRef({ elements, framing })
  useEffect(() => { latest.current = { elements, framing } }, [elements, framing])

  // A previa do video segue a escolha de audio; o navegador pode exigir um toque antes de tocar com som.
  useEffect(() => {
    const element = video.current
    if (!element) return
    element.muted = audioMuted
    element.play().catch(() => { element.muted = true; void element.play().catch(() => undefined) })
  }, [audioMuted])

  useEffect(() => { if (!notice) return; const timer = window.setTimeout(() => setNotice(''), 2600); return () => window.clearTimeout(timer) }, [notice])

  const update = useCallback((id: string, patch: Partial<ReconElement>) => {
    onElements(latest.current.elements.map(element => element.id === id ? { ...element, ...patch } as ReconElement : element))
  }, [onElements])
  const remove = (id: string) => { onElements(elements.filter(element => element.id !== id)); setSelected(null) }
  const add = (element: ReconElement) => {
    if (elements.length >= MAX_ELEMENTS) { setNotice(`Use no máximo ${MAX_ELEMENTS} elementos.`); return }
    onElements([...elements, element]); setSelected(element.id)
  }

  const stageBox = () => stage.current!.getBoundingClientRect()
  const nearTrash = (point: Point) => {
    const box = trash.current?.getBoundingClientRect()
    if (!box) return false
    return Math.hypot(point.x - (box.left + box.width / 2), point.y - (box.top + box.height / 2)) < TRASH_RADIUS
  }

  const begin = (event: ReactPointerEvent<HTMLElement>, element?: ReconElement) => {
    if (tool) return
    event.stopPropagation()
    const point = { x: event.clientX, y: event.clientY }
    const current = gesture.current
    // Segundo dedo entra no gesto que ja esta em andamento (pinca/rotacao do mesmo alvo).
    if (current && current.pointers.size < 2) {
      current.pointers.set(event.pointerId, point)
      current.start = new Map(current.pointers)
      current.element = current.id ? latest.current.elements.find(item => item.id === current.id) : undefined
      current.framing = latest.current.framing
      return
    }
    if (current) return
    if (element) setSelected(element.id)
    gesture.current = { target: element ? 'element' : 'media', id: element?.id, pointers: new Map([[event.pointerId, point]]), start: new Map([[event.pointerId, point]]),
      element, framing: latest.current.framing, moved: false, at: performance.now() }
  }

  useEffect(() => {
    const move = (event: PointerEvent) => {
      const current = gesture.current
      if (!current || !current.pointers.has(event.pointerId) || !stage.current) return
      current.pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
      const box = stageBox()
      const now = [...current.pointers.values()]
      const before = [...current.start.values()]
      const mid = (points: Point[]) => ({ x: points.reduce((sum, p) => sum + p.x, 0) / points.length, y: points.reduce((sum, p) => sum + p.y, 0) / points.length })
      const m0 = mid(before)
      const m1 = mid(now)
      if (!current.moved && Math.hypot(m1.x - m0.x, m1.y - m0.y) < 6 && now.length < 2) return
      current.moved = true
      const two = now.length === 2 && before.length === 2
      const ratio = two ? Math.hypot(now[1].x - now[0].x, now[1].y - now[0].y) / Math.max(1, Math.hypot(before[1].x - before[0].x, before[1].y - before[0].y)) : 1
      const turn = two ? (Math.atan2(now[1].y - now[0].y, now[1].x - now[0].x) - Math.atan2(before[1].y - before[0].y, before[1].x - before[0].x)) * 180 / Math.PI : 0
      if (current.target === 'element' && current.element) {
        const start = current.element
        let x = clamp(start.x + (m1.x - m0.x) / box.width, 0, 1)
        let y = clamp(start.y + (m1.y - m0.y) / box.height, 0, 1)
        const v = Math.abs(x - 0.5) < SNAP
        const h = Math.abs(y - 0.5) < SNAP
        if (v) x = 0.5
        if (h) y = 0.5
        setGuides({ v, h })
        setDragging(start.id)
        setOverTrash(!two && nearTrash(m1))
        if (start.type !== 'DRAWING') update(start.id, { x, y, scale: clamp(start.scale * ratio, 0.2, 6), rotation: start.rotation + turn })
      } else if (current.target === 'media' && fit === 'cover') {
        const placement = mediaPlacement(media.size, { width: box.width, height: box.height }, current.framing)
        if (!placement.scale) return
        const zoom = clamp(current.framing.zoom * ratio, 1, 3)
        onFraming({ zoom, x: clamp(current.framing.x - (m1.x - m0.x) / placement.scale / media.size.width, 0, 1),
          y: clamp(current.framing.y - (m1.y - m0.y) / placement.scale / media.size.height, 0, 1) })
      }
    }
    const end = (event: PointerEvent) => {
      const current = gesture.current
      if (!current || !current.pointers.has(event.pointerId)) return
      current.pointers.delete(event.pointerId)
      if (current.pointers.size > 0) {
        // Um dedo saiu da pinca: o outro continua arrastando a partir daqui.
        current.start = new Map(current.pointers)
        current.element = current.id ? latest.current.elements.find(item => item.id === current.id) : undefined
        current.framing = latest.current.framing
        return
      }
      gesture.current = null
      setGuides({ v: false, h: false }); setDragging(null)
      const dropped = current.target === 'element' && current.id && nearTrash({ x: event.clientX, y: event.clientY }) && current.moved
      setOverTrash(false)
      if (dropped) { onElements(latest.current.elements.filter(element => element.id !== current.id)); setSelected(null); return }
      if (current.moved) return
      if (current.target === 'media') { setSelected(null); return }
      const element = latest.current.elements.find(item => item.id === current.id)
      if (!element) return
      const tapNow = performance.now()
      const double = lastTap.current?.id === element.id && tapNow - lastTap.current.at < 350
      lastTap.current = { id: element.id, at: tapNow }
      if (performance.now() - current.at > TAP_MS * 3) return
      if (element.type === 'TEXT' && double) { setEditingText(element); setTool('text') }
      else if (element.type === 'MENTION') {
        const index = MENTION_STYLES.findIndex(option => option.value === element.style)
        update(element.id, { style: MENTION_STYLES[(index + 1) % MENTION_STYLES.length].value })
      }
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', end)
    window.addEventListener('pointercancel', end)
    return () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', end); window.removeEventListener('pointercancel', end) }
  }, [fit, media.size, onElements, onFraming, update])

  const wheel = (event: React.WheelEvent) => {
    const element = elements.find(item => item.id === selected)
    if (!element || element.type === 'DRAWING') return
    update(element.id, { scale: clamp(element.scale * (event.deltaY < 0 ? 1.08 : 0.93), 0.2, 6) })
  }

  const drawing = elements.find((element): element is DrawingElement => element.type === 'DRAWING')
  const mentionCount = elements.filter(element => element.type === 'MENTION').length
  const selectedElement = elements.find(element => element.id === selected && element.type !== 'DRAWING')

  const saveText = (draft: TextDraft) => {
    const text = draft.text.trim()
    const target = editingText
    setTool(null); setEditingText(null)
    if (target) { if (!text) remove(target.id); else update(target.id, { ...draft, text }); return }
    if (text) add({ ...draft, text, id: newId(), type: 'TEXT', x: 0.5, y: 0.42, scale: 1, rotation: 0, z: nextZ(elements) })
  }
  const saveDrawing = (strokes: Stroke[]) => {
    setTool(null)
    const others = elements.filter(element => element.type !== 'DRAWING')
    // O desenho fica abaixo de textos e adesivos.
    onElements(strokes.length ? [{ id: drawing?.id ?? newId(), type: 'DRAWING', x: 0.5, y: 0.5, scale: 1, rotation: 0, z: 0, strokes }, ...others] : others)
  }
  const addMention = (operator: OperatorSummary) => {
    setTool(null)
    if (elements.some(element => element.type === 'MENTION' && element.username.toLowerCase() === operator.username.toLowerCase())) { setNotice(`@${operator.username} já está marcado.`); return }
    add({ id: newId(), type: 'MENTION', username: operator.username, style: 'ZERO', x: 0.5, y: 0.6, scale: 1, rotation: 0, z: nextZ(elements) })
  }

  return <div className="recon-editor">
    <div ref={stage} className="recon-frame recon-editor-stage" onPointerDown={event => begin(event)} onWheel={wheel}>
      <MediaFrame media={media} framing={framing} fit={fit} muted={audioMuted} videoRef={video}/>
      <ElementLayer elements={elements} selectedId={selected} hiddenId={editingText?.id} onElementPointerDown={(event, element) => begin(event, element)}/>
      {guides.v && <i className="recon-guide recon-guide--v" aria-hidden="true"/>}
      {guides.h && <i className="recon-guide recon-guide--h" aria-hidden="true"/>}
    </div>

    {!tool && !dragging && <header className="recon-top recon-editor-top">
      <button type="button" className="recon-round" onClick={onClose} aria-label="Fechar"><X/></button>
      <div className="recon-toolbar">
        {media.kind === 'VIDEO' && <button type="button" className="recon-round" onClick={() => onAudioMuted(!audioMuted)} aria-pressed={audioMuted}
          aria-label={audioMuted ? 'Ativar áudio' : 'Silenciar áudio'}>{audioMuted ? <VolumeX/> : <Volume2/>}</button>}
        <button type="button" className="recon-round recon-round--text" onClick={() => { setEditingText(null); setTool('text') }} aria-label="Adicionar texto">Aa</button>
        <button type="button" className="recon-round" onClick={() => setTool('add')} aria-label="Adesivos e menções"><Smile/></button>
        <button type="button" className="recon-round" onClick={() => setTool('draw')} aria-label="Desenhar"><Pencil/></button>
        <button type="button" className="recon-round" onClick={() => setTool('more')} aria-label="Mais opções"><MoreHorizontal/></button>
      </div>
    </header>}

    {notice && <p className="recon-toast" role="status">{notice}</p>}

    {dragging && <div ref={trash} className={`recon-trash ${overTrash ? 'is-over' : ''}`} aria-hidden="true"><Trash2/></div>}

    {!tool && !dragging && selectedElement && <div className="recon-selection" role="group" aria-label="Ajustar elemento selecionado">
      <label><span>Tamanho</span><input type="range" min={0.2} max={6} step={0.05} value={selectedElement.scale} onChange={event => update(selectedElement.id, { scale: Number(event.target.value) })}/></label>
      <label><span>Giro</span><input type="range" min={-180} max={180} step={1} value={Math.round(((selectedElement.rotation % 360) + 540) % 360 - 180)} onChange={event => update(selectedElement.id, { rotation: Number(event.target.value) })}/></label>
      <div>
        {selectedElement.type === 'TEXT' && <button type="button" onClick={() => { setEditingText(selectedElement); setTool('text') }} aria-label="Editar texto"><Type/></button>}
        {selectedElement.type === 'MENTION' && <button type="button" onClick={() => {
          const index = MENTION_STYLES.findIndex(option => option.value === selectedElement.style)
          update(selectedElement.id, { style: MENTION_STYLES[(index + 1) % MENTION_STYLES.length].value })
        }}>Estilo</button>}
        {selectedElement.type !== 'MENTION' && <button type="button" onClick={() => add({ ...selectedElement, id: newId(), x: clamp(selectedElement.x + 0.05, 0, 1), y: clamp(selectedElement.y + 0.05, 0, 1), z: nextZ(elements) })} aria-label="Duplicar"><Copy/></button>}
        <button type="button" onClick={() => remove(selectedElement.id)} aria-label="Excluir elemento"><Trash2/></button>
      </div>
    </div>}

    {!tool && !dragging && <div className="recon-editor-bottom">
      <button type="button" className="recon-publish-own" disabled={busy} onClick={onPublish}><span className="recon-publish-avatar">{avatar}</span> Seu Recon</button>
      <button type="button" className="recon-next" disabled={busy} onClick={onNext}>Avançar <ArrowRight/></button>
    </div>}

    {tool === 'text' && <TextEditor initial={editingText ? { text: editingText.text, font: editingText.font, color: editingText.color, align: editingText.align, background: editingText.background, size: editingText.size } : DEFAULT_TEXT}
      onDone={saveText} onCancel={() => { setTool(null); setEditingText(null) }}/>}
    {tool === 'draw' && <DrawPad initial={drawing?.strokes ?? []} onDone={saveDrawing} onCancel={() => setTool(null)}/>}
    {tool === 'add' && <StickerSheet mentionsLeft={MAX_MENTIONS - mentionCount} onMention={addMention}
      onSticker={emoji => { setTool(null); add({ id: newId(), type: 'STICKER', emoji, x: 0.5, y: 0.5, scale: 1, rotation: 0, z: nextZ(elements) }) }} onClose={() => setTool(null)}/>}
    {tool === 'more' && <BottomSheet title="Mais opções" onClose={() => setTool(null)}>
      <div className="recon-more">
        <button type="button" onClick={() => { onFit(fit === 'cover' ? 'contain' : 'cover'); setTool(null) }}>
          {fit === 'cover' ? <><Minimize2/><span><b>Mostrar inteiro</b><small>A foto ou o vídeo aparece completo, sem corte.</small></span></>
            : <><Maximize2/><span><b>Preencher a tela</b><small>Ocupa todo o quadro; arraste e use a pinça para enquadrar.</small></span></>}
        </button>
        <button type="button" onClick={() => { setTool(null); onSettings() }}><Settings/><span><b>Configurações</b><small>Quem pode ver e salvar no Dossiê.</small></span></button>
      </div>
    </BottomSheet>}
  </div>
}
