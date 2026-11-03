import { CalendarDays, Check, Clock3, MapPin, MessageCircle, Navigation, Target, Users } from 'lucide-react'
import { useState } from 'react'
import { getCallUp, requestOperationParticipation, type CallUp, type CallUpTeam, type PlayerRole } from '../api'
import { RoleChooser } from '../operation/RoleChooser'
import { DEFAULT_ROLE } from '../operation/roles'
import { Sheet } from './FeedSheets'

const MODALITIES: Record<string, string> = {
  ELIMINATION: 'Mata-mata', CONQUEST: 'Conquista', CAPTURE_THE_FLAG: 'Captura de bandeira', BASE_DEFENSE: 'Defesa de base',
  ATTACK_DEFENSE: 'Ataque e defesa', ESCORT: 'Escolta', RESCUE: 'Resgate', DOMINATION: 'Dominação', OBJECTIVES: 'Missão por objetivos',
  CHAMPIONSHIP: 'Campeonato', THEMED: 'Operação temática', CUSTOM: 'Personalizada',
}
const ENLISTED = ['APPROVED', 'CONFIRMED', 'CHECKED_IN']

const day = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: 'short' }).replace('.', '')
const money = (value?: number | null) => value ? value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : 'Gratuita'

/** Rotulo do botao conforme a inscricao de quem ve (as regras de vaga e aprovacao sao as da operacao). */
function enlistLabel(callUp: CallUp) {
  if (callUp.viewerStatus && ENLISTED.includes(callUp.viewerStatus)) return { text: 'ALISTADO', done: true }
  if (callUp.viewerStatus === 'REQUESTED') return { text: 'PEDIDO ENVIADO', done: true }
  if (callUp.viewerStatus === 'WAITING_LIST') return { text: 'NA LISTA DE ESPERA', done: true }
  if (callUp.viewerStatus === 'REMOVED') return { text: 'REMOVIDO', done: true }
  if (!callUp.registrationOpen) return { text: 'INSCRIÇÕES ENCERRADAS', done: true }
  if (callUp.remaining === 0) return callUp.waitingListEnabled ? { text: 'ENTRAR NA ESPERA', done: false } : { text: 'LOTADO', done: true }
  return { text: 'ALISTAR', done: false }
}

/** Opcoes de alistamento por equipes: o lado e, quando a equipe tem pelotoes, o pelotao. Lista unica nao tem opcoes. */
function choices(callUp: CallUp) {
  if (callUp.registrationModel !== 'TEAMS') return []
  return callUp.teams.flatMap(team => team.squads.length === 0
    ? [{ team, squad: null as CallUpTeam['squads'][number] | null }]
    : team.squads.map(squad => ({ team, squad })))
}
const UNLIMITED = 100000

