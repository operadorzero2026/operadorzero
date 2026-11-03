import { ArrowLeftRight, Ban, Check, CircleSlash, HeartPulse, ListOrdered, Lock, LockOpen, Radio, RotateCcw, UserMinus, Users, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  authorizeParticipantReturn, cancelOperationParticipation, cancelTeamChange, closeRegistration, confirmOperationPresence, decideOperationParticipant,
  decideTeamChange, getRegistration, removeParticipant, reopenRegistration, requestOperationParticipation, requestTeamChange, setTeamChangeRequests,
  updateMyRole, type OperationStructure, type ParticipantAction, type PlayerRole, type PrimaryRole, type RegistrationOverview, type RegistrationParticipant,
} from '../api'
import { RoleChooser } from './RoleChooser'
import { DEFAULT_ROLE, PRIMARY_ROLES, primaryLabel } from './roles'
import './registration.css'

const STATUS_LABELS: Record<string, string> = {
  REQUESTED: 'Aguardando aprovação', APPROVED: 'Inscrito', CONFIRMED: 'Presença confirmada', CHECKED_IN: 'Check-in feito', WAITING_LIST: 'Lista de espera',
}
const ACTIONS: Record<string, { action: ParticipantAction; label: string }[]> = {
  REQUESTED: [{ action: 'APPROVE', label: 'Aprovar' }, { action: 'REJECT', label: 'Recusar' }],
  WAITING_LIST: [{ action: 'APPROVE', label: 'Aprovar se houver vaga' }, { action: 'REJECT', label: 'Recusar' }],
  APPROVED: [{ action: 'CONFIRM', label: 'Confirmar presença' }, { action: 'CHECK_IN', label: 'Check-in' }],
  CONFIRMED: [{ action: 'CHECK_IN', label: 'Check-in' }],
  CHECKED_IN: [{ action: 'UNDO_CHECK_IN', label: 'Desfazer check-in' }],
}
const CLOSED_TEXT: Record<string, string> = {
  STARTED: 'Inscrições encerradas: o jogo já começou.',
  MANUAL: 'Inscrições encerradas pelo organizador.',
  STATUS: 'Inscrições encerradas.',
}
const when = (value: string) => new Date(value).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

type Filter = { roles: PrimaryRole[]; medic: boolean; radio: boolean; team: string }

/**
 * Inscricao da operacao: o jogador se inscreve escolhendo a funcao (e a equipe, se for por equipes), muda a funcao quando quiser,
 * pede troca de equipe e cancela antes do inicio. O organizador ve contadores, filtra, remove com motivo, autoriza retorno,
 * decide trocas e encerra/reabre as inscricoes. Todas as regras sao conferidas no servidor.
 */
