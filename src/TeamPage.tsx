import { Crown, LogOut, RefreshCw, Save, Send, Shield, UserPlus, Users } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { acceptTeamInvitation, createTeam, declineTeamInvitation, getTeamWorkspace, inviteOperator, leaveTeam, transferCaptaincy, type OperatorSummary, type Team, type TeamWorkspace, updateTeam } from './api'
import { BrazilLocationFields } from './BrazilLocationFields'
import { OperatorSearch } from './OperatorSearch'

export function TeamPage() {
  const [workspace, setWorkspace] = useState<TeamWorkspace | null>(null)
  const [selectedOperator, setSelectedOperator] = useState<OperatorSummary | null>(null)
  const [pending, setPending] = useState('')
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const load = async () => {
    setFeedback(null)
    try { setWorkspace(await getTeamWorkspace()) } catch { setFeedback({ type: 'error', text: 'Não foi possível carregar sua equipe.' }) }
  }
  useEffect(() => { void load() }, [])

  const run = async (key: string, action: () => Promise<unknown>, success: string) => {
    setPending(key); setFeedback(null)
    try { await action(); await load(); setFeedback({ type: 'success', text: success }) }
    catch (error) { setFeedback({ type: 'error', text: error instanceof Error ? error.message : 'Não foi possível concluir a ação.' }) }
    finally { setPending('') }
  }

  if (!workspace) return <main className="module-page"><header><p>ORGANIZAÇÃO</p><h1>Minha Equipe</h1></header><section className="module-state">{feedback ? <><p>{feedback.text}</p><button onClick={() => void load()}><RefreshCw/> Tentar novamente</button></> : <p>Carregando sua equipe…</p>}</section></main>
  const team = workspace.team
  return <main className="module-page"><header><p>ORGANIZAÇÃO</p><h1>Minha Equipe</h1><span>Convites, integrantes e administração em um único lugar.</span></header>{feedback && <p className={`module-feedback ${feedback.type === 'error' ? 'module-feedback--error' : ''}`} role="status">{feedback.text}</p>}
    {workspace.receivedInvitations.length > 0 && <section className="module-section"><div className="module-section-title"><div><small>CONVITES RECEBIDOS</small><h2>Equipes aguardando você</h2></div><UserPlus/></div><div className="invitation-list">{workspace.receivedInvitations.map(invitation => <div key={invitation.id}><div><b>{invitation.teamName} [{invitation.teamAcronym}]</b><small>Enviado por {invitation.inviterCallsign} · função {roleLabel(invitation.proposedRole)}</small>{invitation.message && <p>{invitation.message}</p>}</div><span><button disabled={!!pending} onClick={() => void run(invitation.id, () => acceptTeamInvitation(invitation.id), 'Convite aceito. Bem-vindo à equipe!')}>Aceitar</button><button disabled={!!pending} onClick={() => void run(invitation.id, () => declineTeamInvitation(invitation.id), 'Convite recusado.')}>Recusar</button></span></div>)}</div></section>}
    {!team ? <CreateTeamForm pending={pending} onCreate={(form) => run('create', () => createTeam(form), 'Equipe criada com sucesso.')}/> : <TeamWorkspaceView team={team} pending={pending} selectedOperator={selectedOperator} setSelectedOperator={setSelectedOperator} run={run}/>} 
  </main>
}

function CreateTeamForm({ pending, onCreate }: { pending: string; onCreate: (team: Record<string, unknown>) => Promise<void> }) {
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    await onCreate(teamPayload(data))
  }
  return <section className="module-section"><div className="module-section-title"><div><small>NOVA FORMAÇÃO</small><h2>Crie sua equipe</h2><p>Você será o capitão inicial e poderá convidar outros operadores.</p></div><Users/></div><form className="module-form" onSubmit={submit}><div className="form-grid"><label><span>Nome</span><input name="name" minLength={3} maxLength={80} required/></label><label><span>Sigla</span><input name="acronym" pattern="(?=.*[A-Za-z0-9])[A-Za-z0-9.\-]{2,12}" maxLength={12} title="Use de 2 a 12 caracteres: letras, números, pontos ou hífens." required/></label><BrazilLocationFields namePrefix="team"/><label><span>Estilo de jogo</span><input name="gameStyle" maxLength={80} required placeholder="Milsim, speedsoft, casual…"/></label><label><span>Recrutamento</span><select name="recruitmentStatus"><option value="OPEN">Aberto</option><option value="INVITE_ONLY">Somente por convite</option><option value="CLOSED">Fechado</option></select></label></div><label><span>Descrição</span><textarea name="description" maxLength={500}/></label><label className="check-field"><input type="checkbox" name="ownsField"/> A equipe possui campo próprio</label><button className="module-primary" disabled={pending === 'create'}><Users/> {pending === 'create' ? 'Criando…' : 'Criar equipe'}</button></form></section>
}

