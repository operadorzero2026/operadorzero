import { Medal } from 'lucide-react'
import { useEffect, useState } from 'react'
import { decoratePost, getDecorationBalance, type DecorationBalance, type FeedPost } from '../api'
import { Sheet } from './FeedSheets'

const resetLabel = (iso: string) => new Date(iso).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' })

/** Confirmacao com o saldo da semana: condecoracoes sao escassas e nao voltam ao serem usadas. */
export function DecorateSheet({ post, onClose, onDecorated }: { post: FeedPost; onClose: () => void; onDecorated: (decorations: number, balance: DecorationBalance) => void }) {
  const [balance, setBalance] = useState<DecorationBalance | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { getDecorationBalance().then(setBalance).catch(e => setError(e instanceof Error ? e.message : 'Não foi possível consultar o saldo.')) }, [])
  const confirm = async () => {
    setBusy(true); setError('')
    try {
      const result = await decoratePost(post.id)
      onDecorated(result.decorations, result.balance)
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível condecorar.')
    } finally {
      setBusy(false)
    }
  }
  const empty = balance !== null && balance.remaining === 0
  return <Sheet title="Condecorar" onClose={onClose} labelledBy="decorate-title">
    <div className="decorate-sheet">
      <Medal className="decorate-medal" aria-hidden="true"/>
      <p>Condecore o post de <b>@{post.author.username}</b>. Cada operador tem poucas condecorações por semana; use nas que merecem.</p>
      <p className="decorate-balance" role="status">
        {balance ? <><b>{balance.remaining}/{balance.quota}</b> restantes nesta semana · renova {resetLabel(balance.resetsAt)}</> : 'Consultando saldo…'}
      </p>
      {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}
      <button type="button" className="callup-enlist" disabled={busy || !balance || empty} onClick={() => void confirm()}>
        {empty ? 'SEM CONDECORAÇÕES' : busy ? 'CONDECORANDO…' : 'CONDECORAR'}
      </button>
    </div>
  </Sheet>
}
