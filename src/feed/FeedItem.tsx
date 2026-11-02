import { Bookmark, Crosshair, Flag, MessageCircle, MoreHorizontal, Plus, Send, Skull, Trash2, Volume2, VolumeX } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { deleteFeedPost, followOperator, mediaUrl, reactToPost, reportFeedComment, reportFeedPost, savePost, type FeedComment, type FeedMedia, type FeedPost, type PostCounts } from '../api'
import { CommentsSheet, ReportSheet, ShareSheet, Sheet } from './FeedSheets'
import { RankInsignia } from './RankInsignia'
import { timeAgo } from './format'
import { useVideoSource } from './useVideoSource'

/** Como cada post deve se comportar: tocando (visivel), pre-carregado (vizinho) ou so reservando espaco. */
export type ItemMode = 'active' | 'preload' | 'idle'

const DOUBLE_TAP_MS = 280
const compact = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 })
function MediaStage({ media, mode, muted, alt }: { media: FeedMedia; mode: ItemMode; muted: boolean; alt: string }) {
  const video = useRef<HTMLVideoElement>(null)
  useVideoSource(video, mediaUrl(media.hlsUrl), mediaUrl(media.mp4Url), mode)
  useEffect(() => {
    const element = video.current
    if (!element) return
    element.dataset.active = String(mode === 'active')
    if (mode === 'active') {
      // Navegadores so permitem autoplay mudo; se recusarem, o poster continua visivel.
      element.play().catch(() => undefined)
    } else {
      element.pause()
    }
  }, [mode])
  useEffect(() => { if (video.current) video.current.muted = muted }, [muted])
  const backdrop = media.blurPlaceholder ?? mediaUrl(media.posterUrl) ?? mediaUrl(media.images[0]?.url)
  const portrait = media.height / media.width >= 1.7
  return <div className={`feed-media ${portrait ? 'feed-media--fill' : ''}`}>
    {/* Fundo desfocado da propria midia para o que nao e 9:16 (sem cortar a imagem). */}
    {!portrait && backdrop && <img className="feed-media-backdrop" src={backdrop} alt="" aria-hidden="true"/>}
    {media.kind === 'VIDEO'
      ? <video ref={video} className="feed-media-content" poster={mediaUrl(media.posterUrl)}
          muted={muted} loop playsInline preload={mode === 'idle' ? 'none' : 'auto'} aria-label={alt}/>
      : <img className="feed-media-content" alt={alt} loading={mode === 'idle' ? 'lazy' : 'eager'} decoding="async"
          src={mediaUrl(media.images.at(-1)?.url)} sizes="(max-width: 480px) 100vw, 480px"
          srcSet={media.images.map(image => `${mediaUrl(image.url)} ${image.width}w`).join(', ')}/>}
  </div>
}

