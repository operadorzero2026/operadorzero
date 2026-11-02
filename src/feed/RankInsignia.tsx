// Insignias estilizadas (nao copiam as oficiais): divisas para pracas, losango para subtenente, estrelas para oficiais.
const ranks: Record<string, { name: string; chevrons?: number; diamond?: boolean; stars?: number }> = {
  RECRUTA: { name: 'Recruta' },
  SOLDADO: { name: 'Soldado', chevrons: 1 },
  CABO: { name: 'Cabo', chevrons: 2 },
  TERCEIRO_SARGENTO: { name: '3º Sargento', chevrons: 3 },
  SEGUNDO_SARGENTO: { name: '2º Sargento', chevrons: 4 },
  PRIMEIRO_SARGENTO: { name: '1º Sargento', chevrons: 5 },
  SUBTENENTE: { name: 'Subtenente', diamond: true },
  ASPIRANTE: { name: 'Aspirante', stars: 1, diamond: true },
  TENENTE: { name: 'Tenente', stars: 1 },
  CAPITAO: { name: 'Capitão', stars: 2 },
  MAJOR: { name: 'Major', stars: 3 },
  CORONEL: { name: 'Coronel', stars: 4 },
}

const star = (cx: number, cy: number, r: number) => Array.from({ length: 10 }, (_, i) => {
  const angle = -Math.PI / 2 + i * Math.PI / 5
  const radius = i % 2 ? r * 0.45 : r
  return `${(cx + radius * Math.cos(angle)).toFixed(2)},${(cy + radius * Math.sin(angle)).toFixed(2)}`
}).join(' ')

export function RankInsignia({ code, size = 16 }: { code: string; size?: number }) {
  const rank = ranks[code] ?? ranks.RECRUTA
  const chevrons = Math.min(rank.chevrons ?? 0, 3)
  const bars = Math.max((rank.chevrons ?? 0) - 3, 0)
  const stars = rank.stars ?? 0
  return <svg className="rank-insignia" width={size} height={size} viewBox="0 0 16 16" role="img" aria-label={`Patente: ${rank.name}`}>
    <title>{rank.name}</title>
    <circle cx="8" cy="8" r="7.5" fill="#14170f" stroke="var(--feed-gold, #c9a227)" strokeWidth="1"/>
    {Array.from({ length: chevrons }, (_, i) => <polyline key={`c${i}`} points={`4.5,${6 + i * 2.4} 8,${4 + i * 2.4} 11.5,${6 + i * 2.4}`} fill="none" stroke="var(--feed-gold, #c9a227)" strokeWidth="1.3" strokeLinejoin="round"/>)}
    {Array.from({ length: bars }, (_, i) => <line key={`b${i}`} x1="5" x2="11" y1={12.2 + i * 1.5} y2={12.2 + i * 1.5} stroke="var(--feed-gold, #c9a227)" strokeWidth="1"/>)}
    {rank.diamond && !stars && <polygon points="8,3.5 11.5,8 8,12.5 4.5,8" fill="var(--feed-gold, #c9a227)"/>}
    {rank.diamond && stars > 0 && <polygon points="8,9 10.5,11.5 8,14 5.5,11.5" fill="none" stroke="var(--feed-gold, #c9a227)" strokeWidth="1"/>}
    {stars === 1 && <polygon points={star(8, rank.diamond ? 6 : 8, 3.6)} fill="var(--feed-gold, #c9a227)"/>}
    {stars === 2 && [5, 11].map(cx => <polygon key={cx} points={star(cx, 8, 2.6)} fill="var(--feed-gold, #c9a227)"/>)}
    {stars === 3 && [[8, 5], [5, 10.2], [11, 10.2]].map(([cx, cy]) => <polygon key={`${cx}${cy}`} points={star(cx, cy, 2.3)} fill="var(--feed-gold, #c9a227)"/>)}
    {stars === 4 && [[5, 5.4], [11, 5.4], [5, 10.8], [11, 10.8]].map(([cx, cy]) => <polygon key={`${cx}${cy}`} points={star(cx, cy, 2.2)} fill="var(--feed-gold, #c9a227)"/>)}
  </svg>
}
