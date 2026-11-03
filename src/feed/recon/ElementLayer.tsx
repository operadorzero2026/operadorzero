import './recon.css'
import type { CSSProperties, PointerEvent as ReactPointerEvent } from 'react'
import { contrastOn, fontOf, type DrawingElement, type ReconElement, type Stroke } from './composition'

/** Altura do quadro em unidades do SVG do desenho (largura 1000, proporcao 9:16). */
const SVG_W = 1000
const SVG_H = SVG_W * 16 / 9

export function strokePath(stroke: Stroke) {
  const points = stroke.points
  if (points.length < 2) return ''
  let path = `M${(points[0] * SVG_W).toFixed(1)} ${(points[1] * SVG_H).toFixed(1)}`
  for (let i = 2; i < points.length; i += 2) path += `L${(points[i] * SVG_W).toFixed(1)} ${(points[i + 1] * SVG_H).toFixed(1)}`
  // Um toque so vira um ponto visivel.
  if (points.length === 2) path += `l0.1 0`
  return path
}

export function Strokes({ strokes }: { strokes: Stroke[] }) {
  return <svg className="recon-drawing" viewBox={`0 0 ${SVG_W} ${SVG_H}`} preserveAspectRatio="none" aria-hidden="true">
    {strokes.map((stroke, index) => <path key={index} d={strokePath(stroke)} stroke={stroke.color} strokeWidth={stroke.width * SVG_W}
      fill="none" strokeLinecap="round" strokeLinejoin="round"/>)}
  </svg>
}

/** Aparencia de um elemento; o tamanho usa a largura do quadro (cqw), igual no editor e no visualizador. */
export function ElementBody({ element }: { element: Exclude<ReconElement, DrawingElement> }) {
  if (element.type === 'TEXT') {
    const font = fontOf(element.font)
    const solid = element.background === 'SOLID'
    const style: CSSProperties = {
      fontFamily: font.family, fontWeight: font.weight, textTransform: font.upper ? 'uppercase' : undefined,
      fontSize: `${element.size * 100}cqw`, textAlign: element.align.toLowerCase() as CSSProperties['textAlign'],
      color: solid ? contrastOn(element.color) : element.color,
      background: solid ? element.color : element.background === 'SOFT' ? 'rgba(0,0,0,.55)' : undefined,
    }
    return <span className={`recon-el-text ${element.background === 'NONE' ? 'recon-el-text--bare' : ''}`} style={style}>{element.text}</span>
  }
  if (element.type === 'MENTION') {
    return <span className={`recon-el-mention recon-el-mention--${element.style.toLowerCase()} ${element.available === false ? 'is-unavailable' : ''}`}>@{element.username}</span>
  }
  return <span className="recon-el-sticker">{element.emoji}</span>
}

export const elementTransform = (element: ReconElement): CSSProperties => ({
  left: `${element.x * 100}%`, top: `${element.y * 100}%`, zIndex: element.z,
  transform: `translate(-50%, -50%) rotate(${element.rotation}deg) scale(${element.scale})`,
})

/**
 * Camada de elementos. No visualizador so as mencoes recebem toque (com prioridade sobre avancar/voltar);
 * no editor cada elemento entrega o toque para o controlador de gestos.
 */
export function ElementLayer({ elements, onMention, onElementPointerDown, selectedId, hiddenId }: {
  elements: ReconElement[]
  onMention?: (element: Extract<ReconElement, { type: 'MENTION' }>) => void
  onElementPointerDown?: (event: ReactPointerEvent<HTMLElement>, element: ReconElement) => void
  selectedId?: string | null
  hiddenId?: string | null
}) {
  return <div className="recon-elements">
    {elements.map(element => {
      if (element.type === 'DRAWING') return <div key={element.id} className="recon-el recon-el--drawing" style={{ zIndex: element.z }}><Strokes strokes={element.strokes}/></div>
      if (element.id === hiddenId) return null
      const editable = Boolean(onElementPointerDown)
      const tappable = !editable && element.type === 'MENTION' && onMention
      const className = `recon-el ${editable || tappable ? 'recon-el--interactive' : ''} ${selectedId === element.id ? 'is-selected' : ''}`
      if (tappable) {
        return <button key={element.id} type="button" className={className} style={elementTransform(element)}
          aria-label={element.available === false ? `@${element.username} (perfil indisponível)` : `Abrir perfil de @${element.username}`}
          onPointerDown={event => event.stopPropagation()} onPointerUp={event => event.stopPropagation()}
          onClick={event => { event.stopPropagation(); onMention(element) }}>
          <ElementBody element={element}/>
        </button>
      }
      return <div key={element.id} className={className} style={elementTransform(element)} data-element-id={element.id}
        onPointerDown={editable ? event => onElementPointerDown!(event, element) : undefined}>
        <ElementBody element={element}/>
      </div>
    })}
  </div>
}
