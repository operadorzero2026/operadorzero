import { ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getCallUp, getPublicCallUp, type CallUp } from '../api'
import { CallUpCard } from './CallUpCard'

/** Destino do link de Convocacao compartilhado no WhatsApp: deslogado, ALISTAR leva ao login. */
export function PublicCallUpPage({ operationId, loggedIn, onLogin, onOpenFeed }: { operationId: string; loggedIn: boolean; onLogin: () => void; onOpenFeed: () => void }) {
  const [callUp, setCallUp] = useState<CallUp | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setCallUp(null)
    ;(loggedIn ? getCallUp(operationId) : getPublicCallUp(operationId))
      .then(result => { if (active) { setCallUp(result); setError('') } })
      .catch(() => { if (active) setError('Esta convocação não está mais aberta.') })
    return () => { active = false }
  }, [operationId, loggedIn])
  return <section className="feed-screen public-post" aria-label="Convocação compartilhada">
    <div className="feed-column">
      <header className="feed-topbar public-post-bar">
        <a className="feed-logo" href="/" aria-label="Operador Zero, início">OPERADOR <b>ZERO</b></a>
        <span/>
        {loggedIn ? <button type="button" className="button button--small" onClick={onOpenFeed}>Ver o feed <ArrowRight/></button>
          : <button type="button" className="button button--small" onClick={onLogin}>Entrar</button>}
      </header>
      <div className="feed-stream">
        {error ? <div className="feed-empty" role="alert"><h2>{error}</h2><p>Veja os próximos jogos na agenda do Operador Zero.</p>
          <a className="button button--small" href="/">Ver agenda</a></div>
          : callUp ? <div className="feed-slot"><CallUpCard key={`${callUp.id}:${loggedIn}`} callUp={callUp} onRequireLogin={loggedIn ? undefined : onLogin}/></div>
          : <div className="feed-empty"><p>Carregando…</p></div>}
      </div>
    </div>
  </section>
}
