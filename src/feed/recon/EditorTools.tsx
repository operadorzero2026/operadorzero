import { AlignCenter, AlignLeft, AlignRight, ArrowLeft, AtSign, Check, Eraser, LoaderCircle, Redo2, Search, Smile, Type, Undo2, UserRound } from 'lucide-react'
import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { searchOperators, type DossierSummary, type OperatorSummary } from '../../api'
import { COLORS, FONTS, MAX_TEXT, STICKERS, fontOf, type Stroke, type TextAlign, type TextBackground, type TextElement } from './composition'
import { ElementBody, Strokes } from './ElementLayer'

let openLayers = 0
type LayerState = { reconSheet?: number; reconDepth?: number } | null

/**
 * Fecha com o botao voltar do celular: cada camada aberta (painel, confirmacao) cria um passo no historico com a sua
 * profundidade; o voltar fecha so as camadas acima do passo em que o historico parou.
 */
export function useBackToClose(open: boolean, onClose: () => void) {
  const close = useRef(onClose)
  useEffect(() => { close.current = onClose }, [onClose])
  useEffect(() => {
    if (!open) return
    // O historico guarda uma copia do estado: compara pelo identificador, nao pelo objeto.
    const id = Math.random()
    const depth = ++openLayers
    const ours = () => (window.history.state as LayerState)?.reconSheet === id
    window.history.pushState({ reconSheet: id, reconDepth: depth }, '', window.location.href)
    let poppedByBack = false
    const pop = () => {
      // Parou nesta camada ou acima (um painel aberto por cima foi fechado): continua aberta.
      if (((window.history.state as LayerState)?.reconDepth ?? 0) >= depth) return
      poppedByBack = true
      close.current()
    }
    window.addEventListener('popstate', pop)
    return () => {
      openLayers--
      window.removeEventListener('popstate', pop)
      // Fechado pelo app (nao pelo voltar): o passo vira "vazio" e sai do historico, se ninguem abriu outro por cima.
      if (!poppedByBack && ours()) {
        window.history.replaceState({ reconDepth: 0 }, '', window.location.href)
        window.setTimeout(() => { if ((window.history.state as LayerState)?.reconDepth === 0 && !(window.history.state as LayerState)?.reconSheet) window.history.back() }, 0)
      }
    }
  }, [open])
}

/** Painel que sobe da parte de baixo: fecha tocando fora, arrastando para baixo, no Esc ou no voltar. */
export function BottomSheet({ title, onClose, children, tall }: { title: string; onClose: () => void; children: ReactNode; tall?: boolean }) {
  const [drag, setDrag] = useState(0)
  const start = useRef<number | null>(null)
  useBackToClose(true, onClose)
  useEffect(() => {
    const key = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', key)
    return () => window.removeEventListener('keydown', key)
  }, [onClose])
  const down = (event: ReactPointerEvent<HTMLDivElement>) => {
    start.current = event.clientY
    try { event.currentTarget.setPointerCapture(event.pointerId) } catch { /* sem captura */ }
  }
  const move = (event: ReactPointerEvent) => { if (start.current !== null) setDrag(Math.max(0, event.clientY - start.current)) }
  const up = () => { if (drag > 90) onClose(); start.current = null; setDrag(0) }
  return <div className="recon-sheet-backdrop" onClick={onClose}>
    <section className={`recon-sheet-panel ${tall ? 'is-tall' : ''}`} role="dialog" aria-modal="true" aria-label={title}
      style={{ transform: drag ? `translateY(${drag}px)` : undefined }} onClick={event => event.stopPropagation()}>
      <div className="recon-sheet-handle" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up} aria-hidden="true"><span/></div>
      {children}
    </section>
  </div>
}

const ALIGN_NEXT: Record<TextAlign, TextAlign> = { CENTER: 'LEFT', LEFT: 'RIGHT', RIGHT: 'CENTER' }
const BACKGROUND_NEXT: Record<TextBackground, TextBackground> = { NONE: 'SOLID', SOLID: 'SOFT', SOFT: 'NONE' }
const BACKGROUND_LABEL: Record<TextBackground, string> = { NONE: 'Sem fundo', SOLID: 'Fundo sólido', SOFT: 'Fundo translúcido' }

export type TextDraft = Pick<TextElement, 'text' | 'font' | 'color' | 'align' | 'background' | 'size'>

