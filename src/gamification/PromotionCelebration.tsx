import { useEffect, useState } from 'react'
import { getMyRank, markPromotionSeen, type RankView } from '../api'
import { RankInsignia } from '../feed/RankInsignia'
import './rank.css'

/**
 * Comemoracao de promocao: aparece uma vez por patente nova (o servidor guarda que ja foi vista). Confere ao abrir o app
 * e quando a pessoa volta para a aba.
 */
export function PromotionCelebration() {
  const [rank, setRank] = useState<RankView | null>(null)

  useEffect(() => {
    let active = true
    const check = () => {
      if (document.visibilityState !== 'visible') return
      getMyRank().then(mine => { if (active && mine.promotionPending) setRank(mine.rank) }).catch(() => undefined)
    }
    check()
    document.addEventListener('visibilitychange', check)
    return () => { active = false; document.removeEventListener('visibilitychange', check) }
  }, [])

  if (!rank) return null
  const close = () => { setRank(null); void markPromotionSeen().catch(() => undefined) }
  return <div className="promotion-backdrop" role="presentation" onClick={close}>
    <section className="promotion" role="alertdialog" aria-modal="true" aria-labelledby="promotion-title" onClick={event => event.stopPropagation()}>
      <div className="promotion-rays" aria-hidden="true"/>
      <div className="promotion-insignia"><RankInsignia code={rank.rankCode} size={112}/></div>
      <small>PROMOÇÃO</small>
      <h2 id="promotion-title">{rank.rankName}</h2>
      <p>Sua atividade no Operador Zero rendeu uma nova patente. {rank.totalXp.toLocaleString('pt-BR')} XP acumulados.</p>
      {rank.nextRankName && <p className="promotion-next">Próxima: {rank.nextRankName} ({rank.nextMinXp?.toLocaleString('pt-BR')} XP)</p>}
      <button type="button" className="button" autoFocus onClick={close}>Continuar</button>
    </section>
  </div>
}
