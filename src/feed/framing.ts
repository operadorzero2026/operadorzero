/** Enquadramento: centro visivel da midia (0..1) e zoom sobre o "cobrir o quadro". */
export type Framing = { x: number; y: number; zoom: number }
export type Box = { width: number; height: number }

export const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

/** Converte o enquadramento no retangulo da midia original que aparece no quadro (usado para recortar a foto). */
export function sourceRect(media: Box, aspect: number, framing: Framing) {
  const baseWidth = media.width / media.height > aspect ? media.height * aspect : media.width
  const width = baseWidth / framing.zoom
  const height = width / aspect
  const x = clamp(framing.x * media.width - width / 2, 0, media.width - width)
  const y = clamp(framing.y * media.height - height / 2, 0, media.height - height)
  return { x, y, width, height }
}
