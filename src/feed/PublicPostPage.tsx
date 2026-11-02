import { ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getFeedPost, getPublicPost, type FeedPost } from '../api'
import { FeedItem } from './FeedItem'

/** Destino do link compartilhado: o post sozinho; deslogado ve so leitura com convite para entrar. */
export function PublicPostPage({ postId, loggedIn, onLogin, onSignup, onOpenFeed }: {
  postId: string; loggedIn: boolean; onLogin: () => void; onSignup: () => void; onOpenFeed: () => void
}) {
  const [post, setPost] = useState<FeedPost | null>(null)
  const [error, setError] = useState('')
  const [muted, setMuted] = useState(true)
  useEffect(() => {
    let active = true
    // Ao entrar, descarta a versao publica (sem reacoes de quem le) antes de buscar a versao logada.
    setPost(null)
    ;(loggedIn ? getFeedPost(postId) : getPublicPost(postId))
      .then(result => { if (active) { setPost(result); setError('') } })
      .catch(() => { if (active) setError('Este post não está disponível.') })
    return () => { active = false }
  }, [postId, loggedIn])
  return <section className="feed-screen public-post" aria-label="Post compartilhado">
    <div className="feed-column">
      <header className="feed-topbar public-post-bar">
        <a className="feed-logo" href="/" aria-label="Operador Zero, início">OPERADOR <b>ZERO</b></a>
        <span/>
        {loggedIn ? <button type="button" className="button button--small" onClick={onOpenFeed}>Ver o feed <ArrowRight/></button>
          : <button type="button" className="button button--small" onClick={onLogin}>Entrar</button>}
      </header>
      <div className="feed-stream">
        {error ? <div className="feed-empty" role="alert"><h2>{error}</h2><p>Ele pode ter sido apagado ou o autor desativou o compartilhamento.</p>
          <button type="button" className="button button--small" onClick={loggedIn ? onOpenFeed : onSignup}>{loggedIn ? 'Ir para o feed' : 'Criar conta gratuita'}</button></div>
          : post ? <div className="feed-slot"><FeedItem key={`${post.id}:${loggedIn}`} post={post} mode="active" muted={muted} onToggleMute={() => setMuted(value => !value)}
              onHashtag={() => (loggedIn ? onOpenFeed() : onSignup())} readOnly={!loggedIn}/></div>
          : <div className="feed-empty"><p>Carregando…</p></div>}
      </div>
      {!loggedIn && post && <div className="public-post-cta"><p>Reaja, comente e siga @{post.author.username} no Operador Zero.</p>
        <button type="button" className="button button--small" onClick={onSignup}>Criar conta gratuita</button></div>}
    </div>
  </section>
}
