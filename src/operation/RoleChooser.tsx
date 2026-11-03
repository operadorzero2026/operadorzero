import { HeartPulse, Radio } from 'lucide-react'
import { useId } from 'react'
import type { PlayerRole } from '../api'
import { PRIMARY_ROLES } from './roles'
import './registration.css'

/**
 * Funcao na operacao: uma principal obrigatoria (Assalto, Sniper ou DMR) e duas complementares opcionais,
 * independentes e combinaveis com qualquer principal (Medico e Operador de Radio).
 */
export function RoleChooser({ value, onChange, title = 'Qual será sua função na operação?' }: { value: PlayerRole; onChange: (role: PlayerRole) => void; title?: string }) {
  const name = useId()
  return <fieldset className="role-chooser">
    <legend>{title}</legend>
    <small>Função principal</small>
    <div className="role-primary" role="radiogroup" aria-label="Função principal">
      {PRIMARY_ROLES.map(option => <label key={option.value} className={value.primaryRole === option.value ? 'active' : ''}>
        <input type="radio" name={name} value={option.value} checked={value.primaryRole === option.value}
          onChange={() => onChange({ ...value, primaryRole: option.value })}/>
        {option.label}
      </label>)}
    </div>
    <small>Funções adicionais (opcional)</small>
    <div className="role-extra">
      <label className={value.medic ? 'active' : ''}><input type="checkbox" checked={value.medic} onChange={event => onChange({ ...value, medic: event.target.checked })}/><HeartPulse/> Médico</label>
      <label className={value.radio ? 'active' : ''}><input type="checkbox" checked={value.radio} onChange={event => onChange({ ...value, radio: event.target.checked })}/><Radio/> Operador de Rádio</label>
    </div>
  </fieldset>
}