function TeamWorkspaceView({ team, pending, selectedOperator, setSelectedOperator, run }: { team: Team; pending: string; selectedOperator: OperatorSummary | null; setSelectedOperator: (operator: OperatorSummary | null) => void; run: (key: string, action: () => Promise<unknown>, success: string) => Promise<void> }) {
  const canManage = ['CAPTAIN', 'MANAGER'].includes(team.currentUserRole)
  const isCaptain = team.currentUserRole === 'CAPTAIN'
  const update = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    await run('update', () => updateTeam(team.id, { ...teamPayload(data), version: team.version }), 'Dados da equipe atualizados.')
  }
  const invite = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); if (!selectedOperator) return
    const data = new FormData(event.currentTarget)
    await run('invite', () => inviteOperator(team.id, { operatorId: selectedOperator.id, proposedRole: String(data.get('proposedRole')), message: String(data.get('message')) }), 'Convite enviado.')
    setSelectedOperator(null)
  }
  const transfer = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    await run('captaincy', () => transferCaptaincy(team.id, String(data.get('operatorId')), String(data.get('reason'))), 'Capitania transferida.')
  }
  return <>
    <section className="team-heading"><div><span>{team.acronym}</span><div><small>SUA EQUIPE · {roleLabel(team.currentUserRole)}</small><h2>{team.name}</h2><p>{team.city}/{team.stateCode} · {team.gameStyle}</p></div></div><strong>{recruitmentLabel(team.recruitmentStatus)}</strong></section>
    <section className="module-section"><div className="module-section-title"><div><small>FORMAÇÃO ATUAL</small><h2>{team.members.length} {team.members.length === 1 ? 'integrante' : 'integrantes'}</h2></div><Shield/></div><div className="member-list">{team.members.map(member => <div key={member.operatorId}><span className="operator-avatar">{member.callsign.charAt(0).toUpperCase()}</span><div><b>{member.callsign}</b><small>{member.displayName} · @{member.username}</small></div><strong>{member.role === 'CAPTAIN' && <Crown/>}{roleLabel(member.role)}</strong></div>)}</div></section>
    {canManage && <section className="module-section"><div className="module-section-title"><div><small>RECRUTAMENTO</small><h2>Convidar operador</h2></div><UserPlus/></div><OperatorSearch compact onSelect={setSelectedOperator}/>{selectedOperator && <form className="module-form invite-form" onSubmit={invite}><p>Selecionado: <b>{selectedOperator.callsign}</b> · @{selectedOperator.username}</p><label><span>Função</span><select name="proposedRole"><option value="MEMBER">Integrante</option>{isCaptain && <option value="MANAGER">Gestor</option>}</select></label><label><span>Mensagem</span><textarea name="message" maxLength={500}/></label><button className="module-primary" disabled={pending === 'invite'}><Send/> Enviar convite</button></form>}</section>}
    {canManage && <section className="module-section"><div className="module-section-title"><div><small>ADMINISTRAÇÃO</small><h2>Dados da equipe</h2></div></div><form className="module-form" onSubmit={update} key={`team-${team.version}`}><div className="form-grid"><label><span>Nome</span><input name="name" defaultValue={team.name} minLength={3} maxLength={80} required/></label><label><span>Sigla</span><input name="acronym" defaultValue={team.acronym} pattern="(?=.*[A-Za-z0-9])[A-Za-z0-9.\-]{2,12}" maxLength={12} title="Use de 2 a 12 caracteres: letras, números, pontos ou hífens." required/></label><BrazilLocationFields namePrefix="team" defaultState={team.stateCode} defaultCity={team.city}/><label><span>Estilo</span><input name="gameStyle" defaultValue={team.gameStyle} maxLength={80} required/></label><label><span>Recrutamento</span><select name="recruitmentStatus" defaultValue={team.recruitmentStatus}><option value="OPEN">Aberto</option><option value="INVITE_ONLY">Somente por convite</option><option value="CLOSED">Fechado</option></select></label></div><label><span>Descrição</span><textarea name="description" defaultValue={team.description || ''} maxLength={500}/></label><label className="check-field"><input type="checkbox" name="ownsField" defaultChecked={team.ownsField}/> A equipe possui campo próprio</label><button className="module-primary" disabled={pending === 'update'}><Save/> Salvar equipe</button></form></section>}
    {isCaptain && team.members.length > 1 && <section className="module-section module-section--danger"><div className="module-section-title"><div><small>RESPONSABILIDADE</small><h2>Transferir capitania</h2></div><Crown/></div><form className="module-form" onSubmit={transfer}><label><span>Novo capitão</span><select name="operatorId" required><option value="">Selecione</option>{team.members.filter(member => member.role !== 'CAPTAIN').map(member => <option value={member.operatorId} key={member.operatorId}>{member.callsign} · {roleLabel(member.role)}</option>)}</select></label><label><span>Motivo</span><input name="reason" maxLength={500}/></label><button className="module-secondary" disabled={pending === 'captaincy'}>Transferir capitania</button></form></section>}
    {!isCaptain && <section className="module-section module-section--danger"><div><small>VÍNCULO</small><h2>Sair da equipe</h2><p>Seu histórico de participação será preservado.</p></div><button className="module-secondary" disabled={pending === 'leave'} onClick={() => void run('leave', leaveTeam, 'Você saiu da equipe.')}><LogOut/> Sair da equipe</button></section>}
  </>
}

function teamPayload(data: FormData): Record<string, unknown> {
  return { name: String(data.get('name')), acronym: String(data.get('acronym')).toUpperCase(), city: String(data.get('teamCity')), stateCode: String(data.get('teamState')), gameStyle: String(data.get('gameStyle')), ownsField: data.get('ownsField') === 'on', description: String(data.get('description')), recruitmentStatus: String(data.get('recruitmentStatus')) }
}
function roleLabel(role: string) { return ({ CAPTAIN: 'Capitão', MANAGER: 'Gestor', MEMBER: 'Integrante' } as Record<string,string>)[role] || role }
function recruitmentLabel(status: string) { return ({ OPEN: 'Recrutamento aberto', INVITE_ONLY: 'Somente por convite', CLOSED: 'Recrutamento fechado' } as Record<string,string>)[status] || status }
