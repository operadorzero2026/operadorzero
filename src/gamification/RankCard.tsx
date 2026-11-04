import { Award } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getMyRank, getOperatorRank, type MyRank, type RankView } from '../api'
import { RankInsignia } from '../feed/RankInsignia'
import './rank.css'

const when = (value: string) => new Date(value).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })

/** Patente no perfil: insignia, XP, barra ate a proxima patente e (no proprio perfil) os ultimos ganhos. */
export function RankCard({ username, own }: { username: string; own: boolean }) {
  const [rank, setRank] = useState<RankView | null>(null)
  const [recent, setRecent] = useState<MyRank['recent']>([])

  useEffect(() => {
    let active = true
    const load = own ? getMyRank().then(mine => { if (active) { setRank(mine.rank); setRecent(mine.recent) } })
      : getOperatorRank(username).then(view => { if (active) setRank(view) })
    load.catch(() => { if (active) setRank(null) })
    return () => { active = false }
  }, [username, own])

  if (!rank) return null
  const missing = rank.nextMinXp != null ? Math.max(0, rank.nextMinXp - rank.totalXp) : 0
  return <section className="oz-profile-card rank-card" aria-label="Patente">
    <h2>PATENTE</h2>
    <div className="rank-card-head">
      <RankInsignia code={rank.rankCode} size={52}/>
      <div><b>{rank.rankName}</b><small>{rank.totalXp.toLocaleString('pt-BR')} XP</small></div>
    </div>
    <div className="rank-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(rank.progress * 100)}
      aria-label={rank.nextRankName ? `Progresso até ${rank.nextRankName}` : 'Patente máxima'}>
      <i style={{ width: `${Math.round(rank.progress * 100)}%` }}/>
    </div>
    <p className="rank-next">{rank.nextRankName ? <>Faltam <b>{missing.toLocaleString('pt-BR')} XP</b> para <b>{rank.nextRankName}</b></> : <><Award/> Patente máxima</>}</p>
    {own && recent.length > 0 && <details className="rank-recent">
      <summary>Últimos ganhos de XP</summary>
      <ul>{recent.map((item, index) => <li key={`${item.at}-${index}`}>
        <span>{item.description}<small>{when(item.at)}</small></span><em className={item.xp < 0 ? 'is-negative' : ''}>{item.xp > 0 ? '+' : ''}{item.xp}</em>
      </li>)}</ul>
    </details>}
  </section>
}
