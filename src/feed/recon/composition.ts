/**
 * Composicao do Recon: elementos sobre a midia em coordenadas relativas ao quadro 9:16.
 * x/y sao o centro do elemento (0..1); tamanhos de texto e traco sao fracoes da largura do quadro.
 * O mesmo formato e usado no editor, na previa e no visualizador, e o servidor revalida tudo.
 */
export type TextFont = 'CLASSIC' | 'MODERN' | 'BOLD' | 'TYPEWRITER' | 'HANDWRITTEN'
export type TextAlign = 'LEFT' | 'CENTER' | 'RIGHT'
export type TextBackground = 'NONE' | 'SOLID' | 'SOFT'
export type MentionStyle = 'MINIMAL' | 'HIGHLIGHT' | 'ZERO'
export type Stroke = { color: string; width: number; points: number[] }

type Base = { id: string; x: number; y: number; scale: number; rotation: number; z: number }
export type TextElement = Base & { type: 'TEXT'; text: string; font: TextFont; color: string; align: TextAlign; background: TextBackground; size: number }
export type MentionElement = Base & { type: 'MENTION'; username: string; style: MentionStyle; operatorId?: string | null; available?: boolean | null }
export type StickerElement = Base & { type: 'STICKER'; emoji: string }
export type DrawingElement = Base & { type: 'DRAWING'; strokes: Stroke[] }
export type ReconElement = TextElement | MentionElement | StickerElement | DrawingElement

export const FRAME_ASPECT = 9 / 16
export const MAX_ELEMENTS = 40
export const MAX_MENTIONS = 10
export const MAX_TEXT = 300

export const FONTS: { value: TextFont; label: string; family: string; weight: number; upper?: boolean }[] = [
  { value: 'CLASSIC', label: 'Clássica', family: "'Playfair Display', Georgia, serif", weight: 700 },
  { value: 'MODERN', label: 'Moderna', family: 'Inter, system-ui, sans-serif', weight: 600 },
  { value: 'BOLD', label: 'Negrito', family: "'Barlow Condensed', Impact, sans-serif", weight: 800, upper: true },
  { value: 'TYPEWRITER', label: 'Máquina', family: "'Special Elite', 'Courier New', monospace", weight: 400 },
  { value: 'HANDWRITTEN', label: 'Manuscrita', family: "Caveat, 'Segoe Script', cursive", weight: 700 },
]
export const fontOf = (value: TextFont) => FONTS.find(font => font.value === value) ?? FONTS[1]

export const COLORS = ['#FFFFFF', '#111111', '#C9A227', '#C9CB73', '#7A8450', '#E4572E', '#F2C14E', '#4EA5D9', '#7BC950', '#E05297', '#8E7DBE', '#A0522D']

export const MENTION_STYLES: { value: MentionStyle; label: string }[] = [
  { value: 'ZERO', label: 'Operador Zero' },
  { value: 'HIGHLIGHT', label: 'Destaque' },
  { value: 'MINIMAL', label: 'Minimalista' },
]

/** Mesma lista do servidor (ReconComposition.STICKERS). */
export const STICKERS = ['🎯', '💥', '🔥', '💀', '🪖', '🔫', '🧨', '🛡️', '⚔️', '🎖️', '🏆', '📍', '🚁', '🌲',
  '🌙', '☀️', '🌧️', '👊', '🤝', '💪', '😎', '😂', '😱', '🫡', '👀', '✅', '❌', '⚠️', '📸', '🎥']

export const newId = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4)
export const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))
export const nextZ = (elements: ReconElement[]) => elements.reduce((max, element) => Math.max(max, element.z), 0) + 1

/** Cor de texto legivel sobre um fundo solido. */
export function contrastOn(hex: string) {
  const value = parseInt(hex.slice(1), 16)
  const luminance = (0.299 * ((value >> 16) & 255) + 0.587 * ((value >> 8) & 255) + 0.114 * (value & 255)) / 255
  return luminance > 0.6 ? '#111111' : '#FFFFFF'
}

let fontsRequested = false
/** Fontes do editor (Clássica, Máquina, Manuscrita) so carregam quando um Recon com texto aparece. */
export function ensureReconFonts() {
  if (fontsRequested || typeof document === 'undefined') return
  fontsRequested = true
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = 'https://fonts.googleapis.com/css2?family=Caveat:wght@700&family=Playfair+Display:wght@700&family=Special+Elite&display=swap'
  document.head.appendChild(link)
}

/** Para enviar: so os campos que o servidor aceita (operatorId/available sao definidos por ele). */
export function toPayload(elements: ReconElement[]) {
  return elements.map(element => element.type === 'MENTION'
    ? { id: element.id, type: element.type, x: element.x, y: element.y, scale: element.scale, rotation: element.rotation, z: element.z, username: element.username, style: element.style }
    : element)
}
