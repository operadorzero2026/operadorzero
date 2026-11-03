import { ArrowDown, ChevronLeft, Lock, MoreVertical, Pin, PinOff, Send, Trash2, VolumeX, X } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import {
  chatStreamUrl, deleteChatMessage, getChat, muteChatMember, pinChatMessage, publicMediaUrl, sendChat, unmuteChatMember,
  type OperationChatItem, type OperationChatState,
} from '../api'
import './operation-chat.css'

const POLL_MS = 10_000
const time = (value: string) => new Date(value).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
const dayLabel = (value: string) => new Date(value).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' })
const MUTES: { value: '1H' | '6H' | '24H' | 'INDEFINITE'; label: string }[] = [
  { value: '1H', label: '1 hora' }, { value: '6H', label: '6 horas' }, { value: '24H', label: '24 horas' }, { value: 'INDEFINITE', label: 'Tempo indeterminado' },
]

/**
 * Altura e posicao da area visivel (VisualViewport): no iPhone, quando o teclado abre, a janela nao encolhe; a area visivel sim.
 * O chat ocupa exatamente essa area, entao o campo e o Enviar ficam sempre logo acima do teclado.
 */
function useVisibleArea() {
  const read = () => {
    const vv = window.visualViewport
    return { height: vv ? vv.height : window.innerHeight, top: vv ? vv.offsetTop : 0, keyboard: vv ? window.innerHeight - vv.height > 120 : false }
  }
  const [area, setArea] = useState(read)
  useEffect(() => {
    const vv = window.visualViewport
    const update = () => setArea(read())
    vv?.addEventListener('resize', update)
    vv?.addEventListener('scroll', update)
    window.addEventListener('resize', update)
    return () => { vv?.removeEventListener('resize', update); vv?.removeEventListener('scroll', update); window.removeEventListener('resize', update) }
  }, [])
  return area
}

