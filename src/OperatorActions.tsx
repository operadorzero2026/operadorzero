import { Check, Rss, Shield, Swords, UserPlus, UserRound } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import {
  acceptOperatorFriendship, cancelOperatorFriendship, followOperator, getFollowStats, getOperatorConnectionStatus, getOperatorInviteOptions,
  inviteFriendToGame, inviteOperator, requestOperatorFriendship, type OperatorConnectionStatus, type OperatorInviteOptions,
} from './api'

const day = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' })

/**
 * Acoes sobre um operador encontrado: seguir (ver as publicacoes no feed "Seguindo"), amizade e, entre amigos, convite para a equipe (capitao/gestor) e para jogos.
 * Convites so entre amigos evitam spam; o servidor repete todas as regras.
 */
export function OperatorActions({ username, showFriendButton = true, onViewProfile }: { username: string; showFriendButton?: boolean; onViewProfile?: () => void }) {
  const [connection, setConnection] = useState<OperatorConnectionStatus | null>(null)
  const [options, setOptions] = useState<OperatorInviteOptions | null>(null)
  const [follows, setFollows] = useState<boolean | null>(null)
  const [pending, setPending] = useState('')
  const [sent, setSent] = useState<string[]>([])
  const [notice, setNotice] = useState('')
  const load = useCallback(async () => {
    try {
      const [status, invites, follow] = await Promise.all([getOperatorConnectionStatus(username), getOperatorInviteOptions(username), getFollowStats(username)])
      setConnection(status); setOptions(invites); setFollows(follow.follows)
    } catch { setNotice('Não foi possível carregar as ações agora.') }
  }, [username])
  useEffect(() => { setSent([]); setNotice(''); void load() }, [load])

  const run = async (key: string, action: () => Promise<unknown>, done: string) => {
    setPending(key); setNotice('')
    try { await action(); setSent(current => [...current, key]); setNotice(done) } catch (e) { setNotice(e instanceof Error ? e.message : 'Não foi possível concluir.') } finally { setPending('') }
  }
  const friend = async () => {
    if (!connection) return
    setPending('friend'); setNotice('')
    try {
      if (connection.status === 'NONE') setConnection(await requestOperatorFriendship(username))
      else if (connection.status === 'PENDING_SENT' && connection.connectionId) { await cancelOperatorFriendship(connection.connectionId); setConnection({ status: 'NONE' }) }
      else if (connection.status === 'PENDING_RECEIVED' && connection.connectionId) { await acceptOperatorFriendship(connection.connectionId); await load() }
    } catch (e) { setNotice(e instanceof Error ? e.message : 'Não foi possível concluir.') } finally { setPending('') }
  }
  const toggleFollow = async () => {
    if (follows === null) return
    setPending('follow'); setNotice('')
    try { setFollows((await followOperator(username, !follows)).follows) } catch (e) { setNotice(e instanceof Error ? e.message : 'Não foi possível concluir.') } finally { setPending('') }
  }
  const status = connection?.status
  const friendLabel = status === 'ACCEPTED' ? <><Check/> Amigos</> : status === 'PENDING_SENT' ? 'Pedido enviado · cancelar' : status === 'PENDING_RECEIVED' ? <><Check/> Aceitar amizade</> : <><UserPlus/> Adicionar amigo</>
  const team = options?.team

  return <div className="operator-actions">
    <div className="operator-actions-row">
      {showFriendButton && status !== 'BLOCKED' && <button type="button" className={status === 'ACCEPTED' ? 'is-done' : 'is-primary'} disabled={!connection || pending === 'friend' || status === 'ACCEPTED'} onClick={() => void friend()}>{friendLabel}</button>}
      {showFriendButton && status !== 'BLOCKED' && <button type="button" className={follows ? 'is-done' : ''} aria-pressed={Boolean(follows)} disabled={follows === null || pending === 'follow'} onClick={() => void toggleFollow()}>
        {follows ? <><Check/> Seguindo</> : <><Rss/> Seguir</>}</button>}
      {onViewProfile && <button type="button" onClick={onViewProfile}><UserRound/> Ver perfil</button>}
    </div>
    {options?.friends && <>
      {team && <section>
        <small>EQUIPE</small>
        {team.canInvite
          ? <button type="button" disabled={pending === 'team' || sent.includes('team')} onClick={() => void run('team', () => inviteOperator(team.id, { operatorId: options.operatorId, proposedRole: 'MEMBER', message: '' }), 'Convite para a equipe enviado.')}>
              <Shield/> {sent.includes('team') ? 'Convite enviado' : `Convidar para ${team.name}`}</button>
          : <p>{team.reason}</p>}
      </section>}
      <section>
        <small>CONVIDAR PARA UM JOGO</small>
        {options.operations.length === 0
          ? <p>Nenhum jogo seu com inscrição aberta para convidar agora.</p>
          : <ul>{options.operations.map(op => <li key={op.id}>
              <span><b>{op.name}</b><small>{day(op.date)} · {op.fieldName}</small></span>
              <button type="button" disabled={pending === op.id || sent.includes(op.id)} onClick={() => void run(op.id, () => inviteFriendToGame(op.id, username), 'Convite enviado. Seu amigo recebe um aviso para se alistar.')}>
                {sent.includes(op.id) ? <><Check/> Convidado</> : <><Swords/> Convidar</>}</button>
            </li>)}</ul>}
      </section>
    </>}
    {options && !options.friends && status !== 'BLOCKED' && <p className="operator-actions-hint">Quando forem amigos, você pode convidar para sua equipe e para jogos.</p>}
    {notice && <p className="operator-actions-notice" role="status">{notice}</p>}
  </div>
}