/** Editor de texto em tela cheia sobre o quadro: digitar, fonte, cor, alinhamento, fundo e tamanho. */
export function TextEditor({ initial, onDone, onCancel }: { initial: TextDraft; onDone: (draft: TextDraft) => void; onCancel: () => void }) {
  const [draft, setDraft] = useState(initial)
  const input = useRef<HTMLTextAreaElement>(null)
  useBackToClose(true, onCancel)
  useEffect(() => {
    const element = input.current
    if (!element) return
    element.focus()
    element.setSelectionRange(element.value.length, element.value.length)
  }, [])
  const font = fontOf(draft.font)
  const AlignIcon = draft.align === 'LEFT' ? AlignLeft : draft.align === 'RIGHT' ? AlignRight : AlignCenter
  const preview: TextElement = { ...draft, text: draft.text || ' ', id: 'draft', type: 'TEXT', x: 0.5, y: 0.5, scale: 1, rotation: 0, z: 0 }
  return <div className="recon-text-editor" role="dialog" aria-modal="true" aria-label="Editar texto">
    <header className="recon-top">
      <button type="button" className="recon-pill" onClick={() => setDraft({ ...draft, align: ALIGN_NEXT[draft.align] })} aria-label={`Alinhamento: ${draft.align.toLowerCase()}`}><AlignIcon/></button>
      <button type="button" className={`recon-pill ${draft.background !== 'NONE' ? 'active' : ''}`} onClick={() => setDraft({ ...draft, background: BACKGROUND_NEXT[draft.background] })}
        aria-label={BACKGROUND_LABEL[draft.background]} title={BACKGROUND_LABEL[draft.background]}><Type/> {BACKGROUND_LABEL[draft.background]}</button>
      <button type="button" className="recon-pill recon-pill--primary" onClick={() => onDone(draft)}><Check/> Concluir</button>
    </header>
    <div className="recon-text-stage">
      <label className="recon-text-size" aria-label="Tamanho do texto">
        <input type="range" min={0.03} max={0.2} step={0.005} value={draft.size} onChange={event => setDraft({ ...draft, size: Number(event.target.value) })}/>
      </label>
      <div className="recon-text-input">
        {/* O texto aparece como ficara no Recon; o campo transparente por cima recebe a digitacao. */}
        <div className="recon-text-preview" aria-hidden="true"><ElementBody element={preview}/></div>
        <textarea ref={input} value={draft.text} maxLength={MAX_TEXT} rows={3} placeholder="Digite aqui…" aria-label="Texto do Recon"
          style={{ fontFamily: font.family, fontWeight: font.weight, textAlign: draft.align.toLowerCase() as 'left' }}
          onChange={event => setDraft({ ...draft, text: event.target.value })}/>
      </div>
    </div>
    <div className="recon-text-tools">
      <div className="recon-chips" role="radiogroup" aria-label="Fonte">{FONTS.map(option => <button key={option.value} type="button" role="radio" aria-checked={draft.font === option.value}
        className={draft.font === option.value ? 'active' : ''} style={{ fontFamily: option.family, fontWeight: option.weight }} onClick={() => setDraft({ ...draft, font: option.value })}>{option.label}</button>)}</div>
      <ColorRow value={draft.color} onChange={color => setDraft({ ...draft, color })}/>
    </div>
  </div>
}