export function FeedItem({ post, mode, muted, onToggleMute, onHashtag, onDeleted, readOnly = false }: {
  post: FeedPost; mode: ItemMode; muted: boolean; onToggleMute: () => void; onHashtag: (tag: string) => void; onDeleted?: (id: string) => void; readOnly?: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const [burst, setBurst] = useState(0)
  const [counts, setCounts] = useState(post.counts)
  const [viewer, setViewer] = useState(post.viewer)
  const [sheet, setSheet] = useState<'comments' | 'share' | 'menu' | 'report' | null>(null)
  const [reportComment, setReportComment] = useState<FeedComment | null>(null)
  const [notice, setNotice] = useState('')
  const lastTap = useRef(0)
  const singleTap = useRef<number | undefined>(undefined)
  const media = post.media[0]
  const isVideo = media?.kind === 'VIDEO'
  const alt = `${isVideo ? 'Vídeo' : 'Foto'} de @${post.author.username}${post.caption ? `: ${post.caption.slice(0, 120)}` : ''}`
  useEffect(() => () => window.clearTimeout(singleTap.current), [])
  const applyCounts = (next: PostCounts) => setCounts(previous => ({ ...previous, ...next }))
  const flash = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(''), 2500) }

  // Otimista: muda na hora e desfaz se o servidor recusar.
  const toggle = async (flag: 'onTarget' | 'eliminated' | 'saved', force?: boolean) => {
    if (readOnly) return
    const on = force ?? !viewer[flag]
    if (viewer[flag] === on) return
    const counter = flag === 'saved' ? 'saves' : flag
    setViewer(previous => ({ ...previous, [flag]: on }))
    setCounts(previous => ({ ...previous, [counter]: Math.max(0, previous[counter] + (on ? 1 : -1)) }))
    try {
      applyCounts(flag === 'saved' ? await savePost(post.id, on) : await reactToPost(post.id, flag === 'onTarget' ? 'ON_TARGET' : 'ELIMINATED', on))
    } catch (error) {
      setViewer(previous => ({ ...previous, [flag]: !on }))
      setCounts(previous => ({ ...previous, [counter]: Math.max(0, previous[counter] + (on ? -1 : 1)) }))
      flash(error instanceof Error ? error.message : 'Não foi possível concluir.')
    }
  }
  const follow = async () => {
    setViewer(previous => ({ ...previous, followsAuthor: true }))
    try { await followOperator(post.author.username, true); flash(`Seguindo @${post.author.username}`) }
    catch (error) { setViewer(previous => ({ ...previous, followsAuthor: false })); flash(error instanceof Error ? error.message : 'Não foi possível seguir.') }
  }
  const remove = async () => {
    if (!window.confirm('Apagar este post?')) return
    try { await deleteFeedPost(post.id); setSheet(null); onDeleted?.(post.id) } catch (error) { flash(error instanceof Error ? error.message : 'Não foi possível apagar.') }
  }
  // Toque simples alterna o som; toque duplo marca "Na mira".
  const tap = () => {
    const now = Date.now()
    if (now - lastTap.current < DOUBLE_TAP_MS) {
      window.clearTimeout(singleTap.current)
      lastTap.current = 0
      setBurst(value => value + 1)
      void toggle('onTarget', true)
      return
    }
    lastTap.current = now
    if (isVideo) singleTap.current = window.setTimeout(onToggleMute, DOUBLE_TAP_MS)
  }
  const caption = post.caption ?? ''
  const long = caption.length > 90
  return <article className="feed-item" aria-label={`Post de @${post.author.username}`}>
    {media && <MediaStage media={media} mode={mode} muted={muted} alt={alt}/>}
    <button type="button" className="feed-tap-layer" onClick={tap} aria-label={isVideo ? (muted ? 'Ativar som' : 'Desativar som') : 'Post'}/>
    {burst > 0 && <span key={burst} className="feed-burst" aria-hidden="true"><Crosshair/></span>}
    {isVideo && mode === 'active' && <span className="feed-sound-badge" aria-hidden="true">{muted ? <VolumeX/> : <Volume2/>}</span>}
    {notice && <p className="feed-toast" role="status">{notice}</p>}
    {!readOnly && <aside className="feed-actions" aria-label="Ações do post">
      <span className="feed-author-avatar">{post.author.callsign.charAt(0).toUpperCase()}<RankInsignia code={post.author.rankCode}/>
        {!viewer.followsAuthor && !viewer.ownPost && <button type="button" className="feed-follow" onClick={() => void follow()} aria-label={`Seguir @${post.author.username}`}><Plus/></button>}
      </span>
      <button type="button" onClick={() => void toggle('onTarget')} aria-label={`Na mira, ${counts.onTarget}`} aria-pressed={viewer.onTarget}><Crosshair/><span>{compact.format(counts.onTarget)}</span></button>
      <button type="button" onClick={() => void toggle('eliminated')} aria-label={`Eliminado, ${counts.eliminated}`} aria-pressed={viewer.eliminated}><Skull/><span>{compact.format(counts.eliminated)}</span></button>
      <button type="button" onClick={() => setSheet('comments')} aria-label={`Comentários, ${counts.comments}`}><MessageCircle/><span>{compact.format(counts.comments)}</span></button>
      <button type="button" onClick={() => setSheet('share')} aria-label="Compartilhar"><Send/><span>{compact.format(counts.shares)}</span></button>
      <button type="button" onClick={() => void toggle('saved')} aria-label="Salvar" aria-pressed={viewer.saved}><Bookmark/><span>{compact.format(counts.saves)}</span></button>
      <button type="button" onClick={() => setSheet('menu')} aria-label="Mais opções do post"><MoreHorizontal/></button>
    </aside>}
    <div className="feed-caption">
      <p className="feed-byline"><b>@{post.author.username}</b>{post.team && <span>{post.team.acronym}</span>}<time dateTime={post.publishedAt}>{timeAgo(post.publishedAt)}</time></p>
      {post.field && <p className="feed-field">{post.field.name}</p>}
      {caption && <p className={`feed-text ${expanded ? 'expanded' : ''}`}>{caption}</p>}
      {long && <button type="button" className="feed-more" onClick={() => setExpanded(value => !value)}>{expanded ? 'ver menos' : 'ver mais'}</button>}
      {post.hashtags.length > 0 && <p className="feed-tags">{post.hashtags.map(tag => <button type="button" key={tag} onClick={() => onHashtag(tag)}>#{tag}</button>)}</p>}
    </div>
    {sheet === 'comments' && <CommentsSheet post={{ ...post, counts: { ...post.counts, ...counts } }} onClose={() => setSheet(null)} onCounts={applyCounts}
      onReport={comment => { setReportComment(comment); setSheet('report') }}/>}
    {sheet === 'share' && <ShareSheet post={post} onClose={() => setSheet(null)} onShared={() => setCounts(previous => ({ ...previous, shares: previous.shares + 1 }))}/>}
    {sheet === 'menu' && <Sheet title="Opções" onClose={() => setSheet(null)} labelledBy="post-menu-title">
      {viewer.ownPost
        ? <button type="button" className="create-choice" onClick={() => void remove()}><Trash2/><span><b>APAGAR POST</b><small>Some do feed para todos.</small></span></button>
        : <button type="button" className="create-choice" onClick={() => { setReportComment(null); setSheet('report') }}><Flag/><span><b>DENUNCIAR</b><small>A moderação analisa em sigilo.</small></span></button>}
    </Sheet>}
    {sheet === 'report' && <ReportSheet title={reportComment ? 'Denunciar comentário' : 'Denunciar post'} onClose={() => { setSheet(null); setReportComment(null) }}
      onSubmit={(reason, details) => reportComment ? reportFeedComment(reportComment.id, reason, details) : reportFeedPost(post.id, reason, details)}/>}
  </article>
}

export function FeedSkeleton() {
  return <div className="feed-item feed-skeleton" aria-busy="true" aria-label="Carregando feed"><span/><span/><span/></div>
}
