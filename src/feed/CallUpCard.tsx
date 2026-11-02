import { CalendarDays, Check, Clock3, MapPin, MessageCircle, Navigation, Target, Users } from 'lucide-react'
import { useState } from 'react'
import { getCallUp, requestOperationParticipation, type CallUp, type CallUpTeam } from '../api'
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
  if (callUp.remaining === 0) return callUp.waitingListEnabled ? { text: 'ENTRAR NA ESPERA', done: false } : { text: 'LOTADO', done: true }
  return { text: 'ALISTAR', done: false }
}

/** Opcoes de alistamento: em jogo pequeno escolhe-se o lado; nos demais, o lado e o pelotao. */
function choices(callUp: CallUp) {
  return callUp.teams.flatMap(team => callUp.gameSize === 'SMALL'
    ? [{ team, squad: null as CallUpTeam['squads'][number] | null }]
    : team.squads.map(squad => ({ team, squad })))
}

export function CallUpCard({ callUp: initial, onRequireLogin }: { callUp: CallUp; onRequireLogin?: () => void }) {
  const [callUp, setCallUp] = useState(initial)
  const [choosing, setChoosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const label = enlistLabel(callUp)
  const options = choices(callUp)
  const percent = callUp.limit ? Math.min(100, Math.round(callUp.occupied / callUp.limit * 100)) : 0

  const enlist = async (teamId: string, squadId: string | null) => {
    setBusy(true); setMessage('')
    try {
      const result = await requestOperationParticipation(callUp.id, teamId, squadId)
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
    if (options.length === 1) void enlist(options[0].team.id, options[0].squad?.id ?? null)
    else if (options.length > 1) setChoosing(true)
    else setMessage('O organizador ainda não abriu os times para inscrição.')
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
    {choosing && <Sheet title="Escolha onde se alistar" onClose={() => setChoosing(false)} labelledBy="callup-choose-title">
      {options.map(({ team, squad }) => {
        const capacity = squad?.capacity ?? team.capacity
        const occupied = squad?.occupied ?? team.occupied
        const full = occupied >= capacity
        return <button type="button" key={squad?.id ?? team.id} className="create-choice" disabled={busy || (full && !callUp.waitingListEnabled)}
          onClick={() => void enlist(team.id, squad?.id ?? null)}>
          <Users/><span><b>{squad ? `${team.name} · ${squad.name}` : team.name}</b><small>{occupied}/{capacity} {full ? '· lotado' : 'ocupadas'}</small></span>
        </button>
      })}
    </Sheet>}
  </article>
}
