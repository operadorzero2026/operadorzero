/**
 * Filtros taticos. A foto e filtrada aqui no aparelho e sobe pronta; o video sobe original com o codigo do filtro e o
 * servidor aplica o efeito equivalente no ffmpeg (services/api/.../media/VideoFilters.java). Para criar um filtro novo:
 * uma entrada em TACTICAL_FILTERS, o efeito em paint() e o trecho de ffmpeg com o mesmo codigo no servidor.
 */
export type TacticalFilterId = 'none' | 'night_vision' | 'thermal' | 'scope' | 'helmet'

export const TACTICAL_FILTERS: { id: TacticalFilterId; label: string }[] = [
  { id: 'none', label: 'Normal' },
  { id: 'night_vision', label: 'Visão Noturna' },
  { id: 'thermal', label: 'Térmica' },
  { id: 'scope', label: 'Luneta' },
  { id: 'helmet', label: 'Capacete' },
]

/** Codigo enviado ao servidor (nada para "Normal"). */
export const filterCode = (id: TacticalFilterId) => id === 'none' ? null : id

const HUD = '#9EF01A'

/** Tabela da Termica: luminancia -> paleta "ferro" (mesma formula do lutrgb do servidor). */
const THERMAL = (() => {
  const table = new Uint8ClampedArray(256 * 3)
  for (let v = 0; v < 256; v++) {
    table[v * 3] = 3 * v - 128
    table[v * 3 + 1] = 3 * v - 420
    table[v * 3 + 2] = (v < 80 ? v * 2.6 : Math.max(0, 208 - (v - 80) * 2.4)) + (v > 215 ? (v - 215) * 6 : 0)
  }
  return table
})()

/** Visao Noturna: gamma 1.4 + contraste 1.3 + brilho, como no eq do servidor. */
const NIGHT = (() => {
  const table = new Uint8ClampedArray(256)
  for (let v = 0; v < 256; v++) table[v] = (Math.pow(v / 255, 1 / 1.4) * 255 - 128) * 1.3 + 128 + 13
  return table
})()

const luma = (data: Uint8ClampedArray, i: number) => (data[i] * 77 + data[i + 1] * 150 + data[i + 2] * 29) >> 8

function vignette(ctx: CanvasRenderingContext2D, w: number, h: number, strength: number, inner = 0.35) {
  const radius = Math.hypot(w, h) / 2
  const gradient = ctx.createRadialGradient(w / 2, h / 2, radius * inner, w / 2, h / 2, radius)
  gradient.addColorStop(0, 'rgba(0,0,0,0)')
  gradient.addColorStop(1, `rgba(0,0,0,${strength})`)
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, w, h)
}

/**
 * Aplica o filtro ao que ja esta desenhado no canvas (w x h).
 * seconds: tempo do video (anima o HUD); clock: hora inicial do relogio do HUD.
 */
export function paintFilter(ctx: CanvasRenderingContext2D, w: number, h: number, id: TacticalFilterId, seconds = 0, clock = new Date()) {
  if (id === 'none' || w < 2 || h < 2) return
  if (id === 'night_vision' || id === 'thermal' || id === 'helmet') {
    const image = ctx.getImageData(0, 0, w, h)
    const data = image.data
    for (let i = 0; i < data.length; i += 4) {
      if (id === 'night_vision') {
        const v = NIGHT[luma(data, i)] + (Math.random() * 48 - 24)
        data[i] = v * 0.22; data[i + 1] = v; data[i + 2] = v * 0.32
      } else if (id === 'thermal') {
        const v = luma(data, i) * 3
        data[i] = THERMAL[v]; data[i + 1] = THERMAL[v + 1]; data[i + 2] = THERMAL[v + 2]
      } else {
        // Capacete: saturacao 0.85 e contraste 1.08 (eq do servidor).
        const y = luma(data, i)
        for (let c = 0; c < 3; c++) data[i + c] = ((y + (data[i + c] - y) * 0.85) - 128) * 1.08 + 128
      }
    }
    ctx.putImageData(image, 0, 0)
  }
  if (id === 'night_vision') vignette(ctx, w, h, 0.85)
  if (id === 'scope') drawScope(ctx, w, h)
  if (id === 'helmet') drawHelmet(ctx, w, h, seconds, clock)
}

function drawScope(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const r = Math.min(w, h) * 0.46
  vignette(ctx, w, h, 0.55, 0.15)
  ctx.save()
  ctx.fillStyle = 'rgb(0,0,0)'
  ctx.beginPath()
  ctx.rect(0, 0, w, h)
  ctx.arc(w / 2, h / 2, r, 0, Math.PI * 2)
  ctx.fill('evenodd')
  const line = Math.max(2, Math.round(Math.min(w, h) / 270))
  ctx.fillStyle = 'rgba(0,0,0,0.85)'
  ctx.fillRect(0, h / 2 - line / 2, w, line)
  ctx.fillRect(w / 2 - line / 2, 0, line, h)
  ctx.restore()
}

function drawHelmet(ctx: CanvasRenderingContext2D, w: number, h: number, seconds: number, clock: Date) {
  const t = Math.max(0, seconds)
  const bar = Math.max(2, Math.round(w / 360))
  const len = w * 0.08
  ctx.save()
  ctx.fillStyle = HUD
  ctx.globalAlpha = 0.9
  // Cantos: (x, y, direcao horizontal, direcao vertical)
  for (const [x, y, dx, dy] of [[w * 0.06, h * 0.06, 1, 1], [w * 0.94, h * 0.06, -1, 1], [w * 0.06, h * 0.94, 1, -1], [w * 0.94, h * 0.94, -1, -1]]) {
    ctx.fillRect(dx > 0 ? x : x - len, dy > 0 ? y : y - bar, len, bar)
    ctx.fillRect(dx > 0 ? x : x - bar, dy > 0 ? y : y - len, bar, len)
  }
  ctx.fillRect(w / 2 - w * 0.03, h / 2 - bar / 2, w * 0.06, bar)
  ctx.fillRect(w / 2 - bar / 2, h / 2 - w * 0.03, bar, w * 0.06)
  ctx.globalAlpha = 1
  ctx.textBaseline = 'top'
  const font = (size: number) => `${Math.round(h * size)}px "DejaVu Sans Mono", ui-monospace, Menlo, Consolas, monospace`
  const heading = String(Math.floor((45 + t * 12) % 360)).padStart(3, '0')
  ctx.font = font(0.028); ctx.textAlign = 'center'
  ctx.fillText(`HDG ${heading}`, w / 2, h * 0.07)
  const now = new Date(clock.getTime() + t * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  ctx.font = font(0.026); ctx.textAlign = 'left'
  ctx.fillText(`${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`, w * 0.06, h * 0.88)
  ctx.font = font(0.03); ctx.textAlign = 'right'
  ctx.fillText(`${String(Math.max(0, 30 - Math.floor(t * 2))).padStart(2, '0')}/30`, w * 0.94, h * 0.88)
  ctx.restore()
}
