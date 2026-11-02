import { Crosshair, RefreshCw, Users } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { getFeed, type FeedPost } from '../api'
import { FeedItem, FeedSkeleton, type ItemMode } from './FeedItem'
import type { FeedTab } from './FeedPage'

// Quantos posts ao redor do visivel ficam montados; o resto vira so um espaco vazio do mesmo tamanho.
const MOUNT_RADIUS = 2
const LOAD_AHEAD = 3

export function FeedList({ tab, onFindOperators, onScrolledChange }: { tab: FeedTab; onFindOperators: () => void; onScrolledChange?: (scrolled: boolean) => void }) {
  const [items, setItems] = useState<FeedPost[]>([])
  const [cursor, setCursor] = useState<string | null | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [active, setActive] = useState(0)
  const [muted, setMuted] = useState(true)
  const container = useRef<HTMLDivElement>(null)
  const loadingRef = useRef(false)

  const load = useCallback(async (from: string | null | undefined, reset: boolean) => {
    if (loadingRef.current) return
    loadingRef.current = true
    setLoading(true); setError('')
    try {
      const page = await getFeed(tab, from)
      setItems(previous => reset ? page.items : [...previous, ...page.items.filter(item => !previous.some(existing => existing.id === item.id))])
      setCursor(page.nextCursor ?? null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar o feed.')
    } finally {
      loadingRef.current = false
      setLoading(false)
    }
  }, [tab])

  useEffect(() => {
    setItems([]); setCursor(undefined); setActive(0)
    container.current?.scrollTo({ top: 0 })
    void load(undefined, true)
  }, [load])

  // O post "ativo" e o que ocupa a maior parte da tela; o scroll snap garante um por vez.
  useEffect(() => {
    const root = container.current
    if (!root) return
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (entry.isIntersecting && entry.intersectionRatio >= 0.6) setActive(Number((entry.target as HTMLElement).dataset.index))
      }
    }, { root, threshold: [0.6] })
    root.querySelectorAll<HTMLElement>('[data-index]').forEach(slot => observer.observe(slot))
    return () => observer.disconnect()
  }, [items.length])

  useEffect(() => { onScrolledChange?.(active > 0) }, [active, onScrolledChange])

  useEffect(() => {
    if (cursor && !loading && !error && active >= items.length - LOAD_AHEAD) void load(cursor, false)
  }, [active, cursor, error, items.length, load, loading])

  const mode = (index: number): ItemMode => index === active ? 'active' : Math.abs(index - active) === 1 ? 'preload' : 'idle'

  if (!items.length && loading) return <div className="feed-stream"><FeedSkeleton/></div>
  if (!items.length && error) return <div className="feed-stream"><div className="feed-empty" role="alert"><Crosshair aria-hidden="true"/><h2>Não foi possível carregar o feed</h2><p>{error}</p><button type="button" className="button button--small" onClick={() => void load(undefined, true)}><RefreshCw/> Tentar novamente</button></div></div>
  if (!items.length && cursor === null) return <div className="feed-stream"><div className="feed-empty">
    <Crosshair aria-hidden="true"/>
    <h2>{tab === 'following' ? 'Você ainda não segue ninguém' : 'Nenhum post por aqui ainda'}</h2>
    <p>{tab === 'following' ? 'Siga operadores para ver os posts deles nesta aba, em ordem de publicação.' : 'Os posts da comunidade aparecerão aqui assim que forem publicados.'}</p>
    <button type="button" className="button button--small" onClick={onFindOperators}><Users/> Encontrar operadores</button>
  </div></div>

  return <div className="feed-stream" ref={container}>
    {items.map((post, index) => <div className="feed-slot" key={post.id} data-index={index}>
      {Math.abs(index - active) <= MOUNT_RADIUS
        ? <FeedItem post={post} mode={mode(index)} muted={muted} onToggleMute={() => setMuted(value => !value)} onHashtag={() => undefined}/>
        : null}
    </div>)}
    {loading && <div className="feed-slot"><FeedSkeleton/></div>}
    {error && <div className="feed-slot"><div className="feed-empty" role="alert"><p>{error}</p><button type="button" className="button button--small" onClick={() => void load(cursor, false)}><RefreshCw/> Tentar novamente</button></div></div>}
    {cursor === null && <div className="feed-slot feed-end"><div className="feed-empty"><Crosshair aria-hidden="true"/><h2>Você viu tudo</h2><p>Volte mais tarde para novos posts.</p></div></div>}
  </div>
}
