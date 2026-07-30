export type OperatorIdentityLabel = {
  callsign?: string | null
  displayName?: string | null
  teamName?: string | null
}

export function operatorTeamLabel(operator: OperatorIdentityLabel) {
  const name = operator.callsign?.trim() || operator.displayName?.trim() || 'USUÁRIO'
  const team = operator.teamName?.trim() || 'SEM TIME'
  return `${name} - ${team}`
}