/** Chat geral da operacao em tela cheia: so texto, tempo real, moderacao do organizador e layout pensado para o iPhone. */
export function OperationChat({ operationId, title, onClose }: { operationId: string; title: string; onClose: () => void }) {
  const area = useVisibleArea()
  const [state, setState] = useState<OperationChatState | null>(null)
  const [items, setItems] = useState<OperationChatItem[]>([])
  const [error, setError] = useState('')
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [menu, setMenu] = useState<OperationChatItem | null>(null)
  const [showMuted, setShowMuted] = useState(false)
  const [unread, setUnread] = useState(false)
  const list = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const nearBottom = useRef(true)
  const firstLoad = useRef(true)

  // Trava a rolagem da pagina por tras (no Safari ela "puxa" a tela junto com o teclado).
  useEffect(() => {
    const html = document.documentElement
    const previous = [html.style.overflow, document.body.style.overflow]
    html.style.overflow = 'hidden'; document.body.style.overflow = 'hidden'
    const key = (event: globalThis.KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', key)
    return () => { html.style.overflow = previous[0]; document.body.style.overflow = previous[1]; window.removeEventListener('keydown', key) }
  }, [onClose])

  const merge = useCallback((incoming: OperationChatItem[]) => setItems(current => {
    const byId = new Map(current.map(item => [item.id, item]))
    incoming.forEach(item => byId.set(item.id, item))
    return [...byId.values()].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  }), [])

  const refresh = useCallback(async () => {
    try {
      const page = await getChat(operationId)
      setState(page)
      merge(page.items)
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chat indisponível.')
    }
  }, [operationId, merge])

  // Tempo real: o servidor avisa "mudou" e o app busca as novidades. Sem conexao, confere a cada 10 s.
  useEffect(() => {
    void refresh()
    let poll: number | undefined
    let source: EventSource | null = null
    const fallback = () => { if (!poll) poll = window.setInterval(() => void refresh(), POLL_MS) }
    if (typeof EventSource === 'undefined') fallback()
    else {
      source = new EventSource(chatStreamUrl(operationId), { withCredentials: true })
      source.addEventListener('changed', () => void refresh())
      source.addEventListener('ready', () => { if (poll) { window.clearInterval(poll); poll = undefined } })
      source.onerror = () => fallback()
    }
    const visible = () => { if (document.visibilityState === 'visible') void refresh() }
    document.addEventListener('visibilitychange', visible)
    return () => { source?.close(); if (poll) window.clearInterval(poll); document.removeEventListener('visibilitychange', visible) }
  }, [operationId, refresh])

  // Mantem no fim quando chega mensagem, a menos que a pessoa esteja lendo o historico.
  useLayoutEffect(() => {
    const element = list.current
    if (!element) return
    if (firstLoad.current && items.length) { element.scrollTop = element.scrollHeight; firstLoad.current = false; return }
    if (nearBottom.current) element.scrollTop = element.scrollHeight
    else setUnread(true)
  }, [items])
  useEffect(() => { const element = list.current; if (element && nearBottom.current) element.scrollTop = element.scrollHeight }, [area.height])

  const onScroll = () => {
    const element = list.current
    if (!element) return
    nearBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80
    if (nearBottom.current) setUnread(false)
  }
  const loadOlder = async () => {
    if (!items.length) return
    const element = list.current
    const before = element?.scrollHeight ?? 0
    const page = await getChat(operationId, { before: items[0].createdAt })
    merge(page.items)
    setState(current => current ? { ...current, hasMore: page.hasMore } : current)
    requestAnimationFrame(() => { if (element) element.scrollTop = element.scrollHeight - before })
  }

  const send = async () => {
    const body = text.trim()
    if (!body || sending) return
    setSending(true)
    try {
      await sendChat(operationId, body, crypto.randomUUID())
      setText('')
      nearBottom.current = true
      await refresh()
      input.current?.focus()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível enviar.')
    } finally {
      setSending(false)
    }
  }
  const onKey = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // No computador, Enter envia e Shift+Enter quebra a linha; no celular o botao Enviar fica ao lado.
    if (event.key === 'Enter' && !event.shiftKey && !('ontouchstart' in window)) { event.preventDefault(); void send() }
  }
  const act = async (action: () => Promise<unknown>) => {
    setMenu(null)
    try { await action(); await refresh() } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível concluir.') }
  }

  const style = { height: `${area.height}px`, transform: `translateY(${area.top}px)` }
  return createPortal(<div className={`op-chat ${area.keyboard ? 'is-keyboard' : ''}`} style={style} role="dialog" aria-modal="true" aria-label={`Chat de ${title}`}>
    <header className="op-chat-head">
      <button type="button" onClick={onClose} aria-label="Fechar chat"><ChevronLeft/></button>
      <div><b>Chat da operação</b><small>{title}</small></div>
      {state?.manager && state.muted.length > 0 && <button type="button" className="op-chat-muted-btn" onClick={() => setShowMuted(true)} aria-label="Silenciados"><VolumeX/>{state.muted.length}</button>}
    </header>
    {state?.notice && <p className="op-chat-notice" role="status">{state.notice}</p>}
    {state && state.pinned.length > 0 && <div className="op-chat-pinned">
      {state.pinned.map(item => <p key={item.id}><Pin/><span><b>{item.author?.callsign}</b> {item.body}</span>
        {state.manager && <button type="button" onClick={() => void act(() => pinChatMessage(operationId, item.id, false))} aria-label="Desafixar"><PinOff/></button>}</p>)}
    </div>}
    <div ref={list} className="op-chat-list" onScroll={onScroll} aria-live="polite">
      {error && !state ? <p className="op-chat-empty" role="alert"><Lock/> {error}</p> : !state ? <p className="op-chat-empty">Carregando…</p> : <>
        {state.hasMore && <button type="button" className="op-chat-older" onClick={() => void loadOlder()}>Carregar mensagens anteriores</button>}
        {items.length === 0 && <p className="op-chat-empty">Nenhuma mensagem ainda. Combine a carona, o horário e os detalhes da operação aqui.</p>}
        {items.map((item, index) => {
          const day = dayLabel(item.createdAt)
          const divider = index === 0 || dayLabel(items[index - 1].createdAt) !== day ? <p className="op-chat-day">{day}</p> : null
          return <div key={item.id}>
            {divider}
            <article className={`op-chat-msg ${item.own ? 'is-own' : ''} ${item.deleted ? 'is-deleted' : ''}`}>
              {!item.own && <span className="op-chat-avatar">{item.author?.photoUrl ? <img src={publicMediaUrl(item.author.photoUrl)} alt=""/> : (item.author?.callsign ?? '?').charAt(0).toUpperCase()}</span>}
              <div className="op-chat-bubble">
                {!item.own && <b>{item.author?.callsign}</b>}
                <p>{item.deleted ? 'Mensagem apagada pelo organizador' : item.body}</p>
                <small>{item.pinned && <Pin/>}{time(item.createdAt)}</small>
              </div>
              {state.manager && !item.deleted && <button type="button" className="op-chat-more" onClick={() => setMenu(item)} aria-label="Ações da mensagem"><MoreVertical/></button>}
            </article>
          </div>
        })}
      </>}
    </div>
    {unread && <button type="button" className="op-chat-unread" onClick={() => { const element = list.current; if (element) element.scrollTop = element.scrollHeight; setUnread(false) }}><ArrowDown/> Novas mensagens</button>}
    {error && state && <p className="op-chat-error" role="alert">{error} <button type="button" onClick={() => setError('')}><X/></button></p>}
    <footer className="op-chat-compose">
      {state && !state.me.canSend ? <p className="op-chat-blocked"><Lock/> {state.me.blockedReason}</p> : <>
        <textarea ref={input} value={text} onChange={event => setText(event.target.value)} onKeyDown={onKey} maxLength={1000} rows={1}
          placeholder="Mensagem" aria-label="Mensagem" enterKeyHint="send" disabled={!state}/>
        <button type="button" className="op-chat-send" disabled={!text.trim() || sending || !state} onClick={() => void send()} aria-label="Enviar"><Send/></button>
      </>}
    </footer>

    {menu && <div className="op-chat-sheet-backdrop" onClick={() => setMenu(null)}>
      <section className="op-chat-sheet" role="dialog" aria-label="Ações da mensagem" onClick={event => event.stopPropagation()}>
        <p className="op-chat-sheet-preview"><b>{menu.author?.callsign}</b> {menu.body}</p>
        <button type="button" onClick={() => void act(() => pinChatMessage(operationId, menu.id, !menu.pinned))}>{menu.pinned ? <><PinOff/> Desafixar</> : <><Pin/> Fixar como comunicado</>}</button>
        <button type="button" className="danger" onClick={() => { if (window.confirm('Apagar esta mensagem para todos?')) void act(() => deleteChatMessage(operationId, menu.id)) }}><Trash2/> Apagar mensagem</button>
        {!menu.own && menu.author && <>
          <small>Silenciar {menu.author.callsign}</small>
          <div className="op-chat-sheet-mutes">{MUTES.map(option => <button key={option.value} type="button"
            onClick={() => void act(() => muteChatMember(operationId, menu.author!.operatorId, option.value))}><VolumeX/> {option.label}</button>)}</div>
        </>}
        <button type="button" onClick={() => setMenu(null)}>Cancelar</button>
      </section>
    </div>}

    {showMuted && state && <div className="op-chat-sheet-backdrop" onClick={() => setShowMuted(false)}>
      <section className="op-chat-sheet" role="dialog" aria-label="Silenciados" onClick={event => event.stopPropagation()}>
        <h4>Silenciados</h4>
        {state.muted.length === 0 && <p>Ninguém silenciado.</p>}
        {state.muted.map(item => <p key={item.operatorId} className="op-chat-muted-row"><span><b>{item.callsign}</b><small>{item.until ? `até ${new Date(item.until).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}` : 'tempo indeterminado'}</small></span>
          <button type="button" onClick={() => void act(() => unmuteChatMember(operationId, item.operatorId))}>Retirar silenciamento</button></p>)}
        <button type="button" onClick={() => setShowMuted(false)}>Fechar</button>
      </section>
    </div>}
  </div>, document.body)
}