export function ColorRow({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  return <div className="recon-colors" role="radiogroup" aria-label="Cor">
    {COLORS.map(color => <button key={color} type="button" role="radio" aria-checked={value === color} aria-label={`Cor ${color}`}
      className={value === color ? 'active' : ''} style={{ background: color }} onClick={() => onChange(color)}/>)}
    <label className="recon-color-custom" aria-label="Outra cor"><input type="color" value={value.toLowerCase()} onChange={event => onChange(event.target.value.toUpperCase())}/></label>
  </div>
}

/** Desenho livre sobre o quadro. Os tracos ficam em coordenadas relativas e viram um unico elemento. */
export function DrawPad({ initial, onDone, onCancel }: { initial: Stroke[]; onDone: (strokes: Stroke[]) => void; onCancel: () => void }) {
  const [strokes, setStrokes] = useState<Stroke[]>(initial)
  const [redo, setRedo] = useState<Stroke[]>([])
  const [color, setColor] = useState('#C9A227')
  const [width, setWidth] = useState(0.012)
  const surface = useRef<HTMLDivElement>(null)
  const [current, setCurrent] = useState<Stroke | null>(null)
  const drawing = useRef<Stroke | null>(null)
  useBackToClose(true, onCancel)
  const point = (event: ReactPointerEvent) => {
    const box = surface.current!.getBoundingClientRect()
    return [Math.min(1, Math.max(0, (event.clientX - box.left) / box.width)), Math.min(1, Math.max(0, (event.clientY - box.top) / box.height))]
  }
  const down = (event: ReactPointerEvent<HTMLDivElement>) => {
    // So um dedo desenha; o segundo dedo e ignorado para nao riscar sem querer.
    if (drawing.current || strokes.length >= 80) return
    try { event.currentTarget.setPointerCapture(event.pointerId) } catch { /* sem captura */ }
    drawing.current = { color, width, points: point(event).map(round) }
    setCurrent({ ...drawing.current })
  }
  const move = (event: ReactPointerEvent) => {
    const stroke = drawing.current
    if (!stroke || stroke.points.length >= 2400) return
    const [x, y] = point(event)
    const lastX = stroke.points[stroke.points.length - 2]
    const lastY = stroke.points[stroke.points.length - 1]
    if (Math.hypot(x - lastX, y - lastY) < 0.004) return
    stroke.points.push(round(x), round(y))
    setCurrent({ ...stroke, points: [...stroke.points] })
  }
  const up = () => {
    const stroke = drawing.current
    drawing.current = null
    setCurrent(null)
    if (stroke) { setStrokes(list => [...list, stroke]); setRedo([]) }
  }
  const undo = () => { if (!strokes.length) return; setRedo([strokes[strokes.length - 1], ...redo]); setStrokes(strokes.slice(0, -1)) }
  const again = () => { if (!redo.length) return; setStrokes([...strokes, redo[0]]); setRedo(redo.slice(1)) }
  return <div className="recon-draw" role="dialog" aria-modal="true" aria-label="Desenhar">
    <header className="recon-top">
      <button type="button" className="recon-round" onClick={undo} disabled={!strokes.length} aria-label="Desfazer"><Undo2/></button>
      <button type="button" className="recon-round" onClick={again} disabled={!redo.length} aria-label="Refazer"><Redo2/></button>
      <button type="button" className="recon-round" onClick={() => { setRedo([]); setStrokes([]) }} disabled={!strokes.length} aria-label="Apagar desenho"><Eraser/></button>
      <button type="button" className="recon-pill recon-pill--primary" onClick={() => onDone(strokes)}><Check/> Concluir</button>
    </header>
    <div ref={surface} className="recon-draw-surface" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}>
      <Strokes strokes={current ? [...strokes, current] : strokes}/>
    </div>
    <div className="recon-draw-tools">
      <label className="recon-width"><span>Espessura</span>
        <input type="range" min={0.004} max={0.05} step={0.002} value={width} onChange={event => setWidth(Number(event.target.value))}/>
        <i style={{ width: `${Math.max(4, width * 360)}px`, height: `${Math.max(4, width * 360)}px`, background: color }}/>
      </label>
      <ColorRow value={color} onChange={setColor}/>
    </div>
  </div>
}

const round = (value: number) => Math.round(value * 10000) / 10000

/** Painel "Adicionar": so recursos que funcionam de ponta a ponta (menção e adesivos). */
export function StickerSheet({ mentionsLeft, onMention, onSticker, onClose }: { mentionsLeft: number; onMention: (operator: OperatorSummary) => void; onSticker: (emoji: string) => void; onClose: () => void }) {
  const [view, setView] = useState<'menu' | 'mention' | 'stickers'>('menu')
  return <BottomSheet title="Adicionar" onClose={onClose} tall={view !== 'menu'}>
    {view === 'menu' && <div className="recon-add-menu">
      <h2>Adicionar</h2>
      <div className="recon-add-grid">
        <button type="button" onClick={() => setView('mention')} disabled={mentionsLeft <= 0}><AtSign/><b>@ MENÇÃO</b><small>{mentionsLeft <= 0 ? 'Limite de 10 marcações' : 'Marque um operador'}</small></button>
        <button type="button" onClick={() => setView('stickers')}><Smile/><b>ADESIVOS</b><small>Ícones táticos e emojis</small></button>
      </div>
    </div>}
    {view === 'mention' && <MentionSearch onBack={() => setView('menu')} onSelect={onMention}/>}
    {view === 'stickers' && <div className="recon-sticker-pick">
      <header><button type="button" className="recon-round" onClick={() => setView('menu')} aria-label="Voltar"><ArrowLeft/></button><h2>Adesivos</h2></header>
      <div className="recon-sticker-grid">{STICKERS.map(emoji => <button key={emoji} type="button" onClick={() => onSticker(emoji)} aria-label={`Adesivo ${emoji}`}>{emoji}</button>)}</div>
    </div>}
  </BottomSheet>
}

