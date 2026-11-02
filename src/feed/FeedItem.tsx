import { Bookmark, Crosshair, MessageCircle, Send, Skull, Volume2, VolumeX } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { mediaUrl, type FeedMedia, type FeedPost } from '../api'
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

export function FeedItem({ post, mode, muted, onToggleMute, onHashtag }: { post: FeedPost; mode: ItemMode; muted: boolean; onToggleMute: () => void; onHashtag: (tag: string) => void }) {
  const [expanded, setExpanded] = useState(false)
  const [burst, setBurst] = useState(0)
  const lastTap = useRef(0)
  const singleTap = useRef<number | undefined>(undefined)
  const media = post.media[0]
  const isVideo = media?.kind === 'VIDEO'
  const alt = `${isVideo ? 'Vídeo' : 'Foto'} de @${post.author.username}${post.caption ? `: ${post.caption.slice(0, 120)}` : ''}`
  useEffect(() => () => window.clearTimeout(singleTap.current), [])
  // Toque simples alterna o som; toque duplo marca "Na mira" (a gravacao chega na etapa 6).
  const tap = () => {
    const now = Date.now()
    if (now - lastTap.current < DOUBLE_TAP_MS) {
      window.clearTimeout(singleTap.current)
      lastTap.current = 0
      setBurst(value => value + 1)
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
    <aside className="feed-actions" aria-label="Ações do post">
      <span className="feed-author-avatar" aria-hidden="true">{post.author.callsign.charAt(0).toUpperCase()}<RankInsignia code={post.author.rankCode}/></span>
      <button type="button" aria-label={`Na mira, ${post.counts.onTarget}`} aria-pressed={post.viewer.onTarget}><Crosshair/><span>{compact.format(post.counts.onTarget)}</span></button>
      <button type="button" aria-label={`Eliminado, ${post.counts.eliminated}`} aria-pressed={post.viewer.eliminated}><Skull/><span>{compact.format(post.counts.eliminated)}</span></button>
      <button type="button" aria-label={`Comentários, ${post.counts.comments}`}><MessageCircle/><span>{compact.format(post.counts.comments)}</span></button>
      <button type="button" aria-label="Compartilhar"><Send/><span>{compact.format(post.counts.shares)}</span></button>
      <button type="button" aria-label="Salvar" aria-pressed={post.viewer.saved}><Bookmark/><span>{compact.format(post.counts.saves)}</span></button>
    </aside>
    <div className="feed-caption">
      <p className="feed-byline"><b>@{post.author.username}</b>{post.team && <span>{post.team.acronym}</span>}<time dateTime={post.publishedAt}>{timeAgo(post.publishedAt)}</time></p>
      {post.field && <p className="feed-field">{post.field.name}</p>}
      {caption && <p className={`feed-text ${expanded ? 'expanded' : ''}`}>{caption}</p>}
      {long && <button type="button" className="feed-more" onClick={() => setExpanded(value => !value)}>{expanded ? 'ver menos' : 'ver mais'}</button>}
      {post.hashtags.length > 0 && <p className="feed-tags">{post.hashtags.map(tag => <button type="button" key={tag} onClick={() => onHashtag(tag)}>#{tag}</button>)}</p>}
    </div>
  </article>
}

export function FeedSkeleton() {
  return <div className="feed-item feed-skeleton" aria-busy="true" aria-label="Carregando feed"><span/><span/><span/></div>
}
