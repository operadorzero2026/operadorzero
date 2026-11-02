import { Crosshair, RefreshCw, Users } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getCallUps, getFeed, getHonorBoard, type CallUp, type FeedPost, type HonorBoard } from '../api'
import { CallUpCard } from './CallUpCard'
import { HonorBoardCard } from './HonorBoard'
import { FeedItem, FeedSkeleton, type ItemMode } from './FeedItem'
import type { FeedTab } from './FeedPage'

// Quantos posts ao redor do visivel ficam montados; o resto vira so um espaco vazio do mesmo tamanho.
const MOUNT_RADIUS = 2
const LOAD_AHEAD = 3
// Convocacoes no "Para voce": a primeira depois do 3o post, depois uma a cada 6.
const FIRST_CALLUP_AFTER = 3
const CALLUP_EVERY = 6

type Entry = { kind: 'post'; post: FeedPost } | { kind: 'callup'; callUp: CallUp } | { kind: 'honor'; board: HonorBoard }

/** Segunda-feira em Brasilia: o Quadro de Honra da semana que acabou abre o feed. */
const isMondayInBrasilia = () => new Intl.DateTimeFormat('en-US', { timeZone: 'America/Sao_Paulo', weekday: 'short' }).format(new Date()) === 'Mon'

function interleave(posts: FeedPost[], callUps: CallUp[], finished: boolean): Entry[] {
  const entries: Entry[] = []
  let next = 0
  posts.forEach((post, index) => {
    entries.push({ kind: 'post', post })
    const position = index + 1
    if (next < callUps.length && position >= FIRST_CALLUP_AFTER && (position - FIRST_CALLUP_AFTER) % CALLUP_EVERY === 0) {
      entries.push({ kind: 'callup', callUp: callUps[next++] })
    }
  })
  // Feed curto (ou vazio) com jogos abertos: os jogos aparecem no fim em vez de sumirem.
  if (finished) while (next < callUps.length && next < 3) entries.push({ kind: 'callup', callUp: callUps[next++] })
  return entries
}

export function FeedList({ tab, hashtag, onHashtag, onFindOperators, onScrolledChange, onOpenHonorBoard }: { tab: FeedTab; hashtag?: string | null; onHashtag: (tag: string) => void; onFindOperators: () => void; onScrolledChange?: (scrolled: boolean) => void; onOpenHonorBoard?: () => void }) {
  const [items, setItems] = useState<FeedPost[]>([])
  const [cursor, setCursor] = useState<string | null | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [active, setActive] = useState(0)
  const [muted, setMuted] = useState(true)
  const [callUps, setCallUps] = useState<CallUp[]>([])
  const [honor, setHonor] = useState<HonorBoard | null>(null)
  const container = useRef<HTMLDivElement>(null)
  const loadingRef = useRef(false)

  const load = useCallback(async (from: string | null | undefined, reset: boolean) => {
    if (loadingRef.current) return
    loadingRef.current = true
    setLoading(true); setError('')
    try {
      const page = await getFeed(tab, from, hashtag)
      setItems(previous => reset ? page.items : [...previous, ...page.items.filter(item => !previous.some(existing => existing.id === item.id))])
      setCursor(page.nextCursor ?? null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar o feed.')
    } finally {
      loadingRef.current = false
      setLoading(false)
    }
  }, [tab, hashtag])

  useEffect(() => {
    if (tab !== 'for-you' || hashtag) return
    let active = true
    getCallUps().then(result => { if (active) setCallUps(result.items) }).catch(() => undefined)
    if (isMondayInBrasilia()) getHonorBoard(true).then(result => { if (active && result.items.length) setHonor(result) }).catch(() => undefined)
    return () => { active = false }
  }, [tab, hashtag])

  const entries = useMemo(() => {
    const list = interleave(items, tab === 'for-you' && !hashtag ? callUps : [], cursor === null)
    return honor && tab === 'for-you' && !hashtag ? [{ kind: 'honor', board: honor } as Entry, ...list] : list
  }, [items, callUps, cursor, tab, hashtag, honor])

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
  }, [entries.length])

  useEffect(() => { onScrolledChange?.(active > 0) }, [active, onScrolledChange])

  useEffect(() => {
    if (cursor && !loading && !error && active >= entries.length - LOAD_AHEAD) void load(cursor, false)
  }, [active, cursor, error, entries.length, load, loading])

  const mode = (index: number): ItemMode => index === active ? 'active' : Math.abs(index - active) === 1 ? 'preload' : 'idle'

  if (!items.length && loading) return <div className="feed-stream"><FeedSkeleton/></div>
  if (!items.length && error) return <div className="feed-stream"><div className="feed-empty" role="alert"><Crosshair aria-hidden="true"/><h2>Não foi possível carregar o feed</h2><p>{error}</p><button type="button" className="button button--small" onClick={() => void load(undefined, true)}><RefreshCw/> Tentar novamente</button></div></div>
  if (!entries.length && cursor === null) return <div className="feed-stream"><div className="feed-empty">
    <Crosshair aria-hidden="true"/>
    <h2>{hashtag ? `Nenhum post com #${hashtag}` : tab === 'following' ? 'Você ainda não segue ninguém' : 'Nenhum post por aqui ainda'}</h2>
    <p>{tab === 'following' ? 'Siga operadores para ver os posts deles nesta aba, em ordem de publicação.' : 'Os posts da comunidade aparecerão aqui assim que forem publicados.'}</p>
    <button type="button" className="button button--small" onClick={onFindOperators}><Users/> Encontrar operadores</button>
  </div></div>

  return <div className="feed-stream" ref={container}>
    {entries.map((entry, index) => <div className="feed-slot" key={entry.kind === 'post' ? entry.post.id : entry.kind === 'callup' ? `callup-${entry.callUp.id}` : 'honor-board'} data-index={index}>
      {Math.abs(index - active) > MOUNT_RADIUS ? null
        : entry.kind === 'callup' ? <CallUpCard callUp={entry.callUp}/>
        : entry.kind === 'honor' ? <HonorBoardCard board={entry.board} onOpen={() => onOpenHonorBoard?.()}/>
        : <FeedItem post={entry.post} mode={mode(index)} muted={muted} onToggleMute={() => setMuted(value => !value)} onHashtag={onHashtag}
          onDeleted={id => setItems(previous => previous.filter(item => item.id !== id))}/>}
    </div>)}
    {loading && <div className="feed-slot"><FeedSkeleton/></div>}
    {error && <div className="feed-slot"><div className="feed-empty" role="alert"><p>{error}</p><button type="button" className="button button--small" onClick={() => void load(cursor, false)}><RefreshCw/> Tentar novamente</button></div></div>}
    {cursor === null && <div className="feed-slot feed-end"><div className="feed-empty"><Crosshair aria-hidden="true"/><h2>Você viu tudo</h2><p>Volte mais tarde para novos posts.</p></div></div>}
  </div>
}
