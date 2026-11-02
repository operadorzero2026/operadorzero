import { Copy, Flag, MessageCircle, Send, Share2, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { addComment, deleteFeedComment, getComments, sharePost, type FeedComment, type FeedPost, type PostCounts, type ReportReason } from '../api'
import { REPORT_REASONS } from './reportReasons'
import { RankInsignia } from './RankInsignia'
import { timeAgo } from './format'

/** Folha inferior em portal: fica acima do feed e da barra de navegacao, com foco e Esc. */
export function Sheet({ title, onClose, children, labelledBy }: { title: string; onClose: () => void; children: ReactNode; labelledBy: string }) {
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    panel.current?.focus()
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return createPortal(<div className="create-sheet-backdrop feed-sheet-backdrop" onClick={onClose}>
    <section ref={panel} tabIndex={-1} className="create-sheet feed-sheet" role="dialog" aria-modal="true" aria-labelledby={labelledBy} onClick={event => event.stopPropagation()}>
      <header><h2 id={labelledBy}>{title}</h2><button type="button" className="feed-icon-button" onClick={onClose} aria-label="Fechar"><X/></button></header>
      {children}
    </section>
  </div>, document.body)
}

export function ShareSheet({ post, onClose, onShared }: { post: FeedPost; onClose: () => void; onShared: () => void }) {
  const [message, setMessage] = useState('')
  const url = `${window.location.origin}/p/${post.id}`
  const text = `${post.caption ? `${post.caption.slice(0, 120)} ` : ''}Veja no Operador Zero: ${url}`
  // O link e montado na hora (popups precisam abrir no clique); o registro do compartilhamento vai em paralelo.
  const track = (channel: 'WHATSAPP' | 'LINK' | 'NATIVE') => { void sharePost(post.id, channel).then(onShared).catch(() => undefined) }
  const whatsapp = () => { track('WHATSAPP'); window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener') }
  const copy = async () => {
    try { await navigator.clipboard.writeText(url); track('LINK'); setMessage('Link copiado.') } catch { setMessage(url) }
  }
  const native = async () => {
    try { await navigator.share({ title: `@${post.author.username} no Operador Zero`, text: post.caption ?? undefined, url }); track('NATIVE'); onClose() }
    catch { /* cancelado pela pessoa */ }
  }
  return <Sheet title="Compartilhar" onClose={onClose} labelledBy="share-title">
    <button type="button" className="create-choice" onClick={whatsapp}><MessageCircle/><span><b>WHATSAPP</b><small>Envia o link com prévia.</small></span></button>
    <button type="button" className="create-choice" onClick={() => void copy()}><Copy/><span><b>COPIAR LINK</b><small>{url.replace(/^https?:\/\//, '')}</small></span></button>
    {'share' in navigator && <button type="button" className="create-choice" onClick={() => void native()}><Share2/><span><b>MAIS OPÇÕES</b><small>Outros apps do aparelho.</small></span></button>}
    {message && <p className="create-notice" role="status">{message}</p>}
  </Sheet>
}

export function ReportSheet({ title, onSubmit, onClose }: { title: string; onSubmit: (reason: ReportReason, details: string) => Promise<void>; onClose: () => void }) {
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [details, setDetails] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async () => {
    if (!reason) return
    setBusy(true)
    try { await onSubmit(reason, details.trim()); setMessage('Denúncia enviada. A moderação vai analisar.'); window.setTimeout(onClose, 1200) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'Não foi possível enviar.') }
    finally { setBusy(false) }
  }
  return <Sheet title={title} onClose={onClose} labelledBy="report-title">
    <div className="report-reasons" role="radiogroup" aria-label="Motivo">
      {REPORT_REASONS.map(option => <label key={option.value}><input type="radio" name="reason" checked={reason === option.value} onChange={() => setReason(option.value)}/> {option.label}</label>)}
    </div>
    <label className="create-caption"><span>Detalhes (opcional)</span><textarea rows={2} maxLength={1000} value={details} onChange={event => setDetails(event.target.value)}/></label>
    <button type="button" className="button button--small" disabled={!reason || busy} onClick={() => void send()}><Flag/> Enviar denúncia</button>
    {message && <p className="create-notice" role="status">{message}</p>}
  </Sheet>
}

export function CommentsSheet({ post, onClose, onCounts, onReport }: { post: FeedPost; onClose: () => void; onCounts: (counts: PostCounts) => void; onReport: (comment: FeedComment) => void }) {
  const [items, setItems] = useState<FeedComment[] | null>(null)
  const [replies, setReplies] = useState<Record<string, FeedComment[]>>({})
  const [next, setNext] = useState<string | null>(null)
  const [body, setBody] = useState('')
  const [replyTo, setReplyTo] = useState<FeedComment | null>(null)
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const input = useRef<HTMLTextAreaElement>(null)
  const load = useCallback(async (after?: string | null) => {
    try {
      const page = await getComments(post.id, null, after)
      setItems(previous => after && previous ? [...previous, ...page.items] : page.items)
      setNext(page.nextAfter ?? null)
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar os comentários.') }
  }, [post.id])
  useEffect(() => { void load() }, [load])
  const loadReplies = async (comment: FeedComment) => {
    const page = await getComments(post.id, comment.id)
    setReplies(previous => ({ ...previous, [comment.id]: page.items }))
  }
  const send = async () => {
    const text = body.trim()
    if (!text) return
    setSending(true); setError('')
    try {
      onCounts(await addComment(post.id, text, replyTo?.id ?? null, crypto.randomUUID()))
      setBody('')
      const parent = replyTo ? (replyTo.parentId ?? replyTo.id) : null
      setReplyTo(null)
      const root = parent ? items?.find(item => item.id === parent) : undefined
      if (root) {
        setItems(previous => previous?.map(item => item.id === root.id ? { ...item, replies: item.replies + 1 } : item) ?? null)
        await loadReplies(root)
      } else await load()
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível comentar.') }
    finally { setSending(false) }
  }
  const remove = async (comment: FeedComment) => {
    if (!window.confirm('Apagar este comentário?')) return
    try { onCounts(await deleteFeedComment(comment.id)); await load(); setReplies({}) } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível apagar.') }
  }
  const row = (comment: FeedComment, nested = false) => <li key={comment.id} className={nested ? 'feed-comment feed-comment--reply' : 'feed-comment'}>
    <span className="recon-avatar feed-comment-avatar" aria-hidden="true">{comment.authorCallsign.charAt(0).toUpperCase()}<RankInsignia code={comment.rankCode} size={12}/></span>
    <div>
      <p><b>@{comment.authorUsername}</b> <time dateTime={comment.createdAt}>{timeAgo(comment.createdAt)}</time></p>
      <p className="feed-comment-body">{comment.body}</p>
      <div className="feed-comment-actions">
        <button type="button" onClick={() => { setReplyTo(comment); input.current?.focus() }}>Responder</button>
        {!nested && comment.replies > 0 && !replies[comment.id] && <button type="button" onClick={() => void loadReplies(comment)}>Ver {comment.replies} {comment.replies === 1 ? 'resposta' : 'respostas'}</button>}
        {comment.canDelete && <button type="button" onClick={() => void remove(comment)} aria-label="Apagar comentário"><Trash2/></button>}
        {!comment.own && <button type="button" onClick={() => onReport(comment)} aria-label="Denunciar comentário"><Flag/></button>}
      </div>
      {!nested && replies[comment.id] && <ul>{replies[comment.id].map(reply => row(reply, true))}</ul>}
    </div>
  </li>
  return <Sheet title={`Comentários${post.counts.comments ? ` (${post.counts.comments})` : ''}`} onClose={onClose} labelledBy="comments-title">
    <div className="feed-comments">
      {items === null && !error && <p className="create-notice">Carregando…</p>}
      {items?.length === 0 && <p className="create-notice">Seja o primeiro a comentar.</p>}
      {items && <ul>{items.map(item => row(item))}</ul>}
      {next && <button type="button" className="feed-more" onClick={() => void load(next)}>Carregar mais</button>}
    </div>
    {error && <p className="module-feedback module-feedback--error" role="alert">{error}</p>}
    <form className="feed-comment-form" onSubmit={event => { event.preventDefault(); void send() }}>
      {replyTo && <p className="create-notice">Respondendo @{replyTo.authorUsername} <button type="button" className="feed-more" onClick={() => setReplyTo(null)}>cancelar</button></p>}
      <textarea ref={input} rows={1} maxLength={1000} value={body} placeholder="Adicione um comentário…" aria-label="Comentário" onChange={event => setBody(event.target.value)}/>
      <button type="submit" className="feed-icon-button" disabled={!body.trim() || sending} aria-label="Enviar comentário"><Send/></button>
    </form>
  </Sheet>
}