export function CallUpCard({ callUp: initial, onRequireLogin }: { callUp: CallUp; onRequireLogin?: () => void }) {
  const [callUp, setCallUp] = useState(initial)
  const [choosing, setChoosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [role, setRole] = useState<PlayerRole>(DEFAULT_ROLE)
  const [choice, setChoice] = useState('')
  const label = enlistLabel(callUp)
  const options = choices(callUp)
  const percent = callUp.limit ? Math.min(100, Math.round(callUp.occupied / callUp.limit * 100)) : 0

  const enlist = async (teamId: string | null, squadId: string | null) => {
    setBusy(true); setMessage('')
    try {
      const result = await requestOperationParticipation(callUp.id, role, teamId, squadId)
      setCallUp(await getCallUp(callUp.id))
      setMessage(result.message)
      setChoosing(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Não foi possível se alistar.')
    } finally {
      setBusy(false)
    }
  }
  const start = () => {
    if (onRequireLogin) return onRequireLogin()
    if (callUp.registrationModel === 'TEAMS' && options.length === 0) { setMessage('O organizador ainda não abriu as equipes para inscrição.'); return }
    setChoice(options.length === 1 ? (options[0].squad?.id ?? options[0].team.id) : '')
    setChoosing(true)
  }
  const whatsapp = () => {
    const text = `CONVOCAÇÃO: ${callUp.name} · ${day(callUp.date)} às ${callUp.startTime.slice(0, 5)} · ${callUp.fieldName}. Aliste-se: ${callUp.shareUrl}`
    window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener')
  }

  return <article className="callup-card" aria-label={`Convocação: ${callUp.name}`}>
    <div className="callup-inner">
      <p className="callup-eyebrow"><Target/> CONVOCAÇÃO</p>
      <h2>{callUp.name}</h2>
      <ul className="callup-facts">
        <li><CalendarDays/> {day(callUp.date)}</li>
        <li><Clock3/> Início {callUp.startTime.slice(0, 5)}{callUp.presentationTime && ` · apresentação ${callUp.presentationTime.slice(0, 5)}`}</li>
        <li><MapPin/> {callUp.fieldName} · {callUp.city}/{callUp.stateCode}</li>
        <li><Users/> {callUp.modality === 'CUSTOM' && callUp.customModality ? callUp.customModality : MODALITIES[callUp.modality] ?? callUp.modality} · {money(callUp.price)}</li>
      </ul>
      {callUp.directionsUrl && <a className="callup-directions" href={callUp.directionsUrl} target="_blank" rel="noopener noreferrer"><Navigation/> Como chegar</a>}
      <div className={`callup-spots ${callUp.lastSpots ? 'callup-spots--last' : ''}`}>
        <p><b>{callUp.occupied}{callUp.limit ? `/${callUp.limit}` : ''}</b> {callUp.limit ? 'vagas ocupadas' : 'alistados'}
          {callUp.lastSpots && <em> · Últimas {callUp.remaining} {callUp.remaining === 1 ? 'vaga' : 'vagas'}</em>}
          {callUp.remaining === 0 && <em> · Lotado</em>}</p>
        {callUp.limit ? <div className="callup-bar" role="progressbar" aria-valuemin={0} aria-valuemax={callUp.limit} aria-valuenow={callUp.occupied}
          aria-label="Vagas ocupadas"><i style={{ width: `${percent}%` }}/></div> : null}
      </div>
      <div className="callup-actions">
        <button type="button" className={`callup-enlist ${label.done ? 'callup-enlist--done' : ''}`} disabled={busy || label.done} onClick={start}>
          {label.done && label.text === 'ALISTADO' && <Check/>}{busy ? 'ALISTANDO…' : label.text}
        </button>
        <button type="button" className="callup-share" onClick={whatsapp} aria-label="Compartilhar convocação no WhatsApp"><MessageCircle/></button>
      </div>
      {callUp.approvalRequired && !callUp.viewerStatus && <p className="callup-note">O organizador aprova cada inscrição.</p>}
      {message && <p className="callup-note" role="status">{message}</p>}
    </div>
    {choosing && <Sheet title="Alistar" onClose={() => setChoosing(false)} labelledBy="callup-choose-title">
      <div className="callup-enlist-form">
        <RoleChooser value={role} onChange={setRole}/>
        {options.length > 0 && <fieldset className="role-chooser"><legend>Escolha sua equipe</legend>
          {options.map(({ team, squad }) => {
            const capacity = squad?.capacity ?? team.capacity
            const occupied = squad?.occupied ?? team.occupied
            const full = occupied >= capacity
            const key = squad?.id ?? team.id
            return <button type="button" key={key} className={`create-choice ${choice === key ? 'active' : ''}`} aria-pressed={choice === key}
              disabled={busy || (full && !callUp.waitingListEnabled)} onClick={() => setChoice(key)}>
              <Users/><span><b>{squad ? `${team.name} · ${squad.name}` : team.name}</b><small>{capacity >= UNLIMITED ? `${occupied} inscritos` : `${occupied}/${capacity} ${full ? '· lotado' : 'ocupadas'}`}</small></span>
            </button>
          })}
        </fieldset>}
        <button type="button" className="callup-enlist" disabled={busy || (options.length > 0 && !choice)} onClick={() => {
          const picked = options.find(option => (option.squad?.id ?? option.team.id) === choice)
          void enlist(picked?.team.id ?? null, picked?.squad?.id ?? null)
        }}>{busy ? 'ALISTANDO…' : 'CONFIRMAR ALISTAMENTO'}</button>
      </div>
    </Sheet>}
  </article>
}