/** Busca de operadores reais com espera de 300 ms entre as teclas; mostra carregando, vazio e erro de conexão. */
export function MentionSearch({ onBack, onSelect }: { onBack: () => void; onSelect: (operator: OperatorSummary) => void }) {
  const [query, setQuery] = useState('')
  const [state, setState] = useState<{ status: 'idle' | 'loading' | 'done' | 'error'; items: OperatorSummary[] }>({ status: 'idle', items: [] })
  const [attempt, setAttempt] = useState(0)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => { input.current?.focus() }, [])
  useEffect(() => {
    const term = query.trim().replace(/^@/, '')
    if (term.length < 2) { setState({ status: 'idle', items: [] }); return }
    let active = true
    setState(current => ({ status: 'loading', items: current.items }))
    const timer = window.setTimeout(() => {
      searchOperators(term)
        .then(result => { if (active) setState({ status: 'done', items: result.items }) })
        .catch(() => { if (active) setState({ status: 'error', items: [] }) })
    }, 300)
    return () => { active = false; window.clearTimeout(timer) }
  }, [query, attempt])
  return <div className="recon-mention-search">
    <header><button type="button" className="recon-round" onClick={onBack} aria-label="Voltar"><ArrowLeft/></button><h2>Marcar operador</h2></header>
    <label className="recon-search"><Search/><input ref={input} value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar operador…"
      autoCapitalize="none" autoCorrect="off" spellCheck={false} aria-label="Buscar operador"/>{state.status === 'loading' && <LoaderCircle className="spin"/>}</label>
    <p className="recon-search-label">OPERADORES</p>
    {state.status === 'idle' && <p className="recon-search-note">Digite o nome, o codinome ou o @usuário (pelo menos 2 letras).</p>}
    {state.status === 'error' && <p className="recon-search-note" role="alert">Sem conexão com o servidor. <button type="button" className="recon-link" onClick={() => setAttempt(value => value + 1)}>Tentar de novo</button></p>}
    {state.status === 'done' && !state.items.length && <p className="recon-search-note">Nenhum operador encontrado.</p>}
    <ul className="recon-search-results">{state.items.map(operator => <li key={operator.username}>
      <button type="button" onClick={() => onSelect(operator)}>
        <span className="recon-search-avatar">{(operator.callsign || operator.username).charAt(0).toUpperCase()}</span>
        <span><b>{operator.callsign || operator.displayName}</b><small>@{operator.username}{operator.teamName ? ` · ${operator.teamName}` : ''}</small></span>
      </button>
    </li>)}</ul>
  </div>
}

/** Configuracoes que valem no Recon: quem ve (regra fixa da plataforma) e guardar automaticamente num Dossie. */
export function SettingsSheet({ dossiers, dossierId, onDossier, onClose }: { dossiers: DossierSummary[] | null; dossierId: string; onDossier: (id: string) => void; onClose: () => void }) {
  return <BottomSheet title="Configurações do Recon" onClose={onClose}>
    <div className="recon-settings">
      <h2>Configurações do Recon</h2>
      <section>
        <UserRound/>
        <div><b>Quem pode ver</b><small>Seus seguidores e os operadores que você marcar. O Recon some depois de 24 horas.</small></div>
      </section>
      <label>
        <span><b>Salvar no Dossiê</b><small>Guarda o Recon no seu perfil, mesmo depois das 24 horas.</small></span>
        <select value={dossierId} onChange={event => onDossier(event.target.value)} disabled={dossiers === null}>
          <option value="">Não salvar</option>
          {dossiers?.map(dossier => <option key={dossier.id} value={dossier.id}>{dossier.title}</option>)}
        </select>
      </label>
      {dossiers?.length === 0 && <p className="recon-search-note">Você ainda não tem Dossiês. Crie um abrindo um Recon seu e tocando em "Salvar no Dossiê".</p>}
      <button type="button" className="recon-pill recon-pill--primary" onClick={onClose}><Check/> Pronto</button>
    </div>
  </BottomSheet>
}
