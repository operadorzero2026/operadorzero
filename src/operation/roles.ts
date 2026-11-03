import type { PlayerRole, PrimaryRole } from '../api'

export const PRIMARY_ROLES: { value: PrimaryRole; label: string }[] = [
  { value: 'ASSAULT', label: 'Assalto' },
  { value: 'SNIPER', label: 'Sniper' },
  { value: 'DMR', label: 'DMR' },
]

export const primaryLabel = (role?: PrimaryRole | null) => PRIMARY_ROLES.find(item => item.value === role)?.label ?? 'Sem função'

/** "Assalto · Médico · Rádio": a funcao principal seguida das complementares. */
export const roleText = (role: { primaryRole?: PrimaryRole | null; medic: boolean; radio: boolean }) =>
  [primaryLabel(role.primaryRole), role.medic && 'Médico', role.radio && 'Operador de Rádio'].filter(Boolean).join(' · ')

export const DEFAULT_ROLE: PlayerRole = { primaryRole: 'ASSAULT', medic: false, radio: false }
