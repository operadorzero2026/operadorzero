import { Target } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { getOperationHistory, publicMediaUrl, type OperationHistoryItem } from '../api'
import { roleText } from './roles'
import './operation-page.css'

/** "Operacoes realizadas" do perfil: continua disponivel depois do jogo, com equipe e funcoes de cada participacao. */
export function OperationHistory({ username, onOpen, fallback }: { username: string; onOpen: (id: string) => void; fallback?: ReactNode }) {
  const [items, setItems] = useState<OperationHistoryItem[] | null>(null)
  useEffect(() => {
    let active = true
    getOperationHistory(username).then(result => { if (active) setItems(result) }).catch(() => { if (active) setItems(null) })
    return () => { active = false }
  }, [username])
  if (items === null) return <>{fallback}</>
  return <section className="oz-profile-card">
    <h2>OPERAÇÕES REALIZADAS</h2>
    {items.length === 0 ? <p>Nenhuma operação realizada ainda.</p> : <div className="op-history">
      {items.map(item => <a key={item.id} href={`/convocacao/${item.id}`} onClick={event => { event.preventDefault(); onOpen(item.id) }}>
        {item.coverUrl ? <img src={publicMediaUrl(item.coverUrl)} alt=""/> : <span className="op-history-empty"><Target/></span>}
        <span><b>{item.name}</b>
          <small>{new Date(`${item.date}T12:00:00`).toLocaleDateString('pt-BR')} · por {item.organizerCallsign}</small>
          <small>{[item.teamName, item.primaryRole ? roleText(item) : null].filter(Boolean).join(' · ') || 'Participou'}</small>
        </span>
      </a>)}
    </div>}
  </section>
}