export function OperationRegistrationPanel({ operationId, structure, onChanged }: { operationId: string; structure?: OperationStructure | null; onChanged?: () => void }) {
  const [data, setData] = useState<RegistrationOverview | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [pending, setPending] = useState('')
  const [role, setRole] = useState<PlayerRole>(DEFAULT_ROLE)
  const [team, setTeam] = useState('')
  const [squad, setSquad] = useState('')
  const [editingRole, setEditingRole] = useState(false)
  const [changeTo, setChangeTo] = useState('')
  const [view, setView] = useState<'list' | 'teams'>('list')
  const [filter, setFilter] = useState<Filter>({ roles: [], medic: false, radio: false, team: '' })
  const [removing, setRemoving] = useState<RegistrationParticipant | null>(null)
  const [reason, setReason] = useState('')

  const load = useCallback(async () => {
    try {
      const result = await getRegistration(operationId)
      setData(result)
      setError('')
      if (result.mine && result.mine.primaryRole) setRole({ primaryRole: result.mine.primaryRole, medic: result.mine.medic, radio: result.mine.radio })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar as inscrições.')
    }
  }, [operationId])
  useEffect(() => { void load() }, [load])

  const run = async (key: string, action: () => Promise<{ message: string } | unknown>) => {
    setPending(key); setNotice('')
    try {
      const result = await action()
      if (result && typeof result === 'object' && 'message' in result) setNotice(String((result as { message: string }).message))
      await load()
      onChanged?.()
      return true
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Não foi possível concluir.')
      return false
    } finally {
      setPending('')
    }
  }

  const squads = structure?.teams.find(item => item.id === team)?.squads ?? []
  const active = data?.mine && STATUS_LABELS[data.mine.status]
  const visible = useMemo(() => {
    if (!data) return []
    return data.participants.filter(p => (filter.roles.length === 0 || (p.primaryRole && filter.roles.includes(p.primaryRole)))
      && (!filter.medic || p.medic) && (!filter.radio || p.radio) && (!filter.team || p.teamId === filter.team))
  }, [data, filter])

  if (error) return <p className="module-feedback" role="alert">{error} <button type="button" onClick={() => void load()}>Tentar novamente</button></p>
  if (!data) return <p className="registration-loading">Carregando inscrições…</p>

  const teams = data.model === 'TEAMS'
  const toggleRole = (value: PrimaryRole) => setFilter(f => ({ ...f, roles: f.roles.includes(value) ? f.roles.filter(r => r !== value) : [...f.roles, value] }))
  const register = () => void run('register', () => requestOperationParticipation(operationId, role, teams ? team : null, teams ? squad || null : null))

  return <section className="registration" aria-label="Inscrições">
    <header className="registration-head">
      <div><small>INSCRIÇÕES · {teams ? 'POR EQUIPES' : 'LISTA ÚNICA'}</small><h3>{data.counts.total} {data.counts.total === 1 ? 'inscrito' : 'inscritos'}</h3></div>
      <span className={`registration-state ${data.open ? 'is-open' : ''}`}>{data.open ? <><LockOpen/> Abertas até o início</> : <><Lock/> {CLOSED_TEXT[data.closedReason ?? 'STATUS']}</>}</span>
    </header>
    {notice && <p className="registration-notice" role="status">{notice}</p>}

    {/* ---- Jogador ---- */}
    {data.mine?.status === 'REMOVED' && <div className="registration-alert" role="alert"><Ban/><p><b>Você foi removido desta operação.</b> Para participar novamente, entre em contato com o organizador.{data.mine.removalReason && <><br/><small>Motivo: {data.mine.removalReason}</small></>}</p></div>}

    {!active && data.mine?.status !== 'REMOVED' && data.open && <div className="registration-join">
      <RoleChooser value={role} onChange={setRole}/>
      {teams && <fieldset className="role-chooser">
        <legend>Escolha sua equipe</legend>
        <div className="registration-teams">{data.teams.map(item => <label key={item.id} className={team === item.id ? 'active' : ''}>
          <input type="radio" name="registration-team" checked={team === item.id} onChange={() => { setTeam(item.id); setSquad('') }}/>
          <i style={{ background: item.color }}/><b>{item.name}</b><small>{item.participantCount}{item.capacity ? `/${item.capacity}` : ''}</small>
        </label>)}</div>
        {squads.length > 0 && <div className="registration-teams">{squads.map(item => <label key={item.id} className={squad === item.id ? 'active' : ''}>
          <input type="radio" name="registration-squad" checked={squad === item.id} disabled={item.status !== 'OPEN'} onChange={() => setSquad(item.id)}/>
          <b>{item.name}</b><small>{item.participantCount}/{item.capacity}</small>
        </label>)}</div>}
      </fieldset>}
      <button type="button" className="module-primary" disabled={pending === 'register' || (teams && (!team || (squads.length > 0 && !squad)))} onClick={register}>
        {pending === 'register' ? 'Inscrevendo…' : 'Confirmar inscrição'}</button>
    </div>}
    {!active && data.mine?.status !== 'REMOVED' && !data.open && <p className="registration-muted">Inscrições encerradas.</p>}

    {active && data.mine && <div className="registration-mine">
      <p><Check/> <b>{STATUS_LABELS[data.mine.status]}</b>{data.mine.teamName && <> · {data.mine.teamName}</>} · {roleLine(data.mine)}</p>
      {editingRole ? <div className="registration-edit">
        <RoleChooser value={role} onChange={setRole} title="Alterar minha função"/>
        <div className="registration-row">
          <button type="button" className="module-primary" disabled={pending === 'role'} onClick={() => void run('role', () => updateMyRole(operationId, role)).then(ok => ok && setEditingRole(false))}>Salvar função</button>
          <button type="button" className="module-secondary" onClick={() => setEditingRole(false)}>Cancelar</button>
        </div>
      </div> : <div className="registration-row">
        <button type="button" className="module-secondary" onClick={() => setEditingRole(true)}>Alterar minha função</button>
        {data.mine.status === 'APPROVED' && data.canCancel && <button type="button" className="module-secondary" disabled={pending === 'presence'} onClick={() => void run('presence', () => confirmOperationPresence(operationId))}>Confirmar presença</button>}
        {data.canCancel && <button type="button" className="module-danger" disabled={pending === 'cancel'} onClick={() => { if (window.confirm('Cancelar sua inscrição nesta operação? Você sai da lista e do chat.')) void run('cancel', () => cancelOperationParticipation(operationId)) }}>Cancelar minha inscrição</button>}
      </div>}
      {teams && data.mine.pendingChange && <p className="registration-pending"><ArrowLeftRight/> Solicitação aguardando aprovação do organizador: ir para <b>{data.mine.pendingChange.toTeamName}</b>.
        <button type="button" onClick={() => void run('cancel-change', () => cancelTeamChange(operationId))}>Desistir</button></p>}
      {teams && !data.mine.pendingChange && data.teamChangeRequestsEnabled && data.teams.length > 1 && <details className="registration-change">
        <summary><ArrowLeftRight/> Solicitar troca de equipe</summary>
        <p>Equipe atual: <b>{data.mine.teamName ?? 'nenhuma'}</b></p>
        <label><span>Equipe desejada</span><select value={changeTo} onChange={event => setChangeTo(event.target.value)}>
          <option value="">Selecione</option>
          {data.teams.filter(item => item.id !== data.mine?.teamId).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <button type="button" className="module-primary" disabled={!changeTo || pending === 'change'} onClick={() => void run('change', () => requestTeamChange(operationId, changeTo)).then(ok => ok && setChangeTo(''))}>Enviar solicitação</button>
      </details>}
    </div>}

    {/* ---- Organizador ---- */}
    {data.managed && <div className="registration-admin">
      <h4>Distribuição dos operadores</h4>
      <ul className="registration-counts">
        <li><b>{data.counts.assault}</b> Assalto</li><li><b>{data.counts.sniper}</b> Sniper</li><li><b>{data.counts.dmr}</b> DMR</li>
        <li><b>{data.counts.medic}</b> Médicos</li><li><b>{data.counts.radio}</b> Operadores de Rádio</li>
        {data.counts.withoutRole > 0 && <li><b>{data.counts.withoutRole}</b> Sem função</li>}
      </ul>
      <div className="registration-row">
        {data.open
          ? <button type="button" className="module-secondary" disabled={pending === 'close'} onClick={() => { if (window.confirm('Encerrar as inscrições agora? Ninguém mais consegue se inscrever até você reabrir.')) void run('close', () => closeRegistration(operationId)) }}><Lock/> Encerrar inscrições</button>
          : data.closedReason === 'MANUAL' && <button type="button" className="module-secondary" disabled={pending === 'reopen'} onClick={() => void run('reopen', () => reopenRegistration(operationId))}><LockOpen/> Reabrir inscrições</button>}
        {teams && <label className="registration-switch"><input type="checkbox" checked={data.teamChangeRequestsEnabled} onChange={event => void run('switch', () => setTeamChangeRequests(operationId, event.target.checked))}/> Aceitar pedidos de troca de equipe</label>}
      </div>
      {teams && data.changeRequests.length > 0 && <div className="registration-requests">
        <h4>Solicitações de troca</h4>
        <ul>{data.changeRequests.map(item => <li key={item.id}>
          <span><b>{item.callsign}</b><small>{item.fromTeamName ?? '—'} → {item.toTeamName ?? '—'} · {when(item.createdAt)}</small></span>
          {item.status === 'PENDING' ? <span className="registration-row">
            <button type="button" className="module-secondary" disabled={pending === item.id} onClick={() => void run(item.id, () => decideTeamChange(operationId, item.id, 'APPROVE'))}>Aprovar</button>
            <button type="button" className="module-danger" disabled={pending === item.id} onClick={() => void run(item.id, () => decideTeamChange(operationId, item.id, 'REJECT'))}>Recusar</button>
          </span> : <em className={`registration-tag registration-tag--${item.status.toLowerCase()}`}>{{ APPROVED: 'Aprovada', REJECTED: 'Recusada', CANCELLED: 'Sem efeito', PENDING: '' }[item.status]}</em>}
        </li>)}</ul>
      </div>}
    </div>}

    {/* ---- Listas e filtros ---- */}
    <div className="registration-toolbar">
      {teams && <div className="registration-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={view === 'list'} className={view === 'list' ? 'active' : ''} onClick={() => setView('list')}><ListOrdered/> Lista geral</button>
        <button type="button" role="tab" aria-selected={view === 'teams'} className={view === 'teams' ? 'active' : ''} onClick={() => setView('teams')}><Users/> Por equipes</button>
      </div>}
      <div className="registration-filters" aria-label="Filtrar inscritos">
        {PRIMARY_ROLES.map(item => <button key={item.value} type="button" aria-pressed={filter.roles.includes(item.value)} className={filter.roles.includes(item.value) ? 'active' : ''} onClick={() => toggleRole(item.value)}>{item.label}</button>)}
        <button type="button" aria-pressed={filter.medic} className={filter.medic ? 'active' : ''} onClick={() => setFilter(f => ({ ...f, medic: !f.medic }))}><HeartPulse/> Médico</button>
        <button type="button" aria-pressed={filter.radio} className={filter.radio ? 'active' : ''} onClick={() => setFilter(f => ({ ...f, radio: !f.radio }))}><Radio/> Rádio</button>
        {teams && <select value={filter.team} onChange={event => setFilter(f => ({ ...f, team: event.target.value }))} aria-label="Filtrar por equipe">
          <option value="">Todas as equipes</option>{data.teams.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>}
        {(filter.roles.length > 0 || filter.medic || filter.radio || filter.team) && <button type="button" onClick={() => setFilter({ roles: [], medic: false, radio: false, team: '' })}><X/> Limpar</button>}
      </div>
    </div>
    {data.participants.length === 0 ? <p className="registration-muted">Ainda não há inscritos.</p>
      : visible.length === 0 ? <p className="registration-muted">Nenhum inscrito com esses filtros.</p>
      : view === 'teams' && teams
        ? data.teams.map(item => {
          const members = visible.filter(p => p.teamId === item.id)
          return <div key={item.id} className="registration-group"><h5><i style={{ background: item.color }}/>{item.name} <small>{members.length}</small></h5>
            {members.length ? <ol>{members.map(p => row(p))}</ol> : <p className="registration-muted">Ninguém nesta equipe com esses filtros.</p>}</div>
        })
        : <ol className="registration-list">{visible.map(p => row(p))}</ol>}

    {data.managed && data.removed.length > 0 && <div className="registration-removed">
      <h4>Removidos</h4>
      <ul>{data.removed.map(item => <li key={item.operatorId + item.removedAt}>
        <span><b>{item.callsign}</b><small>{when(item.removedAt)}{item.removedBy && ` por ${item.removedBy}`} · {item.reason}</small></span>
        {item.returnAuthorizedAt ? <em className="registration-tag">Retorno autorizado</em>
          : <button type="button" className="module-secondary" disabled={pending === `return-${item.operatorId}`} onClick={() => void run(`return-${item.operatorId}`, () => authorizeParticipantReturn(operationId, item.operatorId))}><RotateCcw/> Autorizar retorno</button>}
      </li>)}</ul>
    </div>}

    {removing && <div className="registration-modal-backdrop" role="presentation" onClick={() => setRemoving(null)}>
      <section className="registration-modal" role="dialog" aria-modal="true" aria-labelledby="remove-title" onClick={event => event.stopPropagation()}>
        <h4 id="remove-title"><UserMinus/> Remover {removing.callsign}</h4>
        <label><span>Motivo da remoção</span><textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={500} rows={3} required
          placeholder="O jogador recebe este motivo em um aviso."/></label>
        <div className="registration-row">
          <button type="button" className="module-secondary" onClick={() => setRemoving(null)}>Cancelar</button>
          <button type="button" className="module-danger" disabled={reason.trim().length < 3 || pending === 'remove'}
            onClick={() => void run('remove', () => removeParticipant(operationId, removing.operatorId, reason.trim())).then(ok => { if (ok) { setRemoving(null); setReason('') } })}>Confirmar remoção</button>
        </div>
      </section>
    </div>}
  </section>

  function row(p: RegistrationParticipant) {
    return <li key={p.operatorId} className="registration-person">
      <span className="registration-pos">{p.position}</span>
      <span className="registration-who"><b>{p.callsign}</b><small>@{p.username}{p.teamName && !(view === 'teams' && teams) && <> · <i style={{ background: p.teamColor ?? '#777' }}/>{p.teamName}</>}</small></span>
      <span className="registration-roles">
        <em className="registration-tag">{primaryLabel(p.primaryRole)}</em>
        {p.medic && <em className="registration-tag registration-tag--medic" title="Médico"><HeartPulse/></em>}
        {p.radio && <em className="registration-tag registration-tag--radio" title="Operador de Rádio"><Radio/></em>}
        {p.status !== 'APPROVED' && <em className="registration-tag registration-tag--status">{STATUS_LABELS[p.status] ?? p.status}</em>}
      </span>
      {data?.managed && <span className="registration-actions">
        {(ACTIONS[p.status] ?? []).map(item => <button key={item.action} type="button" className="module-secondary" disabled={pending === `${item.action}-${p.operatorId}`}
          onClick={() => void run(`${item.action}-${p.operatorId}`, () => decideOperationParticipant(operationId, p.operatorId, item.action))}>{item.label}</button>)}
        <button type="button" className="module-danger" aria-label={`Remover ${p.callsign}`} onClick={() => { setRemoving(p); setReason('') }}><CircleSlash/> Remover</button>
      </span>}
    </li>
  }
}

function roleLine(mine: { primaryRole?: PrimaryRole | null; medic: boolean; radio: boolean }) {
  return [primaryLabel(mine.primaryRole), mine.medic && 'Médico', mine.radio && 'Operador de Rádio'].filter(Boolean).join(' + ')
}
