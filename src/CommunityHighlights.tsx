import { FormEvent, useMemo, useState } from 'react'
import { ArrowRight, CalendarDays, MapPin, Pause, Play, Plus, ShieldCheck, Square, X } from 'lucide-react'

type HighlightStatus = 'Ativo' | 'Pausado' | 'Encerrado'
type HighlightKind = 'Operação' | 'Classificado' | 'Equipe' | 'Campo' | 'Campeonato' | 'Comunicado'

type CommunityHighlight = {
  id: number
  kind: HighlightKind
  title: string
  description: string
  region: string
  startsAt: string
  endsAt: string
  position: number
  reason: string
  selectedBy: string
  status: HighlightStatus
  dateOrder: number
  relevance: number
  recency: number
}

const initialHighlights: CommunityHighlight[] = [
  { id: 1, kind: 'Operação', title: 'Linha de Frente', description: 'Dominação por objetivos · 26 JUL', region: 'Colombo, PR', startsAt: '2026-07-20', endsAt: '2026-07-27', position: 1, reason: 'Operação próxima com vagas abertas', selectedBy: 'Administração Operador Zero', status: 'Ativo', dateOrder: 1, relevance: 10, recency: 10 },
  { id: 2, kind: 'Campo', title: 'Campo Bravo Zero', description: 'Estrutura para operações e treinamentos.', region: 'Curitiba e região', startsAt: '2026-07-20', endsAt: '2026-08-03', position: 2, reason: 'Relevância regional', selectedBy: 'Administração Operador Zero', status: 'Ativo', dateOrder: 2, relevance: 8, recency: 8 },
  { id: 3, kind: 'Classificado', title: 'Colete modular oliva', description: 'Item permitido · usado · Curitiba, PR', region: 'Curitiba, PR', startsAt: '2026-07-21', endsAt: '2026-07-28', position: 3, reason: 'Conteúdo recente da comunidade', selectedBy: 'Administração Operador Zero', status: 'Ativo', dateOrder: 3, relevance: 7, recency: 9 },
]

function orderCommunityHighlights(items: CommunityHighlight[]) {
  return [...items].filter(item => item.status === 'Ativo').sort((a, b) => a.position - b.position || a.dateOrder - b.dateOrder || b.relevance - a.relevance || b.recency - a.recency || a.id - b.id)
}

export function CommunityHighlights({ onManage }: { onManage: () => void }) {
  const highlights = orderCommunityHighlights(initialHighlights)
  return <section className="community-highlights"><header><div><p>SELEÇÃO GRATUITA · ROTAÇÃO EQUILIBRADA</p><h2>Destaques da comunidade</h2></div><button onClick={onManage}>Gerenciar destaques <ArrowRight/></button></header><div>{highlights.map((item, index) => <article className={index === 0 ? 'community-highlight-main' : ''} key={item.id}><span>{item.kind}</span><small><MapPin/> {item.region}</small><h3>{item.title}</h3><p>{item.description}</p><button>Ver conteúdo</button></article>)}</div><footer>Nenhum usuário pode comprar prioridade. A seleção considera vigência, região, data, relevância, recência e rotação.</footer></section>
}

export default function CommunityHighlightsPage() {
  const [items, setItems] = useState(initialHighlights)
  const [notice, setNotice] = useState('')
  const activeItems = useMemo(() => orderCommunityHighlights(items), [items])
  const updateStatus = (id: number, status: HighlightStatus) => {
    setItems(current => current.map(item => item.id === id ? { ...item, status } : item))
    setNotice(`Destaque ${status.toLowerCase()} no histórico local desta demonstração.`)
  }
  const create = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const next: CommunityHighlight = {
      id: Date.now(), kind: String(data.get('kind')) as HighlightKind, title: String(data.get('title')).trim(), description: String(data.get('description')).trim(), region: String(data.get('region')).trim(), startsAt: String(data.get('startsAt')), endsAt: String(data.get('endsAt')), position: Number(data.get('position')), reason: String(data.get('reason')).trim(), selectedBy: 'Administrador demonstrativo', status: 'Ativo', dateOrder: items.length + 1, relevance: 5, recency: 10,
    }
    setItems(current => [...current, next])
    setNotice('Destaque criado somente na memória desta tela; nenhum dado foi enviado.')
    event.currentTarget.reset()
  }
  return <main className="highlights-admin"><header><div><p>ADMINISTRAÇÃO DEMONSTRATIVA</p><h1>Gerenciar<br/><em>destaques.</em></h1><span>Curadoria gratuita e auditável, sem compra de prioridade.</span></div><ShieldCheck/></header><section className="highlight-demo-note"><ShieldCheck/><p><strong>Sem persistência.</strong> Alterações existem apenas enquanto esta tela permanecer aberta. Permissões administrativas deverão ser validadas pelo backend na implementação real.</p></section><div className="highlights-admin-grid"><form onSubmit={create}><p>NOVO DESTAQUE</p><h2>Selecionar conteúdo</h2><div className="highlight-form-grid"><label><span>Tipo</span><select name="kind" required><option>Operação</option><option>Classificado</option><option>Equipe</option><option>Campo</option><option>Campeonato</option><option>Comunicado</option></select></label><label><span>Posição</span><input name="position" type="number" min="1" max="20" defaultValue="1" required/></label><label className="wide"><span>Conteúdo</span><input name="title" maxLength={120} required/></label><label className="wide"><span>Descrição</span><textarea name="description" maxLength={240} required/></label><label><span>Início</span><input name="startsAt" type="date" required/></label><label><span>Fim</span><input name="endsAt" type="date" required/></label><label className="wide"><span>Região</span><input name="region" maxLength={100} required/></label><label className="wide"><span>Motivo da seleção</span><textarea name="reason" maxLength={500} required/></label></div><button className="button"><Plus/> Criar destaque gratuito</button></form><section className="highlight-list"><header><div><p>VIGENTES E AGENDADOS</p><h2>{activeItems.length} ativos</h2></div><CalendarDays/></header>{items.map(item => <article key={item.id}><div><span>{item.kind} · POSIÇÃO {item.position}</span><h3>{item.title}</h3><p>{item.region} · {item.startsAt} a {item.endsAt}</p><small>{item.reason} · Selecionado por {item.selectedBy}</small></div><strong className={`highlight-status highlight-status--${item.status.toLowerCase()}`}>{item.status}</strong><footer>{item.status !== 'Ativo' && item.status !== 'Encerrado' && <button onClick={() => updateStatus(item.id, 'Ativo')}><Play/> Ativar</button>}{item.status === 'Ativo' && <button onClick={() => updateStatus(item.id, 'Pausado')}><Pause/> Pausar</button>}{item.status !== 'Encerrado' && <button onClick={() => updateStatus(item.id, 'Encerrado')}><Square/> Encerrar</button>}{item.status === 'Encerrado' && <button disabled><X/> Encerrado</button>}</footer></article>)}</section></div>{notice && <p className="highlight-notice" role="status">{notice}</p>}</main>
}
