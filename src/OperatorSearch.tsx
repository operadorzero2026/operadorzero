import { Search, UserRound, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { getOperatorPublicProfile, type OperatorSummary, searchOperators } from './api'
import { operatorTeamLabel } from './operator-label'

type OperatorSearchProps = {
  onSelect?: (operator: OperatorSummary) => void
  compact?: boolean
}

export function OperatorSearch({ onSelect, compact = false }: OperatorSearchProps) {
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<OperatorSummary[]>([])
  const [selected, setSelected] = useState<OperatorSummary | null>(null)
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const request = useRef(0)

  useEffect(() => {
    const normalized = query.trim()
    if (normalized.length < 2) {
      setItems([])
      setStatus('idle')
      return
    }
    const current = ++request.current
    const timer = window.setTimeout(async () => {
      setStatus('loading')
      try {
        const response = await searchOperators(normalized)
        if (request.current === current) {
          setItems(response.items)
          setStatus('ready')
        }
      } catch {
        if (request.current === current) setStatus('error')
      }
    }, 350)
    return () => window.clearTimeout(timer)
  }, [query])

  const select = async (operator: OperatorSummary) => {
    if (onSelect) {
      onSelect(operator)
      setQuery('')
      setItems([])
      return
    }
    try {
      setSelected(await getOperatorPublicProfile(operator.username))
    } catch {
      setSelected(operator)
    }
  }

  return (
    <div className={`operator-search ${compact ? 'operator-search--compact' : ''}`}>
      <label>
        <Search />
        <input aria-label="Buscar operadores" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar operador por nick ou usuário" maxLength={80} />
        {query && (
          <button type="button" onClick={() => setQuery('')} aria-label="Limpar busca">
            <X />
          </button>
        )}
      </label>
      {query.trim().length >= 2 && (
        <div className="operator-search-results">
          <small>OPERADORES</small>
          {status === 'loading' && <p>Buscando operadores…</p>}
          {status === 'error' && <p>Não foi possível realizar a busca agora.</p>}
          {status === 'ready' && items.length === 0 && <p>Nenhum operador encontrado.</p>}
          {items.map((operator) => (
            <button type="button" key={operator.id} onClick={() => void select(operator)}>
              <span>
                <UserRound />
              </span>
              <div>
                <b>
                  {operatorTeamLabel(operator)}
                </b>
                <small>
                  @{operator.username}
                  {operator.city ? ` · ${operator.city}/${operator.stateCode}` : ''}
                </small>
              </div>
            </button>
          ))}
        </div>
      )}
      {selected && (
        <div className="operator-profile-dialog" role="dialog" aria-modal="true" aria-label="Perfil do operador">
          <button className="dialog-close" onClick={() => setSelected(null)} aria-label="Fechar">
            <X />
          </button>
          <span className="operator-avatar">{selected.callsign.charAt(0).toUpperCase()}</span>
          <small>OPERADOR</small>
          <h2>{operatorTeamLabel(selected)}</h2>
          <p>
            {selected.displayName} · @{selected.username}
          </p>
          <p>
            {selected.airsoftExperience ? `${selected.airsoftExperience} no airsoft` : 'Tempo no airsoft não informado'}
          </p>
          {selected.city && (
            <p>
              {selected.city}/{selected.stateCode}
            </p>
          )}
          <strong>{recruitmentLabel(selected.recruitmentStatus)}</strong>
        </div>
      )}
    </div>
  )
}

function recruitmentLabel(value?: string | null) {
  if (value === 'LOOKING_FOR_TEAM') return 'Procurando equipe'
  if (value === 'NOT_LOOKING') return 'Não procura equipe'
  return 'Operador independente'
}
