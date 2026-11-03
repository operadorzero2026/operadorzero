import { CalendarDays, LoaderCircle, Search, Target, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getOperations, type Operation } from '../api'
import './operation-page.css'

export type TaggedOperation = { id: string; name: string; date: string }
const day = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR')

/** "Marcar operacao" no post: busca operacoes futuras e realizadas por nome; qualquer pessoa logada pode marcar. */
export function OperationTagPicker({ value, onChange }: { value: TaggedOperation | null; onChange: (operation: TaggedOperation | null) => void }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [state, setState] = useState<{ status: 'idle' | 'loading' | 'done' | 'error'; items: Operation[] }>({ status: 'idle', items: [] })
  useEffect(() => {
    if (!open) return
    const term = query.trim()
    if (term.length < 2) { setState({ status: 'idle', items: [] }); return }
    let active = true
    setState(current => ({ status: 'loading', items: current.items }))
    const timer = window.setTimeout(() => {
      getOperations(term).then(result => {
        if (!active) return
        const items = [...result.items].filter(item => item.status !== 'DRAFT' && item.status !== 'CANCELLED')
          .sort((a, b) => b.operationDate.localeCompare(a.operationDate)).slice(0, 12)
        setState({ status: 'done', items })
      }).catch(() => { if (active) setState({ status: 'error', items: [] }) })
    }, 300)
    return () => { active = false; window.clearTimeout(timer) }
  }, [query, open])

  if (value) return <div className="tag-op-chosen">
    <span><Target/> <b>{value.name}</b> <small>{day(value.date)}</small></span>
    <button type="button" onClick={() => onChange(null)} aria-label="Remover operação marcada"><X/></button>
  </div>
  if (!open) return <button type="button" className="tag-op-open" onClick={() => setOpen(true)}><Target/> Marcar operação</button>
  return <div className="tag-op">
    <label className="tag-op-search"><Search/><input autoFocus value={query} onChange={event => setQuery(event.target.value)} placeholder="Nome da operação" aria-label="Buscar operação"/>
      {state.status === 'loading' && <LoaderCircle className="spin"/>}
      <button type="button" onClick={() => { setOpen(false); setQuery('') }} aria-label="Fechar busca"><X/></button></label>
    {state.status === 'idle' && <small>Digite pelo menos 2 letras. Aparecem operações futuras e realizadas.</small>}
    {state.status === 'error' && <small role="alert">Não foi possível buscar agora.</small>}
    {state.status === 'done' && state.items.length === 0 && <small>Nenhuma operação encontrada.</small>}
    <ul>{state.items.map(item => <li key={item.id}><button type="button" onClick={() => { onChange({ id: item.id, name: item.name, date: item.operationDate }); setOpen(false); setQuery('') }}>
      <b>{item.name}</b><small><CalendarDays/> {day(item.operationDate)} · {item.city}/{item.stateCode}</small>
    </button></li>)}</ul>
  </div>
}
