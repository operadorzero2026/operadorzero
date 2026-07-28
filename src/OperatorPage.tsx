import { Plus, RefreshCw, Save, ShieldCheck, Trash2 } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { addOperatorEquipment, getOperatorProfile, removeOperatorEquipment, type OperatorProfile, updateOperatorPrivacy, updateOperatorProfile } from './api'
import { BrazilLocationFields } from './BrazilLocationFields'

const positions = ['ASSAULT', 'SUPPORT', 'MEDIC', 'SNIPER', 'RECON', 'COMMAND', 'DEFENSE', 'OTHER']
const positionLabels: Record<string, string> = { ASSAULT: 'Assalto', SUPPORT: 'Suporte', MEDIC: 'Médico', SNIPER: 'Sniper', RECON: 'Reconhecimento', COMMAND: 'Comando', DEFENSE: 'Defesa', OTHER: 'Outra' }
const visibilityLabels: Record<string, string> = { ONLY_ME: 'Somente eu', MY_TEAM: 'Minha equipe', RELATED_ORGANIZERS: 'Organizadores relacionados', AUTHENTICATED: 'Operadores conectados', PUBLIC: 'Público' }

export function OperatorPage({ onUserUpdated }: { onUserUpdated: (user: { username: string; displayName: string; callsign: string }) => void }) {
  const [profile, setProfile] = useState<OperatorProfile | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [pending, setPending] = useState('')
  const load = async () => {
    setError('')
    try { setProfile(await getOperatorProfile()) } catch { setError('Não foi possível carregar seu perfil.') }
  }
  useEffect(() => { void load() }, [])

  if (!profile) return <main className="module-page"><header><p>IDENTIDADE</p><h1>Meu Operador</h1></header><section className="module-state">{error ? <><p>{error}</p><button onClick={() => void load()}><RefreshCw/> Tentar novamente</button></> : <p>Carregando seu perfil…</p>}</section></main>

  const saveProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setPending('profile'); setError(''); setNotice('')
    const data = new FormData(event.currentTarget)
    try {
      const updated = await updateOperatorProfile({
        displayName: String(data.get('displayName')), callsign: String(data.get('callsign')),
        username: String(data.get('username')), bio: String(data.get('bio')), city: String(data.get('profileCity')),
        stateCode: String(data.get('profileState')), preferredPosition: String(data.get('preferredPosition')),
        secondaryPositions: data.getAll('secondaryPositions').map(String), recruitmentStatus: String(data.get('recruitmentStatus')),
        version: profile.version,
      })
      setProfile(updated)
      onUserUpdated({ username: updated.username, displayName: updated.displayName, callsign: updated.callsign })
      setNotice('Perfil atualizado com sucesso.')
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível salvar o perfil.') }
    finally { setPending('') }
  }

  const savePrivacy = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setPending('privacy'); setError(''); setNotice('')
    const data = new FormData(event.currentTarget)
    try {
      setProfile(await updateOperatorPrivacy({ LOCATION: String(data.get('LOCATION')), EQUIPMENT: String(data.get('EQUIPMENT')), TEAM_STATUS: String(data.get('TEAM_STATUS')) }))
      setNotice('Privacidade atualizada.')
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível salvar a privacidade.') }
    finally { setPending('') }
  }

  const addEquipment = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setPending('equipment'); setError(''); setNotice('')
    const form = event.currentTarget; const data = new FormData(form)
    try {
      setProfile(await addOperatorEquipment({ category: String(data.get('category')), name: String(data.get('name')), details: String(data.get('details')), condition: String(data.get('condition')), visibility: String(data.get('visibility')) }))
      form.reset(); setNotice('Equipamento adicionado ao perfil.')
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível adicionar o equipamento.') }
    finally { setPending('') }
  }

  return <main className="module-page"><header><p>IDENTIDADE</p><h1>Meu Operador</h1><span>Controle sua apresentação e o que outros operadores podem consultar.</span></header>{(error || notice) && <p className={error ? 'module-feedback module-feedback--error' : 'module-feedback'} role="status">{error || notice}</p>}
    <section className="module-section"><div className="module-section-title"><div><small>PERFIL PÚBLICO</small><h2>{profile.callsign}</h2></div><span className="operator-avatar">{profile.callsign.charAt(0).toUpperCase()}</span></div>
      <form className="module-form" onSubmit={saveProfile} key={`profile-${profile.version}`}><div className="form-grid"><label><span>Nome de exibição</span><input name="displayName" defaultValue={profile.displayName} minLength={2} maxLength={80} required/></label><label><span>Nick / callsign</span><input name="callsign" defaultValue={profile.callsign} minLength={2} maxLength={40} required/></label><label><span>Nome de usuário</span><input name="username" defaultValue={profile.username} pattern="[A-Za-z0-9._-]{3,30}" required/></label><label><span>Status</span><select name="recruitmentStatus" defaultValue={profile.recruitmentStatus}><option value="LONE_WOLF">Operador independente</option><option value="LOOKING_FOR_TEAM">Procurando equipe</option><option value="NOT_LOOKING">Não procuro equipe</option></select></label><BrazilLocationFields defaultState={profile.stateCode || ''} defaultCity={profile.city || ''} required={false} namePrefix="profile"/><label><span>Posição principal</span><select name="preferredPosition" defaultValue={profile.preferredPosition || ''}><option value="">Não informar</option>{positions.map(value => <option value={value} key={value}>{positionLabels[value]}</option>)}</select></label><label><span>Posições secundárias</span><select name="secondaryPositions" defaultValue={profile.secondaryPositions} multiple size={4}>{positions.map(value => <option value={value} key={value}>{positionLabels[value]}</option>)}</select></label></div><label><span>Biografia</span><textarea name="bio" defaultValue={profile.bio || ''} maxLength={500}/></label><button className="module-primary" disabled={pending === 'profile'}><Save/> {pending === 'profile' ? 'Salvando…' : 'Salvar perfil'}</button></form>
    </section>
    <section className="module-section"><div className="module-section-title"><div><small>CONTROLE</small><h2>Privacidade</h2></div><ShieldCheck/></div><form className="module-form" onSubmit={savePrivacy}><div className="form-grid">{[['LOCATION','Localização'],['EQUIPMENT','Equipamentos'],['TEAM_STATUS','Status de equipe']].map(([field,label]) => <label key={field}><span>{label}</span><select name={field} defaultValue={profile.privacy[field] || 'AUTHENTICATED'}>{Object.entries(visibilityLabels).map(([value,text]) => <option value={value} key={value}>{text}</option>)}</select></label>)}</div><button className="module-primary" disabled={pending === 'privacy'}><Save/> Salvar privacidade</button></form></section>
    <section className="module-section"><div className="module-section-title"><div><small>INVENTÁRIO DO PERFIL</small><h2>Equipamentos</h2></div></div>{profile.equipment.length === 0 ? <p className="module-empty">Nenhum equipamento cadastrado.</p> : <div className="equipment-list">{profile.equipment.map(item => <div key={item.id}><div><b>{item.name}</b><small>{item.category} · {item.condition === 'NEW' ? 'Novo' : 'Usado'} · {visibilityLabels[item.visibility]}</small>{item.details && <p>{item.details}</p>}</div><button aria-label={`Remover ${item.name}`} onClick={async () => { setPending(item.id); try { await removeOperatorEquipment(item.id); await load() } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível remover.') } finally { setPending('') } }} disabled={pending === item.id}><Trash2/></button></div>)}</div>}
      <form className="module-form module-form--inline" onSubmit={addEquipment}><label><span>Categoria</span><input name="category" maxLength={48} required placeholder="AEG, marcador, proteção…"/></label><label><span>Nome do equipamento</span><input name="name" maxLength={100} required/></label><label><span>Estado</span><select name="condition"><option value="USED">Usado</option><option value="NEW">Novo</option></select></label><label><span>Visibilidade</span><select name="visibility"><option value="ONLY_ME">Somente eu</option><option value="MY_TEAM">Minha equipe</option><option value="AUTHENTICATED">Operadores conectados</option><option value="PUBLIC">Público</option></select></label><label className="form-wide"><span>Detalhes e acessórios</span><textarea name="details" maxLength={500}/></label><button className="module-primary" disabled={pending === 'equipment'}><Plus/> Adicionar equipamento</button></form>
    </section>
  </main>
}
